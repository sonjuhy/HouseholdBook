package com.budget.householdbook.domain.usecase

import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.core.datastore.AppUserSettings
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.domain.model.SpaceType
import com.budget.householdbook.domain.model.SyncStatus
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.domain.model.UserTier
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 등록된 거래 수정: 이름·금액·날짜·분류·메모를 바꾸고, 함께 쓰는 장부면 공유 장부에도 반영
 */
class UpdateTransactionUseCaseTest {

    private val shared = AppDatabase.SPACE_ID_SHARED
    private val personal = AppDatabase.SPACE_ID_PERSONAL

    private fun tx(id: Long, spaceId: String) = TransactionEntity(
        id = id, remoteId = "r$id", spaceId = spaceId, type = TransactionType.EXPENSE,
        amount = 18_900, categoryId = 1, merchantOrTitle = "쿠팡", memo = "",
        authorName = "엄마", transactionDate = 1_000L, updatedAt = 1L
    )

    private fun setUp(sharedActive: Boolean): Triple<FakeTransactionRepository, FakeSharedSyncRepository, UpdateTransactionUseCase> {
        val settings = FakeSettingsRepository(AppUserSettings(userTier = if (sharedActive) UserTier.PRO else UserTier.FREE))
        val spaces = FakeLedgerSpaceRepository(
            listOf(
                LedgerSpaceEntity(id = shared, name = "공통", type = SpaceType.SHARED, isRemoteShared = sharedActive),
                LedgerSpaceEntity(id = personal, name = "용돈", type = SpaceType.PERSONAL)
            )
        )
        val transactions = FakeTransactionRepository(listOf(tx(1, shared), tx(2, personal)))
        val sync = FakeSharedSyncRepository()
        return Triple(transactions, sync, UpdateTransactionUseCase(transactions, SyncSharedChangesUseCase(settings, spaces, sync)))
    }

    @Test
    fun `이름·금액·날짜·분류·메모를 바꾸고 작성자와 공간은 그대로 둔다`() = runTest {
        val (transactions, sync, update) = setUp(sharedActive = false)

        val saved = update(
            transactionId = 2, merchantOrTitle = "  쿠팡 로켓배송 ", amount = 21_000, transactionDate = 5_000L,
            categoryId = 9, subCategoryId = 10, memo = " 생필품 ", nowMillis = 9_999L
        )

        val tx = transactions.state.value.first { it.id == 2L }
        assertEquals(saved, tx)
        assertEquals("쿠팡 로켓배송", tx.merchantOrTitle)
        assertEquals(21_000L, tx.amount)
        assertEquals(5_000L, tx.transactionDate)
        assertEquals(9L, tx.categoryId)
        assertEquals(10L, tx.subCategoryId)
        assertEquals("생필품", tx.memo)
        assertEquals("엄마", tx.authorName)
        assertEquals(personal, tx.spaceId)
        assertEquals(9_999L, tx.updatedAt)
        // 개인 용돈은 공유 장부로 올리지 않음
        assertEquals(SyncStatus.SYNCED, tx.syncStatus)
        assertTrue(sync.syncCalls.isEmpty())
    }

    @Test
    fun `함께 쓰는 공통 생활비를 고치면 업로드 대기로 두고 동기화한다`() = runTest {
        val (transactions, sync, update) = setUp(sharedActive = true)

        update(1, "쿠팡", 20_000, 1_000L, 1, null, "", nowMillis = 9_999L)

        val tx = transactions.state.value.first { it.id == 1L }
        assertEquals(20_000L, tx.amount)
        assertEquals(SyncStatus.PENDING_UPLOAD, tx.syncStatus)
        assertEquals(9_999L, tx.updatedAt)
        assertFalse(sync.syncCalls.isEmpty())
    }

    @Test
    fun `바뀐 내용이 없거나 없는 거래면 아무것도 저장하지 않는다`() = runTest {
        val (transactions, sync, update) = setUp(sharedActive = true)

        assertNull(update(1, "쿠팡", 18_900, 1_000L, 1, null, ""))
        assertNull(update(999, "쿠팡", 18_900, 1_000L, 1, null, ""))
        assertEquals(1L, transactions.state.value.first { it.id == 1L }.updatedAt)
        assertTrue(sync.syncCalls.isEmpty())
    }

    @Test
    fun `금액이 0원이거나 이름이 비어 있으면 거절한다`() = runTest {
        val (transactions, _, update) = setUp(sharedActive = false)

        assertTrue(runCatching { update(2, "쿠팡", 0, 1_000L, 1, null, "") }.isFailure)
        assertTrue(runCatching { update(2, "   ", 18_900, 1_000L, 1, null, "") }.isFailure)
        assertEquals(18_900L, transactions.state.value.first { it.id == 2L }.amount)
    }
}
