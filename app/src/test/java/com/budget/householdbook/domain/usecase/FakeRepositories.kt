package com.budget.householdbook.domain.usecase

import com.budget.householdbook.core.datastore.AppUserSettings
import com.budget.householdbook.data.ingestion.parser.ParsedNotificationDraft
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.data.local.entity.IgnoredKeywordEntity
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.data.local.entity.MealSavingLogEntity
import com.budget.householdbook.data.local.entity.MerchantCategoryRuleEntity
import com.budget.householdbook.data.local.entity.PendingTransactionEntity
import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.data.sync.GoogleLoginResult
import com.budget.householdbook.domain.model.QuickInquiryCredentials
import com.budget.householdbook.domain.model.MealPeriodTotal
import com.budget.householdbook.domain.model.MealPriceConfig
import com.budget.householdbook.domain.model.QueueKind
import com.budget.householdbook.domain.model.QueueStatus
import com.budget.householdbook.domain.model.SyncStatus
import com.budget.householdbook.domain.model.ThemeMode
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.domain.model.UserTier
import com.budget.householdbook.domain.repository.AutomationRuleRepository
import com.budget.householdbook.domain.repository.CategoryRepository
import com.budget.householdbook.domain.repository.KbQuickInquiryRepository
import com.budget.householdbook.domain.repository.LedgerSpaceRepository
import com.budget.householdbook.domain.repository.MealRepository
import com.budget.householdbook.domain.repository.MealWidgetRefresher
import com.budget.householdbook.domain.repository.PendingQueueRepository
import com.budget.householdbook.domain.repository.SettingsRepository
import com.budget.householdbook.domain.repository.SharedSyncRepository
import com.budget.householdbook.domain.repository.TransactionRepository
import com.budget.householdbook.domain.repository.TransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/*
 * UseCase 단위 테스트용 메모리 저장소 (Room·DataStore·Firebase 없이 JVM에서 실행)
 */

class ImmediateTransactionRunner : TransactionRunner {
    var transactionCount = 0
    override suspend fun <R> runInTransaction(block: suspend () -> R): R {
        transactionCount++
        return block()
    }
}

class FakeSettingsRepository(initial: AppUserSettings = AppUserSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val userSettingsFlow: Flow<AppUserSettings> = state
    override suspend fun setCurrentSpaceId(spaceId: String) { state.value = state.value.copy(currentSpaceId = spaceId) }
    override suspend fun setUserTier(tier: UserTier) { state.value = state.value.copy(userTier = tier) }
    override suspend fun setThemeMode(themeMode: ThemeMode) { state.value = state.value.copy(themeMode = themeMode) }
    override suspend fun setUserRealName(name: String) { state.value = state.value.copy(userRealName = name) }
    override suspend fun saveGoogleLoginProfile(uid: String, email: String, displayName: String, resolvedTier: UserTier) {
        state.value = state.value.copy(isLoggedIn = true, userUid = uid, userEmail = email, userRealName = displayName, userTier = resolvedTier)
    }
    override suspend fun clearGoogleLogin() {
        state.value = state.value.copy(isLoggedIn = false, userUid = "", userEmail = "", userTier = UserTier.FREE)
    }
    override suspend fun updateMealPriceConfig(config: MealPriceConfig) { state.value = state.value.copy(mealPriceConfig = config) }
    override suspend fun setKbAutoInquiry(enabled: Boolean, intervalHours: Int) {
        state.value = state.value.copy(kbAutoInquiryEnabled = enabled, kbAutoInquiryIntervalHours = intervalHours)
    }
    override suspend fun setEnabledInstitutions(ids: Set<String>) {
        state.value = state.value.copy(enabledInstitutionIds = ids)
    }
    override suspend fun markSeedCompleted() { state.value = state.value.copy(isSeedCompleted = true) }
}

class FakeLedgerSpaceRepository(spaces: List<LedgerSpaceEntity> = emptyList()) : LedgerSpaceRepository {
    val state = MutableStateFlow(spaces)
    override fun observeAllSpaces(): Flow<List<LedgerSpaceEntity>> = state
    override suspend fun getSpaceById(spaceId: String) = state.value.firstOrNull { it.id == spaceId }
    override suspend fun insertSpacesIfEmpty(spaces: List<LedgerSpaceEntity>) {
        val existing = state.value.map { it.id }.toSet()
        state.value = state.value + spaces.filter { it.id !in existing }
    }
    override suspend fun updateBudgetAndCycle(spaceId: String, targetAmount: Long, billingStartDay: Int) = update(spaceId) {
        it.copy(targetBudgetAmount = targetAmount, billingStartDay = billingStartDay)
    }
    override suspend fun updateShareConfig(spaceId: String, memberNamesCsv: String, inviteCode: String?, isShared: Boolean) = update(spaceId) {
        it.copy(memberNamesCsv = memberNamesCsv, inviteCode = inviteCode, isRemoteShared = isShared)
    }
    private fun update(spaceId: String, transform: (LedgerSpaceEntity) -> LedgerSpaceEntity) {
        state.value = state.value.map { if (it.id == spaceId) transform(it) else it }
    }
}

class FakeCategoryRepository(categories: List<CategoryEntity> = emptyList()) : CategoryRepository {
    val state = MutableStateFlow(categories)
    private var nextId = (categories.maxOfOrNull { it.id } ?: 0L) + 1
    override fun observeAllActiveCategories(): Flow<List<CategoryEntity>> = state.map { list -> list.filter { !it.isArchived } }
    override suspend fun getCategoriesBySpaceOnce(spaceId: String) =
        state.value.filter { it.spaceId == spaceId && !it.isArchived }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    override suspend fun insertCategory(category: CategoryEntity): Long {
        val id = if (category.id == 0L) nextId++ else category.id
        state.value = state.value.filter { it.id != id } + category.copy(id = id)
        return id
    }
    override suspend fun updateCategory(category: CategoryEntity) {
        state.value = state.value.map { if (it.id == category.id) category else it }
    }
    override suspend fun archiveCategoryAndSubs(categoryId: Long) {
        state.value = state.value.map { if (it.id == categoryId || it.parentId == categoryId) it.copy(isArchived = true) else it }
    }
}

class FakePendingQueueRepository(items: List<PendingTransactionEntity> = emptyList()) : PendingQueueRepository {
    val state = MutableStateFlow(items)
    private var nextId = (items.maxOfOrNull { it.id } ?: 0L) + 1
    override fun observePendingQueue(): Flow<List<PendingTransactionEntity>> = state.map { list ->
        list.filter { it.status == QueueStatus.PENDING }.sortedWith(compareByDescending<PendingTransactionEntity> { it.occurredAt }.thenByDescending { it.id })
    }
    override fun observePendingCount(): Flow<Int> = state.map { list -> list.count { it.status == QueueStatus.PENDING } }
    override suspend fun getById(id: Long) = state.value.firstOrNull { it.id == id }
    override suspend fun getRecentPendingSince(sinceMillis: Long) =
        state.value.filter { it.status == QueueStatus.PENDING && it.occurredAt >= sinceMillis }.sortedByDescending { it.occurredAt }
    override suspend fun insertIgnoreDuplicate(item: PendingTransactionEntity): Long {
        if (state.value.any { it.dedupHash == item.dedupHash }) return -1L
        val id = nextId++
        state.value = state.value + item.copy(id = id)
        return id
    }
    override suspend fun updateStatus(id: Long, status: QueueStatus) = update(id) { it.copy(status = status) }
    override suspend fun markCancelLinkedAndApproved(id: Long, transactionId: Long) = update(id) {
        it.copy(queueKind = QueueKind.CANCEL_MATCHED, matchedTransactionId = transactionId, status = QueueStatus.APPROVED)
    }
    override suspend fun markAsTransferPair(id: Long, partnerId: Long) = update(id) {
        it.copy(queueKind = QueueKind.TRANSFER_PAIR, pairedQueueId = partnerId, parsedType = TransactionType.TRANSFER)
    }
    private fun update(id: Long, transform: (PendingTransactionEntity) -> PendingTransactionEntity) {
        state.value = state.value.map { if (it.id == id) transform(it) else it }
    }
}

class FakeTransactionRepository(transactions: List<TransactionEntity> = emptyList()) : TransactionRepository {
    val state = MutableStateFlow(transactions)
    private var nextId = (transactions.maxOfOrNull { it.id } ?: 0L) + 1
    override fun observeTransactionsInPeriod(spaceId: String, startMillis: Long, endExclusiveMillis: Long) = state.map { list ->
        list.filter {
            it.spaceId == spaceId && it.transactionDate >= startMillis && it.transactionDate < endExclusiveMillis &&
                it.syncStatus != SyncStatus.PENDING_DELETE
        }
    }
    override suspend fun getById(transactionId: Long) = state.value.firstOrNull { it.id == transactionId }
    override suspend fun getByRemoteId(remoteId: String) = state.value.firstOrNull { it.remoteId == remoteId }
    override suspend fun findCancelCandidatesByAmount(amount: Long, sinceMillis: Long) =
        state.value.filter { !it.isCanceled && it.amount == amount && it.transactionDate >= sinceMillis }
    override suspend fun findManualCancelCandidates(refundAmount: Long, sinceMillis: Long) =
        state.value.filter {
            !it.isCanceled && it.type == TransactionType.EXPENSE && it.syncStatus != SyncStatus.PENDING_DELETE &&
                it.amount >= refundAmount && it.transactionDate >= sinceMillis
        }.sortedWith(compareByDescending<TransactionEntity> { it.amount == refundAmount }.thenByDescending { it.transactionDate })
    override suspend fun insertTransaction(transaction: TransactionEntity): Long {
        val id = if (transaction.id == 0L) nextId++ else transaction.id
        state.value = state.value.filter { it.id != id } + transaction.copy(id = id)
        return id
    }
    override suspend fun insertTransactions(transactions: List<TransactionEntity>) {
        transactions.forEach { insertTransaction(it) }
    }
    override suspend fun markTransactionCanceled(transactionId: Long) = update(transactionId) {
        it.copy(isCanceled = true, syncStatus = SyncStatus.PENDING_UPLOAD)
    }
    override suspend fun applyPartialCancel(transactionId: Long, newAmount: Long, memo: String) = update(transactionId) {
        it.copy(amount = newAmount, memo = memo, syncStatus = SyncStatus.PENDING_UPLOAD)
    }
    override suspend fun markPendingDelete(transactionId: Long) = update(transactionId) { it.copy(syncStatus = SyncStatus.PENDING_DELETE) }
    override suspend fun deleteById(transactionId: Long) {
        state.value = state.value.filter { it.id != transactionId }
    }
    override suspend fun countUnsyncedInSpace(spaceId: String) =
        state.value.count { it.spaceId == spaceId && it.syncStatus != SyncStatus.SYNCED }
    private fun update(id: Long, transform: (TransactionEntity) -> TransactionEntity) {
        state.value = state.value.map { if (it.id == id) transform(it) else it }
    }
}

class FakeMealRepository : MealRepository {
    val state = MutableStateFlow<List<MealSavingLogEntity>>(emptyList())
    private var nextId = 1L
    private fun inPeriod(spaceId: String, start: Long, end: Long) =
        state.value.filter { it.spaceId == spaceId && it.occurredAt >= start && it.occurredAt < end }
    override fun observeMealLogsInPeriod(spaceId: String, startMillis: Long, endExclusiveMillis: Long) =
        state.map { inPeriod(spaceId, startMillis, endExclusiveMillis) }
    override suspend fun sumMealsInPeriod(spaceId: String, startMillis: Long, endExclusiveMillis: Long): MealPeriodTotal {
        val logs = inPeriod(spaceId, startMillis, endExclusiveMillis)
        return MealPeriodTotal(totalCount = logs.sumOf { it.count }, totalSaved = logs.sumOf { it.savedAmount })
    }
    override suspend fun insertMealLog(log: MealSavingLogEntity): Long {
        val id = nextId++
        state.value = state.value + log.copy(id = id)
        return id
    }
    override suspend fun deleteMealLog(id: Long) {
        state.value = state.value.filter { it.id != id }
    }
}

class FakeAutomationRuleRepository(
    ignored: List<IgnoredKeywordEntity> = emptyList(),
    merchantRules: List<MerchantCategoryRuleEntity> = emptyList()
) : AutomationRuleRepository {
    val ignoredState = MutableStateFlow(ignored)
    val merchantRuleState = MutableStateFlow(merchantRules)
    override fun observeIgnoredKeywords(): Flow<List<IgnoredKeywordEntity>> = ignoredState
    override suspend fun getIgnoredKeywordsOnce() = ignoredState.value
    override suspend fun insertIgnoredKeyword(entity: IgnoredKeywordEntity) {
        ignoredState.value = ignoredState.value + entity.copy(id = ignoredState.value.size + 1L)
    }
    override suspend fun deleteIgnoredKeyword(id: Long) {
        ignoredState.value = ignoredState.value.filter { it.id != id }
    }
    override suspend fun getAllMerchantRules() = merchantRuleState.value
    override suspend fun upsertMerchantRule(rule: MerchantCategoryRuleEntity) {
        merchantRuleState.value = merchantRuleState.value.filter { it.merchantKeyword != rule.merchantKeyword } + rule
    }
}

/** 동기화 호출 기록용 */
class FakeSharedSyncRepository : SharedSyncRepository {
    val syncCalls = mutableListOf<Pair<UserTier, Boolean>>()
    var syncResult = true
    var canSync = true
    override val syncStatusMessage: StateFlow<String> = MutableStateFlow("")
    override suspend fun syncIfEligible(userTier: UserTier, isRemoteShared: Boolean): Boolean {
        syncCalls += userTier to isRemoteShared
        return syncResult
    }
    override fun canSyncNow() = canSync
    override suspend fun signInWithGoogleAccount(uiHost: Any?, manualGoogleEmail: String?, manualDisplayName: String?): Result<GoogleLoginResult> =
        Result.failure(UnsupportedOperationException())
    override suspend fun sendGoogleAccountInvitation(
        inviterEmail: String,
        inviterName: String,
        partnerGoogleEmail: String,
        partnerName: String,
        inviteCode: String
    ): Result<String> = Result.success(inviteCode)
    override suspend fun signOutGoogleAccount() = Unit
    override suspend fun syncUserProfileTierToCloud(email: String, displayName: String, tier: UserTier, inviteCode: String?, memberNamesCsv: String) = Unit
    override fun isValidInviteCode(code: String?) = code != null && code.matches(Regex("HB-[A-Z2-9]{4}-[A-Z2-9]{4}"))
    override fun generateInviteCode() = "HB-TEST-CODE"
}

class FakeKbQuickInquiryRepository(
    credentials: QuickInquiryCredentials = QuickInquiryCredentials(),
    var drafts: Result<List<ParsedNotificationDraft>> = Result.success(emptyList())
) : KbQuickInquiryRepository {
    override val credentialsFlow = MutableStateFlow(credentials)
    override fun saveCredentials(credentials: QuickInquiryCredentials) { credentialsFlow.value = credentials }
    override suspend fun fetchRecentTransactions(userRealName: String) = drafts
}

class CountingWidgetRefresher : MealWidgetRefresher {
    var count = 0
    override fun refresh() { count++ }
}
