package com.budget.householdbook.domain.usecase

import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.core.datastore.AppUserSettings
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.domain.model.SpaceType
import com.budget.householdbook.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreCanceledTransactionUseCaseTest {

    private val shared = AppDatabase.SPACE_ID_SHARED
    private val settings = FakeSettingsRepository(AppUserSettings())
    private val spaces = FakeLedgerSpaceRepository(listOf(LedgerSpaceEntity(id = shared, name = "공통", type = SpaceType.SHARED)))
    private val transactions = FakeTransactionRepository(
        listOf(
            TransactionEntity(
                id = 1, remoteId = "r1", spaceId = shared, type = TransactionType.EXPENSE,
                amount = 18_900, categoryId = 1, merchantOrTitle = "쿠팡", transactionDate = 0
            )
        )
    )
    private val syncShared = SyncSharedChangesUseCase(settings, spaces, FakeSharedSyncRepository())
    private val cancel = CancelTransactionUseCase(transactions, syncShared)
    private val restore = RestoreCanceledTransactionUseCase(transactions, syncShared)

    @Test
    fun `실수로 취소한 거래를 되돌리면 다시 예산 합계에 포함된다`() = runTest {
        cancel(1)
        assertTrue(transactions.state.value.single().isCanceled)

        restore(1)
        val tx = transactions.state.value.single()
        assertFalse(tx.isCanceled)
        assertEquals(18_900L, tx.amount)
    }

    @Test
    fun `취소되지 않은 거래나 없는 거래는 그대로 둔다`() = runTest {
        restore(1)
        restore(999)
        assertFalse(transactions.state.value.single().isCanceled)
    }
}
