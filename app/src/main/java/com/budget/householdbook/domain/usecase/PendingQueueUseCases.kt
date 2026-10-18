package com.budget.householdbook.domain.usecase

import com.budget.householdbook.core.common.CurrencyAndDateFormatter
import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.data.local.entity.IgnoredKeywordEntity
import com.budget.householdbook.data.local.entity.MerchantCategoryRuleEntity
import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.domain.model.BulkApprovalResult
import com.budget.householdbook.domain.model.QueueKind
import com.budget.householdbook.domain.model.QueueStatus
import com.budget.householdbook.domain.model.SplitApprovalItem
import com.budget.householdbook.domain.model.SyncStatus
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.domain.model.UserTier
import com.budget.householdbook.domain.repository.AutomationRuleRepository
import com.budget.householdbook.domain.repository.CategoryRepository
import com.budget.householdbook.domain.repository.LedgerSpaceRepository
import com.budget.householdbook.domain.repository.PendingQueueRepository
import com.budget.householdbook.domain.repository.SettingsRepository
import com.budget.householdbook.domain.repository.SharedSyncRepository
import com.budget.householdbook.domain.repository.TransactionRepository
import com.budget.householdbook.domain.repository.TransactionRunner
import kotlinx.coroutines.flow.first
import java.util.UUID

/** 취소 원본 직접 선택: 취소 알림 기준 최근 60일 */
internal const val MANUAL_CANCEL_LOOKBACK_MILLIS = 60L * 24 * 3600 * 1000

/**
 * 대기 큐 단건 수정 및 승인(장부 저장)
 * - 이체 쌍으로 묶인 상대 항목도 함께 승인 처리
 * - 가맹점명 -> 공간/카테고리 자동 분류 규칙 학습
 */
class ApprovePendingItemUseCase(
    private val transactionRunner: TransactionRunner,
    private val pendingQueueRepository: PendingQueueRepository,
    private val transactionRepository: TransactionRepository,
    private val spaceRepository: LedgerSpaceRepository,
    private val ruleRepository: AutomationRuleRepository,
    private val settingsRepository: SettingsRepository,
    private val sharedSyncRepository: SharedSyncRepository
) {
    suspend operator fun invoke(
        pendingId: Long,
        targetSpaceId: String,
        transactionType: TransactionType,
        amount: Long,
        merchantOrTitle: String,
        memo: String,
        categoryId: Long,
        subCategoryId: Long?
    ) {
        val pending = pendingQueueRepository.getById(pendingId) ?: return
        // 연속 터치 등으로 이미 처리된 항목이 다시 저장되는 것을 방지
        if (pending.status != QueueStatus.PENDING) return
        val settings = settingsRepository.userSettingsFlow.first()
        val space = spaceRepository.getSpaceById(targetSpaceId)

        transactionRunner.runInTransaction {
            transactionRepository.insertTransaction(
                TransactionEntity(
                    remoteId = UUID.randomUUID().toString(),
                    spaceId = targetSpaceId,
                    type = transactionType,
                    amount = amount,
                    categoryId = categoryId,
                    subCategoryId = subCategoryId,
                    merchantOrTitle = merchantOrTitle.trim().ifBlank { pending.parsedMerchant },
                    memo = memo.trim(),
                    originQueueId = pending.id,
                    authorName = settings.userRealName,
                    transactionDate = pending.occurredAt,
                    syncStatus = if (settings.userTier == UserTier.PRO && space?.isRemoteShared == true) {
                        SyncStatus.PENDING_UPLOAD
                    } else {
                        SyncStatus.SYNCED
                    }
                )
            )
            pendingQueueRepository.updateStatus(pending.id, QueueStatus.APPROVED)

            // 이체 쌍이 함께 묶여 있던 경우 파트너도 함께 승인 완료 처리
            pending.pairedQueueId?.let { partnerId ->
                pendingQueueRepository.updateStatus(partnerId, QueueStatus.APPROVED)
            }

            // 가맹점명 -> 세부 카테고리 학습 저장
            val cleanMerchant = merchantOrTitle.trim()
            if (cleanMerchant.length >= 2 && transactionType != TransactionType.TRANSFER) {
                ruleRepository.upsertMerchantRule(
                    MerchantCategoryRuleEntity(
                        merchantKeyword = cleanMerchant,
                        targetSpaceId = targetSpaceId,
                        categoryId = categoryId,
                        subCategoryId = subCategoryId
                    )
                )
            }
        }

        sharedSyncRepository.syncIfEligible(settings.userTier, space?.isRemoteShared == true)
    }
}

/**
 * 대기 큐 1건 결제를 '공통 생활비' + '개인 용돈' 등으로 금액을 쪼개어 분할 승인(Split Transaction)
 * - 분할 금액 합계가 원본 금액과 다르면 [IllegalArgumentException]
 * - 원격 업로드 대상(PENDING_UPLOAD)은 공유 중인 공통 생활비 분할분뿐
 */
class ApproveSplitPendingItemUseCase(
    private val transactionRunner: TransactionRunner,
    private val pendingQueueRepository: PendingQueueRepository,
    private val transactionRepository: TransactionRepository,
    private val spaceRepository: LedgerSpaceRepository,
    private val settingsRepository: SettingsRepository,
    private val sharedSyncRepository: SharedSyncRepository
) {
    suspend operator fun invoke(
        pendingId: Long,
        merchantOrTitle: String,
        splits: List<SplitApprovalItem>
    ) {
        if (splits.isEmpty()) return
        val pending = pendingQueueRepository.getById(pendingId) ?: return
        if (pending.status != QueueStatus.PENDING) return
        // 분할 금액 합계는 원본 금액과 반드시 일치해야 함
        require(splits.sumOf { it.amount } == pending.parsedAmount) {
            "분할 금액 합계가 원본 금액과 다릅니다."
        }
        val settings = settingsRepository.userSettingsFlow.first()
        val sharedSpace = spaceRepository.getSpaceById(AppDatabase.SPACE_ID_SHARED)
        val isSharedSynced = settings.userTier == UserTier.PRO && sharedSpace?.isRemoteShared == true

        transactionRunner.runInTransaction {
            val entities = splits.filter { it.amount > 0L }.mapIndexed { index, split ->
                TransactionEntity(
                    remoteId = UUID.randomUUID().toString(),
                    spaceId = split.spaceId,
                    type = pending.parsedType,
                    amount = split.amount,
                    categoryId = split.categoryId,
                    subCategoryId = split.subCategoryId,
                    merchantOrTitle = "$merchantOrTitle (분할 ${index + 1})",
                    memo = split.memo.ifBlank { "원본 ${CurrencyAndDateFormatter.formatWon(pending.parsedAmount)} 분할 등록" },
                    originQueueId = pending.id,
                    authorName = settings.userRealName,
                    transactionDate = pending.occurredAt,
                    syncStatus = if (isSharedSynced && split.spaceId == AppDatabase.SPACE_ID_SHARED) {
                        SyncStatus.PENDING_UPLOAD
                    } else {
                        SyncStatus.SYNCED
                    }
                )
            }
            transactionRepository.insertTransactions(entities)
            pendingQueueRepository.updateStatus(pending.id, QueueStatus.APPROVED)
        }

        sharedSyncRepository.syncIfEligible(settings.userTier, sharedSpace?.isRemoteShared == true)
    }
}

/**
 * 대기 큐의 결제 취소 매칭 항목을 원본 거래에 반영하여 취소(isCanceled = true) 처리
 */
class ApplyMatchedCancelUseCase(
    private val transactionRunner: TransactionRunner,
    private val pendingQueueRepository: PendingQueueRepository,
    private val transactionRepository: TransactionRepository,
    private val syncSharedChanges: SyncSharedChangesUseCase
) {
    suspend operator fun invoke(pendingId: Long) {
        val pending = pendingQueueRepository.getById(pendingId) ?: return
        if (pending.status != QueueStatus.PENDING) return
        transactionRunner.runInTransaction {
            pending.matchedTransactionId?.let { txId ->
                transactionRepository.markTransactionCanceled(txId)
            }
            pendingQueueRepository.updateStatus(pendingId, QueueStatus.APPROVED)
        }
        syncSharedChanges()
    }
}

/**
 * 원본을 자동으로 찾지 못한 취소 알림(CANCEL_UNMATCHED)에 연결할 수 있는 원본 지출 후보
 * - 취소 알림 발생 60일 전 이후의 미취소 지출 중, 환불 금액 이상인 거래 (같은 금액 우선)
 */
class GetManualCancelCandidatesUseCase(
    private val pendingQueueRepository: PendingQueueRepository,
    private val transactionRepository: TransactionRepository
) {
    suspend operator fun invoke(pendingId: Long): List<TransactionEntity> {
        val pending = pendingQueueRepository.getById(pendingId) ?: return emptyList()
        val since = pending.occurredAt - MANUAL_CANCEL_LOOKBACK_MILLIS
        return transactionRepository.findManualCancelCandidates(pending.parsedAmount, since)
    }
}

/**
 * 사용자가 직접 고른 원본 지출에 취소 알림을 반영합니다.
 * - 금액이 같으면 원본을 취소(isCanceled) 처리
 * - 부분 취소(환불 금액 < 원본 금액)면 원본 금액에서 환불 금액만큼 차감
 */
class LinkCancelToTransactionUseCase(
    private val transactionRunner: TransactionRunner,
    private val pendingQueueRepository: PendingQueueRepository,
    private val transactionRepository: TransactionRepository,
    private val syncSharedChanges: SyncSharedChangesUseCase
) {
    suspend operator fun invoke(pendingId: Long, transactionId: Long): Result<Unit> = runCatching {
        val pending = pendingQueueRepository.getById(pendingId) ?: error("대기 내역을 찾을 수 없습니다.")
        check(pending.status == QueueStatus.PENDING) { "이미 처리된 대기 내역입니다." }
        val original = transactionRepository.getById(transactionId) ?: error("원본 결제 내역을 찾을 수 없습니다.")
        check(!original.isCanceled) { "이미 취소된 결제입니다." }
        check(pending.parsedAmount <= original.amount) { "환불 금액이 원본 결제 금액보다 큽니다." }

        transactionRunner.runInTransaction {
            if (pending.parsedAmount == original.amount) {
                transactionRepository.markTransactionCanceled(original.id)
            } else {
                val partialMemo = listOf(
                    original.memo,
                    "부분 취소 ${CurrencyAndDateFormatter.formatWon(pending.parsedAmount)}"
                ).filter { it.isNotBlank() }.joinToString(" · ")
                transactionRepository.applyPartialCancel(
                    transactionId = original.id,
                    newAmount = original.amount - pending.parsedAmount,
                    memo = partialMemo
                )
            }
            pendingQueueRepository.markCancelLinkedAndApproved(pending.id, original.id)
        }
        syncSharedChanges()
    }
}

/**
 * 대기 큐 항목 삭제(무시) 및 선택적 키워드 영구 차단 등록
 */
class DismissPendingItemUseCase(
    private val transactionRunner: TransactionRunner,
    private val pendingQueueRepository: PendingQueueRepository,
    private val ruleRepository: AutomationRuleRepository
) {
    suspend operator fun invoke(pendingId: Long, ignoreKeyword: String? = null) {
        transactionRunner.runInTransaction {
            pendingQueueRepository.updateStatus(pendingId, QueueStatus.DISMISSED)
            if (!ignoreKeyword.isNullOrBlank()) {
                ruleRepository.insertIgnoredKeyword(IgnoredKeywordEntity(keyword = ignoreKeyword.trim()))
            }
        }
    }
}

/**
 * 대기 큐 전체 일괄 승인
 * - 원본이 매칭된 취소 알림은 취소 반영
 * - 원본을 못 찾은 취소 알림은 사용자가 직접 확인하도록 대기 목록에 남김
 * - 그 외는 추천 공간/카테고리로 승인 (이체 쌍 상대 항목은 함께 승인되므로 건너뜀)
 */
class ApproveAllPendingItemsUseCase(
    private val pendingQueueRepository: PendingQueueRepository,
    private val categoryRepository: CategoryRepository,
    private val approvePendingItem: ApprovePendingItemUseCase,
    private val applyMatchedCancel: ApplyMatchedCancelUseCase
) {
    suspend operator fun invoke(): BulkApprovalResult {
        val items = pendingQueueRepository.observePendingQueue().first()
        var processedCount = 0
        for (item in items) {
            // 이체 쌍의 상대 항목은 함께 승인되므로 최신 상태를 다시 확인
            if (pendingQueueRepository.getById(item.id)?.status != QueueStatus.PENDING) continue
            if (item.queueKind == QueueKind.CANCEL_MATCHED && item.matchedTransactionId != null) {
                applyMatchedCancel(item.id)
                processedCount++
            } else if (item.queueKind == QueueKind.CANCEL_UNMATCHED || item.queueKind == QueueKind.CANCEL_MATCHED) {
                // 원본을 못 찾은 취소 알림은 사용자가 직접 확인하도록 대기 목록에 남겨둠
                continue
            } else {
                val fallbackCatId = item.suggestedCategoryId
                    ?: categoryRepository.getCategoriesBySpaceOnce(item.suggestedSpaceId).firstOrNull()?.id
                    ?: 1L
                approvePendingItem(
                    pendingId = item.id,
                    targetSpaceId = item.suggestedSpaceId,
                    transactionType = item.parsedType,
                    amount = item.parsedAmount,
                    merchantOrTitle = item.parsedMerchant,
                    memo = "",
                    categoryId = fallbackCatId,
                    subCategoryId = item.suggestedSubCategoryId
                )
                processedCount++
            }
        }
        return BulkApprovalResult(
            processedCount = processedCount,
            remainingCount = pendingQueueRepository.observePendingCount().first()
        )
    }
}
