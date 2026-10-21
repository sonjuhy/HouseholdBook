package com.budget.householdbook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.data.local.entity.IgnoredKeywordEntity
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.data.local.entity.MealSavingLogEntity
import com.budget.householdbook.data.local.entity.MerchantCategoryRuleEntity
import com.budget.householdbook.data.local.entity.PendingTransactionEntity
import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.domain.model.MealPeriodTotal
import com.budget.householdbook.domain.model.QueueStatus
import com.budget.householdbook.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerSpaceDao {
    @Query("SELECT * FROM ledger_spaces ORDER BY type ASC")
    fun observeAllSpaces(): Flow<List<LedgerSpaceEntity>>

    @Query("SELECT * FROM ledger_spaces WHERE id = :spaceId LIMIT 1")
    suspend fun getSpaceById(spaceId: String): LedgerSpaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSpace(space: LedgerSpaceEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSpacesIfEmpty(spaces: List<LedgerSpaceEntity>)

    @Query("UPDATE ledger_spaces SET targetBudgetAmount = :targetAmount, billingStartDay = :billingStartDay, syncStatus = 'PENDING_UPLOAD' WHERE id = :spaceId")
    suspend fun updateBudgetAndCycle(spaceId: String, targetAmount: Long, billingStartDay: Int)

    @Query("UPDATE ledger_spaces SET memberNamesCsv = :memberNamesCsv, inviteCode = :inviteCode, isRemoteShared = :isShared WHERE id = :spaceId")
    suspend fun updateShareConfig(spaceId: String, memberNamesCsv: String, inviteCode: String?, isShared: Boolean)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE spaceId = :spaceId AND isArchived = 0 ORDER BY sortOrder ASC, id ASC")
    fun observeCategoriesBySpace(spaceId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE isArchived = 0 ORDER BY sortOrder ASC, id ASC")
    fun observeAllActiveCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE spaceId = :spaceId AND isArchived = 0 ORDER BY sortOrder ASC, id ASC")
    suspend fun getCategoriesBySpaceOnce(spaceId: String): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>): List<Long>

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query("UPDATE categories SET isArchived = 1 WHERE id = :categoryId OR parentId = :categoryId")
    suspend fun archiveCategoryAndSubs(categoryId: Long)
}

@Dao
interface PendingTransactionDao {
    @Query("SELECT * FROM pending_transactions WHERE status = :status ORDER BY occurredAt DESC, id DESC")
    fun observePendingQueue(status: QueueStatus = QueueStatus.PENDING): Flow<List<PendingTransactionEntity>>

    @Query("SELECT COUNT(*) FROM pending_transactions WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM pending_transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PendingTransactionEntity?

    @Query("SELECT * FROM pending_transactions WHERE status = 'PENDING' AND occurredAt >= :sinceMillis ORDER BY occurredAt DESC")
    suspend fun getRecentPendingSince(sinceMillis: Long): List<PendingTransactionEntity>

    @Query("SELECT * FROM pending_transactions WHERE occurredAt >= :sinceMillis ORDER BY occurredAt DESC")
    suspend fun getRecentSince(sinceMillis: Long): List<PendingTransactionEntity>

    @Query(
        """
        UPDATE pending_transactions
        SET suggestedSpaceId = :spaceId, parsedType = :type, suggestedCategoryId = :categoryId, suggestedSubCategoryId = :subCategoryId
        WHERE id = :id AND status = 'PENDING'
        """
    )
    suspend fun updateClassification(id: Long, spaceId: String, type: TransactionType, categoryId: Long?, subCategoryId: Long?)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreDuplicate(item: PendingTransactionEntity): Long

    @Update
    suspend fun update(item: PendingTransactionEntity)

    @Query("UPDATE pending_transactions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: QueueStatus)

    @Query("UPDATE pending_transactions SET queueKind = 'CANCEL_MATCHED', matchedTransactionId = :transactionId, status = 'APPROVED' WHERE id = :id")
    suspend fun markCancelLinkedAndApproved(id: Long, transactionId: Long)

    @Query("UPDATE pending_transactions SET queueKind = 'TRANSFER_PAIR', pairedQueueId = :partnerId, parsedType = 'TRANSFER' WHERE id = :id")
    suspend fun markAsTransferPair(id: Long, partnerId: Long)
}

@Dao
interface TransactionDao {
    @Query(
        """
        SELECT * FROM transactions 
        WHERE spaceId = :spaceId 
          AND transactionDate >= :startMillis 
          AND transactionDate < :endExclusiveMillis
          AND syncStatus != 'PENDING_DELETE'
        ORDER BY transactionDate DESC, id DESC
        """
    )
    fun observeTransactionsInPeriod(
        spaceId: String,
        startMillis: Long,
        endExclusiveMillis: Long
    ): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions 
        WHERE isCanceled = 0 
          AND amount = :amount 
          AND transactionDate >= :sinceMillis
        ORDER BY transactionDate DESC
        """
    )
    suspend fun findCancelCandidatesByAmount(amount: Long, sinceMillis: Long): List<TransactionEntity>

    /**
     * 취소 대상 원본 직접 선택용 후보: 최근 미취소 지출 중 환불 금액 이상인 거래 (같은 금액 우선)
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE isCanceled = 0
          AND type = 'EXPENSE'
          AND syncStatus != 'PENDING_DELETE'
          AND amount >= :refundAmount
          AND transactionDate >= :sinceMillis
        ORDER BY (amount = :refundAmount) DESC, transactionDate DESC
        LIMIT 50
        """
    )
    suspend fun findManualCancelCandidates(refundAmount: Long, sinceMillis: Long): List<TransactionEntity>

    @Query("UPDATE transactions SET amount = :newAmount, memo = :memo, updatedAt = :nowMillis, syncStatus = 'PENDING_UPLOAD' WHERE id = :transactionId")
    suspend fun applyPartialCancel(transactionId: Long, newAmount: Long, memo: String, nowMillis: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Query("UPDATE transactions SET isCanceled = 1, updatedAt = :nowMillis, syncStatus = 'PENDING_UPLOAD' WHERE id = :transactionId")
    suspend fun markTransactionCanceled(transactionId: Long, nowMillis: Long = System.currentTimeMillis())

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteById(transactionId: Long)

    @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1")
    suspend fun getById(transactionId: Long): TransactionEntity?

    @Query("UPDATE transactions SET syncStatus = 'PENDING_DELETE', updatedAt = :nowMillis WHERE id = :transactionId")
    suspend fun markPendingDelete(transactionId: Long, nowMillis: Long = System.currentTimeMillis())

    @Query("DELETE FROM transactions WHERE remoteId = :remoteId")
    suspend fun deleteByRemoteId(remoteId: String)

    @Query("SELECT COUNT(*) FROM transactions WHERE syncStatus != 'SYNCED'")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE spaceId = :spaceId AND syncStatus != 'SYNCED'")
    suspend fun countUnsyncedInSpace(spaceId: String): Int

    // 공유 대상 공간(공통 생활비)의 업로드 대기 내역만 조회 — 개인 용돈은 절대 원격으로 올리지 않음
    @Query("SELECT * FROM transactions WHERE spaceId = :spaceId AND syncStatus = 'PENDING_UPLOAD'")
    suspend fun getPendingUploadTransactions(spaceId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE spaceId = :spaceId AND syncStatus = 'PENDING_DELETE'")
    suspend fun getPendingDeleteTransactions(spaceId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE spaceId = :spaceId AND syncStatus != 'PENDING_DELETE'")
    suspend fun getAllBySpaceOnce(spaceId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): TransactionEntity?

    @Query("UPDATE transactions SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSyncedByIds(ids: List<Long>)

    @Query("UPDATE transactions SET syncStatus = 'SYNCED' WHERE syncStatus = 'PENDING_UPLOAD'")
    suspend fun markAllUploadsSynced()

    @Query("DELETE FROM transactions WHERE syncStatus = 'PENDING_DELETE'")
    suspend fun purgePendingDeletes()
}

@Dao
interface MealSavingDao {
    @Query(
        """
        SELECT * FROM meal_saving_logs 
        WHERE spaceId = :spaceId 
          AND occurredAt >= :startMillis 
          AND occurredAt < :endExclusiveMillis
        ORDER BY occurredAt DESC, id DESC
        """
    )
    fun observeMealLogsInPeriod(
        spaceId: String,
        startMillis: Long,
        endExclusiveMillis: Long
    ): Flow<List<MealSavingLogEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(count), 0) AS totalCount, COALESCE(SUM(savedAmount), 0) AS totalSaved
        FROM meal_saving_logs
        WHERE spaceId = :spaceId
          AND occurredAt >= :startMillis
          AND occurredAt < :endExclusiveMillis
        """
    )
    suspend fun sumMealsInPeriod(spaceId: String, startMillis: Long, endExclusiveMillis: Long): MealPeriodTotal

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealLog(log: MealSavingLogEntity): Long

    @Query("DELETE FROM meal_saving_logs WHERE id = :id")
    suspend fun deleteMealLog(id: Long)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM ignored_keywords ORDER BY createdAt DESC")
    fun observeIgnoredKeywords(): Flow<List<IgnoredKeywordEntity>>

    @Query("SELECT * FROM ignored_keywords")
    suspend fun getIgnoredKeywordsOnce(): List<IgnoredKeywordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIgnoredKeyword(entity: IgnoredKeywordEntity)

    @Query("DELETE FROM ignored_keywords WHERE id = :id")
    suspend fun deleteIgnoredKeyword(id: Long)

    @Query("SELECT * FROM merchant_category_rules")
    suspend fun getAllMerchantRules(): List<MerchantCategoryRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMerchantRule(rule: MerchantCategoryRuleEntity)
}
