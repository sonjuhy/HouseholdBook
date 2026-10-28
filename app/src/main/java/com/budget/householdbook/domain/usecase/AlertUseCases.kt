package com.budget.householdbook.domain.usecase

import com.budget.householdbook.core.common.BillingCycleCalculator
import com.budget.householdbook.data.local.entity.PendingTransactionEntity
import com.budget.householdbook.domain.model.BudgetAlert
import com.budget.householdbook.domain.model.BudgetAlertLevel
import com.budget.householdbook.domain.model.BudgetAlertRecord
import com.budget.householdbook.domain.model.CollectionStopReason
import com.budget.householdbook.domain.model.PendingPaymentAlert
import com.budget.householdbook.domain.model.QueueKind
import com.budget.householdbook.domain.model.QueueStatus
import com.budget.householdbook.domain.model.SyncStatus
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.domain.repository.AlertStateRepository
import com.budget.householdbook.domain.repository.AppNotifier
import com.budget.householdbook.domain.repository.AutomationRuleRepository
import com.budget.householdbook.domain.repository.CategoryRepository
import com.budget.householdbook.domain.repository.LedgerSpaceRepository
import com.budget.householdbook.domain.repository.PendingQueueRepository
import com.budget.householdbook.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** 대기 항목 → 새 결제 알림 내용 */
fun PendingTransactionEntity.toPaymentAlert() = PendingPaymentAlert(
    pendingId = id,
    merchant = parsedMerchant,
    amount = parsedAmount,
    type = parsedType,
    // 결제 취소·내 계좌 이체는 원본 선택이나 확인이 필요해 앱에서 처리
    canQuickSave = queueKind == QueueKind.NORMAL && parsedType != TransactionType.TRANSFER
)

/**
 * 알림의 [공용에 저장] / [개인에 저장] 버튼: 앱을 열지 않고 대기 항목을 장부에 저장
 * - 분류: 고른 공간이 추천 공간이면 추천 분류 → 그 공간에서 이 가게로 배운 분류 → 그 공간의 첫 분류
 * @return 저장했으면 true (이미 처리됐거나 바로 저장할 수 없는 항목이면 false)
 */
class QuickSavePendingUseCase(
    private val pendingQueueRepository: PendingQueueRepository,
    private val categoryRepository: CategoryRepository,
    private val ruleRepository: AutomationRuleRepository,
    private val approvePendingItem: ApprovePendingItemUseCase
) {
    suspend operator fun invoke(pendingId: Long, targetSpaceId: String): Boolean {
        val pending = pendingQueueRepository.getById(pendingId) ?: return false
        if (pending.status != QueueStatus.PENDING || !pending.toPaymentAlert().canQuickSave) return false

        val (categoryId, subCategoryId) = when {
            pending.suggestedSpaceId == targetSpaceId && pending.suggestedCategoryId != null ->
                pending.suggestedCategoryId to pending.suggestedSubCategoryId
            else -> learnedCategory(pending, targetSpaceId) ?: defaultCategory(targetSpaceId, pending.parsedType)
                ?: return false
        }
        approvePendingItem(
            pendingId = pending.id,
            targetSpaceId = targetSpaceId,
            transactionType = pending.parsedType,
            amount = pending.parsedAmount,
            merchantOrTitle = pending.parsedMerchant,
            memo = "",
            categoryId = categoryId,
            subCategoryId = subCategoryId
        )
        return true
    }

    private suspend fun learnedCategory(pending: PendingTransactionEntity, spaceId: String): Pair<Long, Long?>? =
        ruleRepository.getAllMerchantRules()
            .filter { it.targetSpaceId == spaceId && pending.parsedMerchant.contains(it.merchantKeyword, ignoreCase = true) }
            .maxByOrNull { it.id }
            ?.let { it.categoryId to it.subCategoryId }

    private suspend fun defaultCategory(spaceId: String, type: TransactionType): Pair<Long, Long?>? {
        val categories = categoryRepository.getCategoriesBySpaceOnce(spaceId)
        val parent = categories.firstOrNull { it.parentId == null && it.transactionType == type }
            ?: categories.firstOrNull { it.parentId == null }
            ?: return null
        return parent.id to categories.firstOrNull { it.parentId == parent.id }?.id
    }
}

/**
 * 예산 80% · 100% 알림: 공간마다 정산 주기마다 단계별로 한 번만
 * - 취소 등으로 다시 아래로 내려갔다가 넘어도 같은 주기에 같은 단계는 다시 알리지 않음
 */
class CheckBudgetAlertUseCase(
    private val spaceRepository: LedgerSpaceRepository,
    private val transactionRepository: TransactionRepository,
    private val alertStateRepository: AlertStateRepository,
    private val notifier: AppNotifier
) {
    suspend operator fun invoke(spaceId: String, today: LocalDate = LocalDate.now()) {
        val space = spaceRepository.getSpaceById(spaceId) ?: return
        val period = BillingCycleCalculator.calculatePeriod(baseDate = today, billingStartDay = space.billingStartDay)
        val spent = transactionRepository
            .observeTransactionsInPeriod(spaceId, period.startMillis, period.endExclusiveMillis)
            .first()
            .filter { !it.isCanceled && it.type == TransactionType.EXPENSE && it.syncStatus != SyncStatus.PENDING_DELETE }
            .sumOf { it.amount }

        val level = BudgetAlertLevel.of(spent, space.targetBudgetAmount)
        if (level == BudgetAlertLevel.NONE) return

        val periodStart = period.startDate.toString()
        val last = alertStateRepository.getLastBudgetAlert(spaceId)
        val alreadySent = if (last?.periodStart == periodStart) last.level else BudgetAlertLevel.NONE
        if (level.ordinal <= alreadySent.ordinal) return

        notifier.notifyBudget(
            BudgetAlert(
                spaceId = space.id,
                spaceName = space.name,
                level = level,
                spent = spent,
                target = space.targetBudgetAmount,
                remainingDays = period.remainingDays
            )
        )
        alertStateRepository.setLastBudgetAlert(spaceId, BudgetAlertRecord(periodStart, level))
    }
}

/**
 * 결제 알림 읽기 권한이 꺼져 자동 수집이 멈췄는지 확인
 * - 한 번이라도 켜서 쓰던 사람에게만, 꺼진 동안 한 번만 알림 (다시 켜면 알림을 지우고 기록 초기화)
 * @param notify false면 상태만 기록 (앱을 보고 있을 때는 알림 대신 설정 화면에서 보임)
 */
class CheckCollectionStatusUseCase(
    private val alertStateRepository: AlertStateRepository,
    private val notifier: AppNotifier
) {
    suspend operator fun invoke(notificationAccessEnabled: Boolean, notify: Boolean) {
        val state = alertStateRepository.getCollectionState()
        if (notificationAccessEnabled) {
            if (!state.notificationAccessEverEnabled || state.notificationAccessAlertSent) {
                alertStateRepository.setCollectionState(
                    state.copy(notificationAccessEverEnabled = true, notificationAccessAlertSent = false)
                )
            }
            notifier.cancelCollectionStopped(CollectionStopReason.NOTIFICATION_ACCESS_OFF)
            return
        }
        if (notify && state.notificationAccessEverEnabled && !state.notificationAccessAlertSent) {
            notifier.notifyCollectionStopped(CollectionStopReason.NOTIFICATION_ACCESS_OFF)
            alertStateRepository.setCollectionState(state.copy(notificationAccessAlertSent = true))
        }
    }
}

/**
 * 은행 계좌 자동 불러오기 결과 기록: 연속 [FAILURE_THRESHOLD]번 실패하면 한 번 알리고, 성공하면 초기화
 */
class RecordBankInquiryResultUseCase(
    private val alertStateRepository: AlertStateRepository,
    private val notifier: AppNotifier
) {
    suspend operator fun invoke(success: Boolean) {
        val state = alertStateRepository.getCollectionState()
        if (success) {
            if (state.bankInquiryFailures != 0 || state.bankInquiryAlertSent) {
                alertStateRepository.setCollectionState(state.copy(bankInquiryFailures = 0, bankInquiryAlertSent = false))
                notifier.cancelCollectionStopped(CollectionStopReason.BANK_INQUIRY_FAILING)
            }
            return
        }
        val failures = state.bankInquiryFailures + 1
        val shouldNotify = failures >= FAILURE_THRESHOLD && !state.bankInquiryAlertSent
        if (shouldNotify) notifier.notifyCollectionStopped(CollectionStopReason.BANK_INQUIRY_FAILING)
        alertStateRepository.setCollectionState(
            state.copy(bankInquiryFailures = failures, bankInquiryAlertSent = state.bankInquiryAlertSent || shouldNotify)
        )
    }

    companion object {
        const val FAILURE_THRESHOLD = 3
    }
}
