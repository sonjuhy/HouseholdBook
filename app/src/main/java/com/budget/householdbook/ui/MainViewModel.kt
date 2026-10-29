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
import com.budget.householdbook.domain.model.SharedRoomInfo
import com.budget.householdbook.domain.model.MealPriceConfig
import com.budget.householdbook.domain.model.MealType
import com.budget.householdbook.domain.model.SplitApprovalItem
import com.budget.householdbook.domain.model.ThemeMode
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.domain.model.UserTier
import com.budget.householdbook.domain.usecase.AccountUseCases
import com.budget.householdbook.domain.usecase.AddCategoryResult
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
    /** 저장된 설정·장부를 처음 읽어오기 전에는 false (튜토리얼이 잠깐 깜빡이지 않도록) */
    val isLoaded: Boolean = false,
    val settings: AppUserSettings = AppUserSettings(),
    val spaces: List<LedgerSpaceEntity> = emptyList(),
    val currentSpace: LedgerSpaceEntity = FALLBACK_SHARED_SPACE,
    val monthOffset: Int = 0,
    val billingPeriod: BillingPeriod = BillingCycleCalculator.calculatePeriod(),
    val allCategories: List<CategoryEntity> = emptyList(),
    val currentSpaceCategoryGroups: List<CategoryGroup> = emptyList(),
    val categoryGroupsBySpace: Map<String, List<CategoryGroup>> = emptyMap(),
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
    isLoaded = true,
    settings = settings,
    spaces = spaces,
    currentSpace = currentSpace,
    monthOffset = monthOffset,
    billingPeriod = billingPeriod,
    allCategories = allCategories,
    currentSpaceCategoryGroups = currentSpaceCategoryGroups,
    categoryGroupsBySpace = categoryGroupsBySpace,
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

    /** '되돌리기' 버튼이 붙는 스낵바 (취소처럼 실수로 누르기 쉬운 동작 직후) */
    private val _undoSnackbar = MutableStateFlow<UndoSnackbar?>(null)
    val undoSnackbar: StateFlow<UndoSnackbar?> = _undoSnackbar.asStateFlow()

    fun clearUndoSnackbar() {
        _undoSnackbar.value = null
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

    /** 아낀 금액 직접 입력 (현재 공간에 기록) */
    fun recordCustomMealSaving(amount: Long, label: String) {
        viewModelScope.launch {
            runCatching {
                meals.recordCustom(amount = amount, label = label, spaceId = uiState.value.currentSpace.id)
            }.onSuccess {
                _snackbarMessage.value = "${MealType.CUSTOM.emoji} ${CurrencyAndDateFormatter.formatWon(amount)} 아낀 금액을 기록했어요!"
            }.onFailure {
                _snackbarMessage.value = it.message ?: "아낀 금액을 기록하지 못했습니다."
            }
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

    /**
     * @param excludeSourceId 함께 제외할 알림 출처(앱 패키지 또는 sms). 같은 결제 알림이 여러 앱에서 올 때 사용
     */
    fun dismissPendingItem(pendingId: Long, ignoreKeyword: String? = null, excludeSourceId: String? = null) {
        viewModelScope.launch {
            queue.dismiss(pendingId, ignoreKeyword)
            val excludedLabel = excludeSourceId?.let { id ->
                val current = uiState.value.settings
                settings.updateExcludedSources(current.excludedSourceIds + id)
                current.seenSources[id] ?: id
            }
            _snackbarMessage.value = when {
                excludedLabel != null -> "삭제되었으며, 앞으로 '$excludedLabel' 알림은 읽지 않습니다."
                ignoreKeyword.isNullOrBlank() -> "확인 대기 목록에서 삭제되었습니다."
                else -> "삭제되었으며, 앞으로 '$ignoreKeyword' 단어가 들어간 알림은 자동으로 건너뜁니다."
            }
        }
    }

    /** 검수 카드에서 공간·유형·분류를 바꾸면 대기 항목에 바로 저장 */
    fun updatePendingClassification(
        pendingId: Long,
        spaceId: String,
        type: TransactionType,
        categoryId: Long?,
        subCategoryId: Long?
    ) {
        viewModelScope.launch {
            queue.updateClassification(pendingId, spaceId, type, categoryId, subCategoryId)
        }
    }

    fun updateExcludedSources(ids: Set<String>, autoFilterDuplicates: Boolean) {
        viewModelScope.launch {
            settings.updateExcludedSources(ids)
            settings.updateAutoFilterDuplicates(autoFilterDuplicates)
            _snackbarMessage.value = if (ids.isEmpty()) {
                "모든 금융 앱 알림을 읽습니다."
            } else {
                "${ids.size}개 출처의 알림은 앞으로 읽지 않습니다."
            }
        }
    }

    fun updateDefaultIngestSpace(spaceId: String?) {
        viewModelScope.launch {
            settings.updateDefaultIngestSpace(spaceId)
            val name = spaceId?.let { id -> uiState.value.spaces.firstOrNull { it.id == id }?.name }
            _snackbarMessage.value = "새 알림 내역은 ${name ?: "마지막으로 보던 공간"}에 먼저 담깁니다."
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
            _undoSnackbar.value = UndoSnackbar(
                message = "거래를 취소 처리해 예산 합계에서 뺐습니다.",
                onUndo = { restoreCanceledTransaction(transactionId) }
            )
        }
    }

    fun restoreCanceledTransaction(transactionId: Long) {
        viewModelScope.launch {
            transactions.restoreCancel(transactionId)
            _snackbarMessage.value = "취소를 되돌렸습니다. 다시 예산 합계에 포함됩니다."
        }
    }

    /** 등록된 거래의 이름·금액·날짜·분류·메모 수정 */
    fun updateTransaction(
        transactionId: Long,
        merchantOrTitle: String,
        amount: Long,
        transactionDate: Long,
        categoryId: Long,
        subCategoryId: Long?,
        memo: String
    ) {
        viewModelScope.launch {
            runCatching {
                transactions.update(transactionId, merchantOrTitle, amount, transactionDate, categoryId, subCategoryId, memo)
            }.onSuccess { saved ->
                val period = uiState.value.billingPeriod
                _snackbarMessage.value = when {
                    saved == null -> "바뀐 내용이 없어요."
                    saved.transactionDate < period.startMillis || saved.transactionDate >= period.endExclusiveMillis ->
                        "수정했어요. 날짜가 바뀌어 다른 정산 주기에서 볼 수 있어요."
                    else -> "수정했어요."
                }
            }.onFailure {
                _snackbarMessage.value = it.message ?: "수정하지 못했어요."
            }
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
            val spaceLabel = uiState.value.spaces.firstOrNull { it.id == spaceId }?.name ?: "이 장부"
            _snackbarMessage.value = when (categories.add(spaceId, parentId, transactionType, name, emoji, colorHex)) {
                AddCategoryResult.ADDED ->
                    if (parentId == null) "$spaceLabel 분류에 '${name.trim()}'을(를) 추가했어요."
                    else "세부 분류 '${name.trim()}'을(를) 추가했어요."
                AddCategoryResult.DUPLICATE_NAME -> "$spaceLabel 분류에 이미 '${name.trim()}'이(가) 있어요."
                AddCategoryResult.PARENT_NOT_FOUND -> "대분류를 찾을 수 없어요. 다시 시도해 주세요."
                AddCategoryResult.BLANK_NAME -> "분류 이름을 입력해 주세요."
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
                "혼자 쓰기로 바꿨어요. 이 휴대폰에만 저장돼요."
            } else {
                "함께 쓰기로 바꿨어요. 가족을 초대해 보세요."
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
                    info.isInvitedJoin -> "${info.inviterName}님의 공유 장부에 연결됐어요."
                    info.isExistingUser -> "${info.displayName}님, 다시 오셨네요!"
                    else -> "${info.displayName}님, 반가워요!"
                }
            }.onFailure { err ->
                _snackbarMessage.value = err.message ?: "로그인하지 못했어요. 잠시 후 다시 시도해 주세요."
            }
        }
    }

    /** 로그인해 둔 상태에서 받은 함께 쓰기 초대가 있으면 수락 (앱을 열거나 앱으로 돌아올 때) */
    fun checkPendingInvitation() {
        viewModelScope.launch {
            account.acceptPendingInvitation()?.let { info ->
                _snackbarMessage.value = "${info.inviterName}님의 초대를 받아 공유 장부에 연결됐어요."
            }
        }
    }

    /** 장부에 표시할 내 이름 바꾸기 */
    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            account.updateDisplayName(name)
                .onSuccess { _snackbarMessage.value = "이름을 '${name.trim()}'(으)로 바꿨어요." }
                .onFailure {
                    _snackbarMessage.value = if (it is IllegalArgumentException) {
                        it.message
                    } else {
                        // 이 휴대폰에는 저장됨. 공유 장부·프로필 반영만 실패 (인터넷 등)
                        "이 휴대폰에는 저장했어요. 함께 쓰는 장부에는 인터넷에 연결된 뒤 다시 바꿔 주세요."
                    }
                }
        }
    }

    // ---------------- 함께 쓰는 멤버 관리 ----------------

    private val _sharedMembers = MutableStateFlow<SharedMembersState>(SharedMembersState.Idle)
    val sharedMembers: StateFlow<SharedMembersState> = _sharedMembers.asStateFlow()

    fun loadSharedMembers() {
        viewModelScope.launch {
            _sharedMembers.value = SharedMembersState.Loading
            _sharedMembers.value = account.loadMembers().fold(
                onSuccess = { SharedMembersState.Loaded(it) },
                onFailure = { SharedMembersState.Error(it.message ?: "멤버 목록을 불러오지 못했습니다.") }
            )
        }
    }

    fun cancelInvitation(email: String) = runMemberAction(
        action = { account.cancelInvitation(email) },
        successMessage = "'$email' 초대를 취소했습니다."
    )

    fun removeSharedMember(email: String) = runMemberAction(
        action = { account.removeMember(email) },
        successMessage = "'$email' 님을 공유 장부에서 내보냈습니다."
    )

    fun leaveSharedSpace() {
        viewModelScope.launch {
            account.leaveSharedSpace()
                .onSuccess {
                    _sharedMembers.value = SharedMembersState.Idle
                    _snackbarMessage.value = "공유 장부에서 나왔습니다. 이 휴대폰의 기록은 그대로 남아 있습니다."
                }
                .onFailure { _snackbarMessage.value = it.message ?: "나가기 중 문제가 발생했습니다." }
        }
    }

    fun transferOwnership(email: String, name: String) = runMemberAction(
        action = { account.transferOwnership(email) },
        successMessage = "'$name' 님에게 방장을 넘겼습니다. 이제 공유 장부에서 나가거나 탈퇴할 수 있습니다."
    )

    // ---------------- 회원 탈퇴 ----------------

    private val _accountDeletion = MutableStateFlow<AccountDeletionState>(AccountDeletionState.Idle)
    val accountDeletion: StateFlow<AccountDeletionState> = _accountDeletion.asStateFlow()

    /**
     * @param activity 본인 재확인용 구글 인증 창을 띄울 Activity
     * @param wipeLocalData 이 휴대폰의 기록도 지울지 (지우면 앱이 종료됨)
     */
    fun deleteAccount(activity: Any?, wipeLocalData: Boolean) {
        if (_accountDeletion.value == AccountDeletionState.InProgress) return
        viewModelScope.launch {
            _accountDeletion.value = AccountDeletionState.InProgress
            account.deleteAccount(activity, wipeLocalData)
                .onSuccess {
                    _sharedMembers.value = SharedMembersState.Idle
                    _accountDeletion.value = AccountDeletionState.Idle
                    _snackbarMessage.value = "회원 탈퇴가 완료되었습니다. 이 휴대폰의 기록은 로그인 없이 계속 쓸 수 있습니다."
                }
                .onFailure {
                    _accountDeletion.value = AccountDeletionState.Failed(it.message ?: "회원 탈퇴 중 문제가 발생했습니다.")
                }
        }
    }

    /** 탈퇴 창을 닫을 때 지난 오류 문구 지우기 */
    fun clearAccountDeletionState() {
        if (_accountDeletion.value is AccountDeletionState.Failed) _accountDeletion.value = AccountDeletionState.Idle
    }

    private fun runMemberAction(action: suspend () -> Result<SharedRoomInfo>, successMessage: String) {
        viewModelScope.launch {
            action()
                .onSuccess {
                    _sharedMembers.value = SharedMembersState.Loaded(it)
                    _snackbarMessage.value = successMessage
                }
                .onFailure { _snackbarMessage.value = it.message ?: "멤버 관리 중 문제가 발생했습니다." }
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
            ).onSuccess {
                _snackbarMessage.value = "$partnerEmail 님에게 초대를 보냈어요. 이 계정으로 로그인하면 바로 함께 쓸 수 있어요."
            }.onFailure { err ->
                _snackbarMessage.value = err.message ?: "초대장 발송 중 문제가 발생했습니다."
            }
        }
    }

    fun signOutGoogleAccount() {
        viewModelScope.launch {
            account.signOut()
            _snackbarMessage.value = "로그아웃했어요."
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

    /**
     * 튜토리얼을 마치거나 건너뜀
     * @param withSampleData 마지막 화면에서 '예시 데이터로 둘러보기'를 고른 경우
     */
    fun completeOnboarding(withSampleData: Boolean) {
        viewModelScope.launch {
            settings.updateOnboardingSeen(true)
            if (withSampleData) insertSampleData()
        }
    }

    /** 설정 > 앱 사용법 다시 보기 */
    fun replayOnboarding() {
        viewModelScope.launch { settings.updateOnboardingSeen(false) }
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

/** 되돌리기 버튼이 있는 스낵바 내용 */
data class UndoSnackbar(
    val message: String,
    val actionLabel: String = "되돌리기",
    val onUndo: () -> Unit
)

/** 멤버 관리 화면 상태 */
sealed interface AccountDeletionState {
    data object Idle : AccountDeletionState
    data object InProgress : AccountDeletionState
    data class Failed(val message: String) : AccountDeletionState
}

sealed interface SharedMembersState {
    data object Idle : SharedMembersState
    data object Loading : SharedMembersState
    data class Loaded(val info: SharedRoomInfo) : SharedMembersState
    data class Error(val message: String) : SharedMembersState
}
