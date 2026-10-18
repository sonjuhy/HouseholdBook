package com.budget.householdbook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budget.householdbook.core.common.BillingCycleCalculator
import com.budget.householdbook.core.common.BillingPeriod
import com.budget.householdbook.core.common.CurrencyAndDateFormatter
import com.budget.householdbook.core.datastore.AppUserSettings
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.data.local.entity.IgnoredKeywordEntity
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.data.local.entity.MealSavingLogEntity
import com.budget.householdbook.data.local.entity.PendingTransactionEntity
import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.domain.model.CategoryGroup
import com.budget.householdbook.domain.model.CategorySpendSummary
import com.budget.householdbook.domain.model.FALLBACK_SHARED_SPACE
import com.budget.householdbook.domain.model.HouseholdOverview
import com.budget.householdbook.domain.model.IngestionSource
import com.budget.householdbook.domain.model.FinancialInstitutions
import com.budget.householdbook.domain.model.QuickInquiryCredentials
import com.budget.householdbook.domain.model.MealPriceConfig
import com.budget.householdbook.domain.model.MealType
import com.budget.householdbook.domain.model.SplitApprovalItem
import com.budget.householdbook.domain.model.ThemeMode
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.domain.model.UserTier
import com.budget.householdbook.domain.usecase.AccountUseCases
import com.budget.householdbook.domain.usecase.CategoryUseCases
import com.budget.householdbook.domain.usecase.IngestionUseCases
import com.budget.householdbook.domain.usecase.InitializeAppUseCase
import com.budget.householdbook.domain.usecase.MealUseCases
import com.budget.householdbook.domain.usecase.ObserveHouseholdOverviewUseCase
import com.budget.householdbook.domain.usecase.PendingQueueUseCases
import com.budget.householdbook.domain.usecase.SampleDataResult
import com.budget.householdbook.domain.usecase.SettingsUseCases
import com.budget.householdbook.domain.usecase.TransactionUseCases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 앱 전역 UI 상태
 */
data class HouseholdUiState(
    val settings: AppUserSettings = AppUserSettings(),
    val spaces: List<LedgerSpaceEntity> = emptyList(),
    val currentSpace: LedgerSpaceEntity = FALLBACK_SHARED_SPACE,
    val monthOffset: Int = 0,
    val billingPeriod: BillingPeriod = BillingCycleCalculator.calculatePeriod(),
    val allCategories: List<CategoryEntity> = emptyList(),
    val currentSpaceCategoryGroups: List<CategoryGroup> = emptyList(),
    val pendingQueue: List<PendingTransactionEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val mealLogs: List<MealSavingLogEntity> = emptyList(),
    val ignoredKeywords: List<IgnoredKeywordEntity> = emptyList(),
    val kbCredentials: QuickInquiryCredentials = QuickInquiryCredentials(),
    val syncStatusMessage: String = "",
    val totalSpentAmount: Long = 0L,
    val totalIncomeAmount: Long = 0L,
    val totalTransferAmount: Long = 0L,
    val remainingBudgetAmount: Long = 1_500_000L,
    val budgetUsageRatio: Float = 0f,
    val recommendedDailyBudget: Long = 0L,
    val totalMealCount: Int = 0,
    val totalMealSavedAmount: Long = 0L,
    val categorySpendSummaries: List<CategorySpendSummary> = emptyList()
)

private fun HouseholdOverview.toUiState() = HouseholdUiState(
    settings = settings,
    spaces = spaces,
    currentSpace = currentSpace,
    monthOffset = monthOffset,
    billingPeriod = billingPeriod,
    allCategories = allCategories,
    currentSpaceCategoryGroups = currentSpaceCategoryGroups,
    pendingQueue = pendingQueue,
    transactions = transactions,
    mealLogs = mealLogs,
    ignoredKeywords = ignoredKeywords,
    kbCredentials = kbCredentials,
    syncStatusMessage = syncStatusMessage,
    totalSpentAmount = budget.totalSpentAmount,
    totalIncomeAmount = budget.totalIncomeAmount,
    totalTransferAmount = budget.totalTransferAmount,
    remainingBudgetAmount = budget.remainingBudgetAmount,
    budgetUsageRatio = budget.budgetUsageRatio,
    recommendedDailyBudget = budget.recommendedDailyBudget,
    totalMealCount = totalMealCount,
    totalMealSavedAmount = totalMealSavedAmount,
    categorySpendSummaries = budget.categorySpendSummaries
)

/**
 * 앱 전역 단일 ViewModel: 화면 이벤트를 도메인 UseCase로 전달하고 결과를 스낵바 문구로 알립니다.
 */
class MainViewModel(
    initializeApp: InitializeAppUseCase,
    observeHouseholdOverview: ObserveHouseholdOverviewUseCase,
    private val ingestion: IngestionUseCases,
    private val queue: PendingQueueUseCases,
    private val transactions: TransactionUseCases,
    private val categories: CategoryUseCases,
    private val meals: MealUseCases,
    private val account: AccountUseCases,
    private val settings: SettingsUseCases
) : ViewModel() {

    private val _monthOffset = MutableStateFlow(0)
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        viewModelScope.launch { initializeApp() }
    }

    val uiState: StateFlow<HouseholdUiState> = observeHouseholdOverview(_monthOffset)
        .map { it.toUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HouseholdUiState()
        )

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun selectSpace(spaceId: String) {
        viewModelScope.launch {
            _monthOffset.value = 0
            settings.selectSpace(spaceId)
        }
    }

    fun changeMonthOffset(delta: Int) {
        _monthOffset.value = (_monthOffset.value + delta).coerceIn(-24, 12)
    }

    fun resetToCurrentMonth() {
        _monthOffset.value = 0
    }

    fun recordMeal(mealType: MealType, memo: String = "") {
        viewModelScope.launch {
            val unitPrice = meals.record(
                mealType = mealType,
                spaceId = uiState.value.currentSpace.id,
                count = 1,
                memo = memo
            )
            _snackbarMessage.value = "${mealType.emoji} ${mealType.displayName} +1 기록! 배달·외식비 ${
                CurrencyAndDateFormatter.formatWon(unitPrice)
            } 절약했어요!"
        }
    }

    fun deleteMealLog(logId: Long) {
        viewModelScope.launch {
            meals.delete(logId)
            _snackbarMessage.value = "집밥 기록이 삭제되었습니다."
        }
    }

    fun updateMealPriceConfig(config: MealPriceConfig) {
        viewModelScope.launch {
            meals.updatePriceConfig(config)
            _snackbarMessage.value = "끼니별 절약 단가가 저장되었습니다."
        }
    }

    fun approvePendingItem(
        pendingId: Long,
        targetSpaceId: String,
        transactionType: TransactionType,
        amount: Long,
        merchantOrTitle: String,
        memo: String,
        categoryId: Long,
        subCategoryId: Long?
    ) {
        viewModelScope.launch {
            queue.approve(
                pendingId = pendingId,
                targetSpaceId = targetSpaceId,
                transactionType = transactionType,
                amount = amount,
                merchantOrTitle = merchantOrTitle,
                memo = memo,
                categoryId = categoryId,
                subCategoryId = subCategoryId
            )
            _snackbarMessage.value = "'$merchantOrTitle' 내역이 장부에 등록되었습니다."
        }
    }

    fun approveSplitPendingItem(
        pendingId: Long,
        merchantOrTitle: String,
        splits: List<SplitApprovalItem>
    ) {
        viewModelScope.launch {
            runCatching { queue.approveSplit(pendingId, merchantOrTitle, splits) }
                .onSuccess {
                    _snackbarMessage.value = "'$merchantOrTitle' 내역이 ${splits.size}건으로 분할 등록되었습니다."
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.message ?: "분할 등록 중 문제가 발생했습니다."
                }
        }
    }

    fun applyMatchedCancel(pendingId: Long) {
        viewModelScope.launch {
            queue.applyMatchedCancel(pendingId)
            _snackbarMessage.value = "기존 결제 내역이 승인 취소 처리되어 예산에서 복구되었습니다."
        }
    }

    fun loadManualCancelCandidates(pendingId: Long, onLoaded: (List<TransactionEntity>) -> Unit) {
        viewModelScope.launch {
            onLoaded(queue.getManualCancelCandidates(pendingId))
        }
    }

    fun linkCancelToTransaction(pendingId: Long, transactionId: Long) {
        viewModelScope.launch {
            queue.linkCancelToTransaction(pendingId, transactionId)
                .onSuccess {
                    _snackbarMessage.value = "선택한 결제에 취소 내역을 반영했습니다. 예산에서 해당 금액이 복구되었습니다."
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.message ?: "취소 내역을 반영하지 못했습니다."
                }
        }
    }

    fun dismissPendingItem(pendingId: Long, ignoreKeyword: String? = null) {
        viewModelScope.launch {
            queue.dismiss(pendingId, ignoreKeyword)
            _snackbarMessage.value = if (ignoreKeyword.isNullOrBlank()) {
                "확인 대기 목록에서 삭제되었습니다."
            } else {
                "삭제되었으며, 앞으로 '$ignoreKeyword' 단어가 들어간 알림은 자동으로 건너뜁니다."
            }
        }
    }

    fun approveAllPending() {
        viewModelScope.launch {
            if (uiState.value.pendingQueue.isEmpty()) return@launch
            val result = queue.approveAll()
            _snackbarMessage.value = if (result.remainingCount > 0) {
                "확인 대기 내역 ${result.processedCount}건을 저장했습니다. 원본을 찾지 못한 취소 알림 등 ${result.remainingCount}건은 직접 확인해주세요."
            } else {
                "확인 대기 내역 ${result.processedCount}건이 모두 장부에 저장되었습니다."
            }
        }
    }

    fun simulateFinancialNotification(presetIndex: Int) {
        viewModelScope.launch {
            when (presetIndex) {
                0 -> {
                    ingestion.ingestRawNotification(
                        sourceType = IngestionSource.APP_NOTIFICATION,
                        sourceAppName = "KB Pay(국민카드)",
                        title = "KB국민카드 승인",
                        body = "KB국민카드 23,500원 일시불 승인 (주)우아한형제들(배달의민족)"
                    )
                    _snackbarMessage.value = "🔔 [예시 알림] KB Pay 배달의민족 23,500원 결제 알림이 도착했습니다."
                }
                1 -> {
                    ingestion.ingestRawNotification(
                        sourceType = IngestionSource.APP_NOTIFICATION,
                        sourceAppName = "토스(Toss)",
                        title = "토스 결제",
                        body = "토스뱅크 체크카드 5,800원 결제 스타벅스코리아"
                    )
                    _snackbarMessage.value = "🔔 [예시 알림] 토스 스타벅스 5,800원 결제 알림이 도착했습니다."
                }
                2 -> {
                    // 3분 이내 동일 금액 토스 출금 + KB 입금 쌍 시뮬레이션 (내 계좌 간 이체 자동 감지)
                    ingestion.ingestRawNotification(
                        sourceType = IngestionSource.APP_NOTIFICATION,
                        sourceAppName = "토스(Toss)",
                        title = "토스뱅크 출금",
                        body = "출금 200,000원 토스뱅크 -> 국민은행"
                    )
                    ingestion.ingestRawNotification(
                        sourceType = IngestionSource.APP_NOTIFICATION,
                        sourceAppName = "KB스타뱅킹",
                        title = "KB국민은행 입금",
                        body = "입금 200,000원 토스뱅크이체"
                    )
                    _snackbarMessage.value = "🔄 [예시 알림] 토스 출금 ↔ KB 입금(200,000원)이 '내 계좌 이체'로 자동 분류되었습니다."
                }
                3 -> {
                    ingestion.ingestRawNotification(
                        sourceType = IngestionSource.SMS,
                        sourceAppName = "문자 메시지",
                        title = "",
                        body = "[Web발신] KB국민카드 145,000원 승인취소 이마트 트레이더스"
                    )
                    _snackbarMessage.value = "🔄 [예시 알림] 이마트 트레이더스 145,000원 결제 취소 문자가 기존 장부 내역과 자동 연결되었습니다."
                }
            }
        }
    }

    fun runKbQuickInquiryBeta() {
        viewModelScope.launch {
            val bankName = FinancialInstitutions.byId(uiState.value.kbCredentials.institutionId)?.displayName ?: "은행"
            ingestion.runKbQuickInquiry()
                .onSuccess { count ->
                    _snackbarMessage.value = if (count > 0) {
                        "$bankName 계좌에서 새로운 내역 ${count}건을 불러왔습니다."
                    } else {
                        "$bankName 조회 완료: 새로 추가할 내역이 없습니다."
                    }
                }.onFailure { err ->
                    _snackbarMessage.value = err.message ?: "계좌 내역을 불러오는 중 문제가 발생했습니다."
                }
        }
    }

    fun addManualTransaction(
        spaceId: String,
        type: TransactionType,
        amount: Long,
        categoryId: Long,
        subCategoryId: Long?,
        merchantOrTitle: String,
        memo: String
    ) {
        viewModelScope.launch {
            transactions.addManual(
                spaceId = spaceId,
                type = type,
                amount = amount,
                categoryId = categoryId,
                subCategoryId = subCategoryId,
                merchantOrTitle = merchantOrTitle,
                memo = memo
            )
            _snackbarMessage.value = "'$merchantOrTitle' 내역이 등록되었습니다."
        }
    }

    fun cancelTransaction(transactionId: Long) {
        viewModelScope.launch {
            transactions.cancel(transactionId)
            _snackbarMessage.value = "해당 거래가 취소 처리되어 예산 합계에서 제외되었습니다."
        }
    }

    fun deleteTransaction(transactionId: Long) {
        viewModelScope.launch {
            transactions.delete(transactionId)
            _snackbarMessage.value = "거래 내역이 삭제되었습니다."
        }
    }

    fun addCategory(
        spaceId: String,
        parentId: Long?,
        transactionType: TransactionType,
        name: String,
        emoji: String,
        colorHex: String
    ) {
        viewModelScope.launch {
            categories.add(spaceId, parentId, transactionType, name, emoji, colorHex)
            _snackbarMessage.value = if (parentId == null) {
                "상위 카테고리 '$name'이(가) 추가되었습니다."
            } else {
                "세부 카테고리 '$name'이(가) 추가되었습니다."
            }
        }
    }

    fun updateCategory(category: CategoryEntity) {
        viewModelScope.launch {
            categories.update(category)
            _snackbarMessage.value = "카테고리 '${category.name}' 수정 완료"
        }
    }

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch {
            categories.delete(categoryId)
            _snackbarMessage.value = "카테고리가 삭제되었습니다."
        }
    }

    fun updateSpaceBudgetAndStartDay(spaceId: String, targetBudgetAmount: Long, billingStartDay: Int) {
        viewModelScope.launch {
            settings.updateSpaceBudget(spaceId, targetBudgetAmount, billingStartDay)
            _snackbarMessage.value = "목표 예산 및 월 기준일(${BillingCycleCalculator.startDayLabel(billingStartDay)})이 저장되었습니다."
        }
    }

    fun updateUserTier(tier: UserTier) {
        viewModelScope.launch {
            account.updateUserTier(tier)
            _snackbarMessage.value = if (tier == UserTier.FREE) {
                "기본 (혼자 쓰기) 모드로 변경되었습니다. 내 휴대폰에만 안전하게 저장됩니다."
            } else {
                "프리미엄 (함께 쓰기) 모드로 변경되었습니다! 가족·짝꿍과 장부를 함께 쓸 수 있습니다."
            }
        }
    }

    /**
     * @param activity 구글 인증 창을 띄울 Activity
     */
    fun signInWithGoogleAccount(
        activity: Any?,
        manualEmail: String? = null,
        manualName: String? = null
    ) {
        viewModelScope.launch {
            account.signIn(
                uiHost = activity,
                manualGoogleEmail = manualEmail,
                manualDisplayName = manualName
            ).onSuccess { info ->
                _snackbarMessage.value = when {
                    info.isInvitedJoin ->
                        "🎉 '${info.inviterName}'님의 초대가 확인되어 '프리미엄 (함께 쓰기)' 장부(${info.inviteCode})에 자동 연결되었습니다!"
                    info.isExistingUser ->
                        "✅ 구글 계정(${info.email}) 로그인 완료! 회원님의 '${info.resolvedTier.displayName}' 플랜이 적용되었습니다."
                    else ->
                        "👋 환영합니다! 구글 계정(${info.email}) 확인 결과 '${info.resolvedTier.displayName}' 플랜으로 시작합니다."
                }
            }.onFailure { err ->
                _snackbarMessage.value = err.message ?: "구글 로그인 중 문제가 발생했습니다. 잠시 후 다시 시도해주세요."
            }
        }
    }

    fun invitePartnerByGoogleEmail(
        partnerEmail: String,
        partnerName: String,
        customInviteCode: String
    ) {
        viewModelScope.launch {
            account.invitePartner(
                partnerGoogleEmail = partnerEmail,
                partnerName = partnerName,
                customInviteCode = customInviteCode
            ).onSuccess { code ->
                _snackbarMessage.value = "💌 '$partnerEmail' 계정으로 초대장을 보냈습니다! 상대방이 구글 로그인하면($code) 즉시 함께 쓰기가 연결됩니다."
            }.onFailure { err ->
                _snackbarMessage.value = err.message ?: "초대장 발송 중 문제가 발생했습니다."
            }
        }
    }

    fun signOutGoogleAccount() {
        viewModelScope.launch {
            account.signOut()
            _snackbarMessage.value = "구글 계정에서 로그아웃되었습니다."
        }
    }

    fun updateSharedMembers(memberNamesCsv: String, isShared: Boolean, inviteCode: String? = null) {
        viewModelScope.launch {
            if (uiState.value.settings.userTier == UserTier.FREE && isShared) {
                _snackbarMessage.value = "함께 쓰기 기능은 '프리미엄 (함께 쓰기)' 플랜에서 이용할 수 있습니다."
                return@launch
            }
            account.updateSharedMembers(
                memberNamesCsv = memberNamesCsv,
                isShared = isShared,
                customInviteCode = inviteCode
            )
            _snackbarMessage.value = "함께 쓰는 멤버 설정이 저장되고 실시간 장부 동기화가 완료되었습니다."
        }
    }

    fun saveQuickInquiryCredentials(
        institutionId: String,
        accountNo: String,
        birthOrBiz: String,
        pw4: String,
        userRealName: String
    ) {
        viewModelScope.launch {
            ingestion.saveKbCredentials(institutionId, accountNo, birthOrBiz, pw4, userRealName)
            val bankName = FinancialInstitutions.byId(institutionId)?.displayName ?: "은행"
            _snackbarMessage.value = "$bankName 빠른조회 정보와 내 이름이 휴대폰에 안전하게 저장되었습니다."
        }
    }

    fun updateKbAutoInquiry(enabled: Boolean, intervalHours: Int) {
        viewModelScope.launch {
            ingestion.updateKbAutoInquiry(enabled, intervalHours)
                .onSuccess {
                    _snackbarMessage.value = if (enabled) {
                        "계좌 내역을 ${intervalHours}시간마다 자동으로 불러옵니다. 새 내역은 대기내역에서 확인하세요."
                    } else {
                        "계좌 내역 자동 불러오기를 껐습니다."
                    }
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.message ?: "자동 불러오기 설정을 저장하지 못했습니다."
                }
        }
    }

    fun updateEnabledInstitutions(ids: Set<String>) {
        viewModelScope.launch {
            settings.updateEnabledInstitutions(ids)
            _snackbarMessage.value = if (ids.isEmpty()) {
                "모든 금융사 알림 읽기를 껐습니다."
            } else {
                "알림을 읽어올 금융사 ${ids.size}곳을 저장했습니다."
            }
        }
    }

    fun insertSampleData() {
        viewModelScope.launch {
            _snackbarMessage.value = when (settings.insertSampleData()) {
                SampleDataResult.INSERTED -> "예시 거래·집밥 기록·대기 내역을 추가했습니다. 대기내역 탭에서 직접 검수해보세요."
                SampleDataResult.ALREADY_INSERTED -> "예시 데이터는 이미 추가되어 있습니다."
            }
        }
    }

    fun removeIgnoredKeyword(id: Long) {
        viewModelScope.launch {
            settings.deleteIgnoredKeyword(id)
            _snackbarMessage.value = "자동 건너뛰기 단어가 삭제되었습니다."
        }
    }

    fun updateThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            settings.updateThemeMode(themeMode)
            _snackbarMessage.value = "${themeMode.emoji} 화면 스타일이 '${themeMode.displayName}'(으)로 변경되었습니다."
        }
    }

    fun toggleDarkMode(currentlyDark: Boolean) {
        updateThemeMode(if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK)
    }
}
