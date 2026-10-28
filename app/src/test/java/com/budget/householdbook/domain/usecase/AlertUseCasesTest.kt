package com.budget.householdbook.domain.usecase

import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.core.datastore.AppUserSettings
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.data.local.entity.MerchantCategoryRuleEntity
import com.budget.householdbook.data.local.entity.PendingTransactionEntity
import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.domain.model.BudgetAlertLevel
import com.budget.householdbook.domain.model.CollectionStopReason
import com.budget.householdbook.domain.model.IngestionSource
import com.budget.householdbook.domain.model.QueueKind
import com.budget.householdbook.domain.model.QueueStatus
import com.budget.householdbook.domain.model.SpaceType
import com.budget.householdbook.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 1차 알림: 새 결제 대기(알림에서 바로 저장) · 예산 80%/100% · 자동 기록 멈춤
 */
class AlertUseCasesTest {

    private val shared = AppDatabase.SPACE_ID_SHARED
    private val personal = AppDatabase.SPACE_ID_PERSONAL

    private val settings = FakeSettingsRepository(AppUserSettings(currentSpaceId = shared))
    private val spaces = FakeLedgerSpaceRepository(
        listOf(
            LedgerSpaceEntity(id = shared, name = "공통 생활비", type = SpaceType.SHARED, billingStartDay = 1, targetBudgetAmount = 100_000),
            LedgerSpaceEntity(id = personal, name = "내 용돈", type = SpaceType.PERSONAL, billingStartDay = 1, targetBudgetAmount = 0)
        )
    )
    private val categories = FakeCategoryRepository(
        listOf(
            CategoryEntity(id = 1, spaceId = shared, name = "식비"),
            CategoryEntity(id = 2, spaceId = shared, parentId = 1, name = "식재료"),
            CategoryEntity(id = 10, spaceId = personal, name = "식음료/간식"),
            CategoryEntity(id = 11, spaceId = personal, parentId = 10, name = "커피"),
            CategoryEntity(id = 20, spaceId = personal, name = "취미")
        )
    )
    private val rules = FakeAutomationRuleRepository()
    private val transactions = FakeTransactionRepository()
    private val pending = FakePendingQueueRepository()
    private val sync = FakeSharedSyncRepository()
    private val notifier = FakeAppNotifier()
    private val alertState = FakeAlertStateRepository()

    private val approve = ApprovePendingItemUseCase(ImmediateTransactionRunner(), pending, transactions, spaces, rules, settings, sync)
    private val quickSave = QuickSavePendingUseCase(pending, categories, rules, approve)

    private fun pendingItem(
        id: Long,
        merchant: String = "스타벅스",
        kind: QueueKind = QueueKind.NORMAL,
        type: TransactionType = TransactionType.EXPENSE
    ) = PendingTransactionEntity(
        id = id, sourceType = IngestionSource.APP_NOTIFICATION, sourceAppName = "KB Pay", rawText = merchant,
        parsedAmount = 5_800, parsedType = type, parsedMerchant = merchant, occurredAt = 1_000L, queueKind = kind,
        suggestedSpaceId = shared, suggestedCategoryId = 1, suggestedSubCategoryId = 2, dedupHash = "h$id"
    )

    // ---------------- 새 결제 대기 ----------------

    @Test
    fun `새 결제가 대기내역에 들어오면 알림을 보내고, 제외한 앱의 알림은 보내지 않는다`() = runTest {
        val enqueue = EnqueueParsedDraftUseCase(settings, rules, categories, transactions, pending)
        val ingest = IngestRawNotificationUseCase(settings, enqueue, pending, notifier)

        assertTrue(ingest(IngestionSource.APP_NOTIFICATION, "KB Pay", "결제", "KB국민카드 5,800원 승인 스타벅스", "com.kbcard.cxh.appcard"))
        val alert = notifier.payments.single()
        assertEquals(pending.state.value.single().id, alert.pendingId)
        assertEquals(5_800L, alert.amount)
        assertTrue(alert.canQuickSave)

        settings.setExcludedSources(setOf("com.kbstar.kbbank"))
        assertFalse(ingest(IngestionSource.APP_NOTIFICATION, "KB스타뱅킹", "출금", "출금 12,000원 GS25", "com.kbstar.kbbank"))
        assertEquals(1, notifier.payments.size)
    }

    @Test
    fun `결제 취소와 이체는 알림에서 바로 저장할 수 없다`() {
        assertTrue(pendingItem(1).toPaymentAlert().canQuickSave)
        assertFalse(pendingItem(2, kind = QueueKind.CANCEL_MATCHED).toPaymentAlert().canQuickSave)
        assertFalse(pendingItem(3, kind = QueueKind.CANCEL_UNMATCHED).toPaymentAlert().canQuickSave)
        assertFalse(pendingItem(4, kind = QueueKind.TRANSFER_PAIR, type = TransactionType.TRANSFER).toPaymentAlert().canQuickSave)
    }

    @Test
    fun `알림에서 공용에 저장하면 추천 분류로, 개인에 저장하면 그 공간의 분류로 장부에 저장한다`() = runTest {
        pending.state.value = listOf(pendingItem(1), pendingItem(2, merchant = "투썸플레이스"))

        assertTrue(quickSave(1, shared))
        assertTrue(quickSave(2, personal))

        val saved = transactions.state.value.associateBy { it.merchantOrTitle }
        assertEquals(shared, saved.getValue("스타벅스").spaceId)
        assertEquals(1L to 2L, saved.getValue("스타벅스").categoryId to saved.getValue("스타벅스").subCategoryId)
        assertEquals(personal, saved.getValue("투썸플레이스").spaceId)
        assertEquals(10L to 11L, saved.getValue("투썸플레이스").categoryId to saved.getValue("투썸플레이스").subCategoryId)
        assertTrue(pending.state.value.all { it.status == QueueStatus.APPROVED })

        // 이미 처리한 항목은 다시 저장하지 않음
        assertFalse(quickSave(1, personal))
        assertEquals(2, transactions.state.value.size)
    }

    @Test
    fun `개인에 저장할 때 그 공간에서 이 가게로 배운 분류가 있으면 그 분류를 쓴다`() = runTest {
        rules.upsertMerchantRule(MerchantCategoryRuleEntity(merchantKeyword = "스타벅스", targetSpaceId = personal, categoryId = 20, subCategoryId = null))
        pending.state.value = listOf(pendingItem(1))

        assertTrue(quickSave(1, personal))

        assertEquals(20L, transactions.state.value.single().categoryId)
    }

    @Test
    fun `취소 알림은 알림 버튼으로 저장하지 않는다`() = runTest {
        pending.state.value = listOf(pendingItem(1, kind = QueueKind.CANCEL_UNMATCHED))
        assertFalse(quickSave(1, shared))
        assertTrue(transactions.state.value.isEmpty())
    }

    // ---------------- 예산 80% · 100% ----------------

    private val today = LocalDate.of(2026, 10, 15)
    private fun expense(id: Long, amount: Long, date: LocalDate = today) = TransactionEntity(
        id = id, remoteId = "r$id", spaceId = shared, type = TransactionType.EXPENSE, amount = amount, categoryId = 1,
        merchantOrTitle = "가게", transactionDate = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 3_600_000
    )

    @Test
    fun `예산의 80퍼센트와 100퍼센트를 넘을 때 주기마다 한 번씩 알린다`() = runTest {
        val check = CheckBudgetAlertUseCase(spaces, transactions, alertState, notifier)

        transactions.state.value = listOf(expense(1, 70_000))
        check(shared, today)
        assertTrue(notifier.budgets.isEmpty())

        transactions.state.value += expense(2, 15_000)
        check(shared, today)
        check(shared, today)
        assertEquals(listOf(BudgetAlertLevel.WARNING), notifier.budgets.map { it.level })
        assertEquals(15_000L, notifier.budgets.last().remaining)

        transactions.state.value += expense(3, 20_000)
        check(shared, today)
        assertEquals(listOf(BudgetAlertLevel.WARNING, BudgetAlertLevel.EXCEEDED), notifier.budgets.map { it.level })

        // 다음 주기에는 다시 처음부터
        val nextMonth = LocalDate.of(2026, 11, 10)
        transactions.state.value += expense(4, 90_000, nextMonth)
        check(shared, nextMonth)
        assertEquals(BudgetAlertLevel.WARNING, notifier.budgets.last().level)
        assertEquals(3, notifier.budgets.size)
    }

    @Test
    fun `취소된 거래와 수입은 예산 사용액에 넣지 않고, 목표 예산이 없으면 알리지 않는다`() = runTest {
        val check = CheckBudgetAlertUseCase(spaces, transactions, alertState, notifier)
        transactions.state.value = listOf(
            expense(1, 90_000).copy(isCanceled = true),
            expense(2, 90_000).copy(type = TransactionType.INCOME),
            expense(3, 10_000)
        )
        check(shared, today)
        check(personal, today)
        assertTrue(notifier.budgets.isEmpty())
        assertEquals(BudgetAlertLevel.NONE, BudgetAlertLevel.of(spent = 99_999, target = 0))
    }

    // ---------------- 자동 기록 멈춤 ----------------

    @Test
    fun `알림 읽기를 쓰던 사람만, 꺼진 동안 한 번 알리고 다시 켜면 알림을 지운다`() = runTest {
        val check = CheckCollectionStatusUseCase(alertState, notifier)

        // 처음부터 안 쓰던 사람에게는 알리지 않음
        check(notificationAccessEnabled = false, notify = true)
        assertTrue(notifier.stopped.isEmpty())

        check(notificationAccessEnabled = true, notify = false)
        assertTrue(alertState.collection.notificationAccessEverEnabled)

        // 앱을 보고 있을 때(notify=false)는 기록만
        check(notificationAccessEnabled = false, notify = false)
        assertTrue(notifier.stopped.isEmpty())

        check(notificationAccessEnabled = false, notify = true)
        check(notificationAccessEnabled = false, notify = true)
        assertEquals(listOf(CollectionStopReason.NOTIFICATION_ACCESS_OFF), notifier.stopped)

        check(notificationAccessEnabled = true, notify = false)
        assertTrue(CollectionStopReason.NOTIFICATION_ACCESS_OFF in notifier.canceledStops)
        check(notificationAccessEnabled = false, notify = true)
        assertEquals(2, notifier.stopped.size)
    }

    @Test
    fun `은행 자동 불러오기가 연속 3회 실패하면 한 번 알리고, 성공하면 처음부터 센다`() = runTest {
        val record = RecordBankInquiryResultUseCase(alertState, notifier)

        repeat(2) { record(success = false) }
        assertTrue(notifier.stopped.isEmpty())
        record(success = false)
        record(success = false)
        assertEquals(listOf(CollectionStopReason.BANK_INQUIRY_FAILING), notifier.stopped)

        record(success = true)
        assertEquals(0, alertState.collection.bankInquiryFailures)
        assertTrue(CollectionStopReason.BANK_INQUIRY_FAILING in notifier.canceledStops)
        repeat(3) { record(success = false) }
        assertEquals(2, notifier.stopped.size)
    }
}
