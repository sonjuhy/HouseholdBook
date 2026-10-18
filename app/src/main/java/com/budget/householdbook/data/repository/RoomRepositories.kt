package com.budget.householdbook.data.repository

import androidx.room.withTransaction
import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.data.local.dao.CategoryDao
import com.budget.householdbook.data.local.dao.LedgerSpaceDao
import com.budget.householdbook.data.local.dao.MealSavingDao
import com.budget.householdbook.data.local.dao.PendingTransactionDao
import com.budget.householdbook.data.local.dao.RuleDao
import com.budget.householdbook.data.local.dao.TransactionDao
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.data.local.entity.IgnoredKeywordEntity
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.data.local.entity.MealSavingLogEntity
import com.budget.householdbook.data.local.entity.MerchantCategoryRuleEntity
import com.budget.householdbook.data.local.entity.PendingTransactionEntity
import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.domain.model.MealPeriodTotal
import com.budget.householdbook.domain.model.QueueStatus
import com.budget.householdbook.domain.repository.AutomationRuleRepository
import com.budget.householdbook.domain.repository.CategoryRepository
import com.budget.householdbook.domain.repository.LedgerSpaceRepository
import com.budget.householdbook.domain.repository.MealRepository
import com.budget.householdbook.domain.repository.PendingQueueRepository
import com.budget.householdbook.domain.repository.TransactionRepository
import com.budget.householdbook.domain.repository.TransactionRunner
import kotlinx.coroutines.flow.Flow

/*
 * Room(SSOT) 기반 도메인 저장소 구현체. 비즈니스 규칙은 domain/usecase에 있고 여기서는 DAO만 위임합니다.
 */

class RoomTransactionRunner(private val database: AppDatabase) : TransactionRunner {
    override suspend fun <R> runInTransaction(block: suspend () -> R): R = database.withTransaction { block() }
}

class RoomLedgerSpaceRepository(private val dao: LedgerSpaceDao) : LedgerSpaceRepository {
    override fun observeAllSpaces(): Flow<List<LedgerSpaceEntity>> = dao.observeAllSpaces()
    override suspend fun getSpaceById(spaceId: String): LedgerSpaceEntity? = dao.getSpaceById(spaceId)
    override suspend fun insertSpacesIfEmpty(spaces: List<LedgerSpaceEntity>) = dao.insertSpacesIfEmpty(spaces)
    override suspend fun updateBudgetAndCycle(spaceId: String, targetAmount: Long, billingStartDay: Int) =
        dao.updateBudgetAndCycle(spaceId, targetAmount, billingStartDay)
    override suspend fun updateShareConfig(spaceId: String, memberNamesCsv: String, inviteCode: String?, isShared: Boolean) =
        dao.updateShareConfig(spaceId, memberNamesCsv, inviteCode, isShared)
}

class RoomCategoryRepository(private val dao: CategoryDao) : CategoryRepository {
    override fun observeAllActiveCategories(): Flow<List<CategoryEntity>> = dao.observeAllActiveCategories()
    override suspend fun getCategoriesBySpaceOnce(spaceId: String): List<CategoryEntity> = dao.getCategoriesBySpaceOnce(spaceId)
    override suspend fun insertCategory(category: CategoryEntity): Long = dao.insertCategory(category)
    override suspend fun updateCategory(category: CategoryEntity) = dao.updateCategory(category)
    override suspend fun archiveCategoryAndSubs(categoryId: Long) = dao.archiveCategoryAndSubs(categoryId)
}

class RoomPendingQueueRepository(private val dao: PendingTransactionDao) : PendingQueueRepository {
    override fun observePendingQueue(): Flow<List<PendingTransactionEntity>> = dao.observePendingQueue()
    override fun observePendingCount(): Flow<Int> = dao.observePendingCount()
    override suspend fun getById(id: Long): PendingTransactionEntity? = dao.getById(id)
    override suspend fun getRecentPendingSince(sinceMillis: Long): List<PendingTransactionEntity> =
        dao.getRecentPendingSince(sinceMillis)
    override suspend fun insertIgnoreDuplicate(item: PendingTransactionEntity): Long = dao.insertIgnoreDuplicate(item)
    override suspend fun updateStatus(id: Long, status: QueueStatus) = dao.updateStatus(id, status)
    override suspend fun markCancelLinkedAndApproved(id: Long, transactionId: Long) =
        dao.markCancelLinkedAndApproved(id, transactionId)
    override suspend fun markAsTransferPair(id: Long, partnerId: Long) = dao.markAsTransferPair(id, partnerId)
}

class RoomTransactionRepository(private val dao: TransactionDao) : TransactionRepository {
    override fun observeTransactionsInPeriod(
        spaceId: String,
        startMillis: Long,
        endExclusiveMillis: Long
    ): Flow<List<TransactionEntity>> = dao.observeTransactionsInPeriod(spaceId, startMillis, endExclusiveMillis)
    override suspend fun getById(transactionId: Long): TransactionEntity? = dao.getById(transactionId)
    override suspend fun getByRemoteId(remoteId: String): TransactionEntity? = dao.getByRemoteId(remoteId)
    override suspend fun findCancelCandidatesByAmount(amount: Long, sinceMillis: Long): List<TransactionEntity> =
        dao.findCancelCandidatesByAmount(amount, sinceMillis)
    override suspend fun findManualCancelCandidates(refundAmount: Long, sinceMillis: Long): List<TransactionEntity> =
        dao.findManualCancelCandidates(refundAmount, sinceMillis)
    override suspend fun insertTransaction(transaction: TransactionEntity): Long = dao.insertTransaction(transaction)
    override suspend fun insertTransactions(transactions: List<TransactionEntity>) = dao.insertTransactions(transactions)
    override suspend fun markTransactionCanceled(transactionId: Long) = dao.markTransactionCanceled(transactionId)
    override suspend fun applyPartialCancel(transactionId: Long, newAmount: Long, memo: String) =
        dao.applyPartialCancel(transactionId = transactionId, newAmount = newAmount, memo = memo)
    override suspend fun markPendingDelete(transactionId: Long) = dao.markPendingDelete(transactionId)
    override suspend fun deleteById(transactionId: Long) = dao.deleteById(transactionId)
    override suspend fun countUnsyncedInSpace(spaceId: String): Int = dao.countUnsyncedInSpace(spaceId)
}

class RoomMealRepository(private val dao: MealSavingDao) : MealRepository {
    override fun observeMealLogsInPeriod(
        spaceId: String,
        startMillis: Long,
        endExclusiveMillis: Long
    ): Flow<List<MealSavingLogEntity>> = dao.observeMealLogsInPeriod(spaceId, startMillis, endExclusiveMillis)
    override suspend fun sumMealsInPeriod(spaceId: String, startMillis: Long, endExclusiveMillis: Long): MealPeriodTotal =
        dao.sumMealsInPeriod(spaceId, startMillis, endExclusiveMillis)
    override suspend fun insertMealLog(log: MealSavingLogEntity): Long = dao.insertMealLog(log)
    override suspend fun deleteMealLog(id: Long) = dao.deleteMealLog(id)
}

class RoomAutomationRuleRepository(private val dao: RuleDao) : AutomationRuleRepository {
    override fun observeIgnoredKeywords(): Flow<List<IgnoredKeywordEntity>> = dao.observeIgnoredKeywords()
    override suspend fun getIgnoredKeywordsOnce(): List<IgnoredKeywordEntity> = dao.getIgnoredKeywordsOnce()
    override suspend fun insertIgnoredKeyword(entity: IgnoredKeywordEntity) = dao.insertIgnoredKeyword(entity)
    override suspend fun deleteIgnoredKeyword(id: Long) = dao.deleteIgnoredKeyword(id)
    override suspend fun getAllMerchantRules(): List<MerchantCategoryRuleEntity> = dao.getAllMerchantRules()
    override suspend fun upsertMerchantRule(rule: MerchantCategoryRuleEntity) = dao.upsertMerchantRule(rule)
}
