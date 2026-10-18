package com.budget.householdbook.domain.usecase

import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.domain.model.QueueKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedAndSampleDataUseCasesTest {

    private val settings = FakeSettingsRepository()
    private val spaces = FakeLedgerSpaceRepository()
    private val categories = FakeCategoryRepository()
    private val rules = FakeAutomationRuleRepository()
    private val transactions = FakeTransactionRepository()
    private val meals = FakeMealRepository()
    private val pending = FakePendingQueueRepository()
    private val runner = ImmediateTransactionRunner()

    private val seed = SeedInitialDataUseCase(runner, settings, spaces, categories, rules)
    private val insertSample = InsertSampleDataUseCase(runner, categories, transactions, meals, pending)

    @Test
    fun `첫 실행 시드는 공간과 카테고리만 만들고 예시 거래·집밥·대기 내역은 넣지 않는다`() = runTest {
        seed()

        assertEquals(
            setOf(AppDatabase.SPACE_ID_SHARED, AppDatabase.SPACE_ID_PERSONAL),
            spaces.state.value.map { it.id }.toSet()
        )
        assertTrue(categories.state.value.isNotEmpty())
        assertTrue(transactions.state.value.isEmpty())
        assertTrue(meals.state.value.isEmpty())
        assertTrue(pending.state.value.isEmpty())
    }

    @Test
    fun `예시 데이터는 한 번만 추가되고 취소 예시는 쿠팡 결제와 연결된다`() = runTest {
        seed()

        assertEquals(SampleDataResult.INSERTED, insertSample())
        val txCount = transactions.state.value.size
        val pendingCount = pending.state.value.size
        assertEquals(4, txCount)
        assertEquals(2, meals.state.value.size)
        assertEquals(3, pendingCount)

        val coupang = transactions.state.value.first { it.merchantOrTitle.startsWith("쿠팡") }
        val cancel = pending.state.value.first { it.queueKind == QueueKind.CANCEL_MATCHED }
        assertEquals(coupang.id, cancel.matchedTransactionId)

        assertEquals(SampleDataResult.ALREADY_INSERTED, insertSample())
        assertEquals(txCount, transactions.state.value.size)
        assertEquals(pendingCount, pending.state.value.size)
    }
}
