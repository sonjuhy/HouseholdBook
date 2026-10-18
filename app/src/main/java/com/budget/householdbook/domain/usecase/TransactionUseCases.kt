package com.budget.householdbook.domain.usecase

import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.domain.model.SyncStatus
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.domain.model.UserTier
import com.budget.householdbook.domain.repository.LedgerSpaceRepository
import com.budget.householdbook.domain.repository.SettingsRepository
import com.budget.householdbook.domain.repository.SharedSyncRepository
import com.budget.householdbook.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * 수동 거래 내역 직접 추가 (사용자가 직접 입력한 내역이므로 대기 큐를 거치지 않음)
 */
class AddManualTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val spaceRepository: LedgerSpaceRepository,
    private val settingsRepository: SettingsRepository,
    private val sharedSyncRepository: SharedSyncRepository
) {
    suspend operator fun invoke(
        spaceId: String,
        type: TransactionType,
        amount: Long,
        categoryId: Long,
        subCategoryId: Long?,
        merchantOrTitle: String,
        memo: String,
        transactionDate: Long = System.currentTimeMillis()
    ) {
        val settings = settingsRepository.userSettingsFlow.first()
        val space = spaceRepository.getSpaceById(spaceId)
        transactionRepository.insertTransaction(
            TransactionEntity(
                remoteId = UUID.randomUUID().toString(),
                spaceId = spaceId,
                type = type,
                amount = amount,
                categoryId = categoryId,
                subCategoryId = subCategoryId,
                merchantOrTitle = merchantOrTitle.trim().ifBlank { "직접 입력 내역" },
                memo = memo.trim(),
                authorName = settings.userRealName,
                transactionDate = transactionDate,
                syncStatus = if (settings.userTier == UserTier.PRO && space?.isRemoteShared == true) {
                    SyncStatus.PENDING_UPLOAD
                } else {
                    SyncStatus.SYNCED
                }
            )
        )
        sharedSyncRepository.syncIfEligible(settings.userTier, space?.isRemoteShared == true)
    }
}

/**
 * 확정 거래를 취소 처리(isCanceled = true)하여 예산·지출 합계에서 제외
 */
class CancelTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val syncSharedChanges: SyncSharedChangesUseCase
) {
    suspend operator fun invoke(transactionId: Long) {
        transactionRepository.markTransactionCanceled(transactionId)
        syncSharedChanges()
    }
}

/**
 * 거래 삭제: 함께 쓰는 공통 생활비 내역은 원격에서도 지워지도록 PENDING_DELETE로 표시 후 동기화하고,
 * 그 외(개인 용돈 / 혼자 쓰기)는 즉시 로컬에서 삭제합니다.
 */
class DeleteTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val syncSharedChanges: SyncSharedChangesUseCase
) {
    suspend operator fun invoke(transactionId: Long) {
        val tx = transactionRepository.getById(transactionId) ?: return
        if (syncSharedChanges.isSharedSyncActive(tx.spaceId)) {
            transactionRepository.markPendingDelete(transactionId)
            syncSharedChanges()
        } else {
            transactionRepository.deleteById(transactionId)
        }
    }
}
