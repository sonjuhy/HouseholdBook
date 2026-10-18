package com.budget.householdbook.domain.usecase

import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.core.datastore.AppUserSettings
import com.budget.householdbook.data.ingestion.parser.ParsedNotificationDraft
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.data.local.entity.IgnoredKeywordEntity
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.data.local.entity.MerchantCategoryRuleEntity
import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.domain.model.IngestionSource
import com.budget.householdbook.domain.model.QueueKind
import com.budget.householdbook.domain.model.QueueStatus
import com.budget.householdbook.domain.model.SpaceType
import com.budget.householdbook.domain.model.SplitApprovalItem
import com.budget.householdbook.domain.model.SyncStatus
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.domain.model.UserTier
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 대기 큐 적재·승인·분할·취소 연결 UseCase 검증 (메모리 저장소 사용)
 */
class PendingQueueUseCasesTest {

    private val shared = AppDatabase.SPACE_ID_SHARED
    private val personal = AppDatabase.SPACE_ID_PERSONAL
    private val now = 1_800_000_000_000L

    private val settings = FakeSettingsRepository(AppUserSettings(currentSpaceId = shared, userRealName = "홍길동"))
    private val spaces = FakeLedgerSpaceRepository(
        listOf(
            LedgerSpaceEntity(id = shared, name = "공통", type = SpaceType.SHARED, isRemoteShared = false),
            LedgerSpaceEntity(id = personal, name = "용돈", type = SpaceType.PERSONAL)
        )
    )
    private val categories = FakeCategoryRepository(
        listOf(
            CategoryEntity(id = 1, spaceId = shared, name = "식비"),
            CategoryEntity(id = 2, spaceId = shared, parentId = 1, name = "식재료"),
            CategoryEntity(id = 3, spaceId = shared, transactionType = TransactionType.INCOME, name = "생활비 입금", sortOrder = 10),
            CategoryEntity(id = 10, spaceId = personal, name = "취미")
        )
    )
    private val rules = FakeAutomationRuleRepository()
    private val transactions = FakeTransactionRepository()
    private val pending = FakePendingQueueRepository()
    private val sync = FakeSharedSyncRepository()
    private val runner = ImmediateTransactionRunner()

    private val enqueue = EnqueueParsedDraftUseCase(settings, rules, categories, transactions, pending)
    private val syncShared = SyncSharedChangesUseCase(settings, spaces, sync)
    private val approve = ApprovePendingItemUseCase(runner, pending, transactions, spaces, rules, settings, sync)
    private val applyMatchedCancel = ApplyMatchedCancelUseCase(runner, pending, transactions, syncShared)
    private val approveAll = ApproveAllPendingItemsUseCase(pending, categories, approve, applyMatchedCancel)
    private val approveSplit = ApproveSplitPendingItemUseCase(runner, pending, transactions, spaces, settings, sync)
    private val linkCancel = LinkCancelToTransactionUseCase(runner, pending, transactions, syncShared)
    private val dismiss = DismissPendingItemUseCase(runner, pending, rules)

    private fun draft(
        amount: Long,
        merchant: String,
        type: TransactionType = TransactionType.EXPENSE,
        app: String = "KB Pay",
        isCancel: Boolean = false,
        occurredAt: Long = now,
        raw: String = "$app $merchant $amount"
    ) = ParsedNotificationDraft(
        sourceType = IngestionSource.APP_NOTIFICATION,
        sourceAppName = app,
        rawText = raw,
        amount = amount,
        transactionType = type,
        merchant = merchant,
        isCancelAlert = isCancel,
        occurredAt = occurredAt,
        dedupHash = "$app|$merchant|$amount|$occurredAt|$isCancel"
    )

    private fun expense(id: Long, amount: Long, merchant: String, date: Long = now - 86_400_000L, memo: String = "") =
        TransactionEntity(
            id = id,
            remoteId = "r$id",
            spaceId = shared,
            type = TransactionType.EXPENSE,
            amount = amount,
            categoryId = 1,
            merchantOrTitle = merchant,
            memo = memo,
            transactionDate = date
        )

    @Test
    fun `무시 키워드가 포함된 알림은 대기 큐에 넣지 않는다`() = runTest {
        rules.ignoredState.value = listOf(IgnoredKeywordEntity(id = 1, keyword = "광고"))

        val id = enqueue(draft(10_000, "가맹점", raw = "(광고) 10,000원 혜택"))

        assertNull(id)
        assertTrue(pending.state.value.isEmpty())
    }

    @Test
    fun `학습된 가맹점 규칙이 있으면 해당 공간과 카테고리를 추천한다`() = runTest {
        rules.merchantRuleState.value = listOf(
            MerchantCategoryRuleEntity(merchantKeyword = "스타벅스", targetSpaceId = personal, categoryId = 10, subCategoryId = null)
        )

        val id = enqueue(draft(5_800, "스타벅스코리아"))!!
        val item = pending.getById(id)!!

        assertEquals(personal, item.suggestedSpaceId)
        assertEquals(10L, item.suggestedCategoryId)
        assertEquals(QueueKind.NORMAL, item.queueKind)
    }

    @Test
    fun `규칙이 없으면 현재 공간의 첫 상위 카테고리와 첫 세부 카테고리를 추천한다`() = runTest {
        val item = pending.getById(enqueue(draft(12_000, "동네마트"))!!)!!

        assertEquals(shared, item.suggestedSpaceId)
        assertEquals(1L, item.suggestedCategoryId)
        assertEquals(2L, item.suggestedSubCategoryId)
    }

    @Test
    fun `취소 알림은 비슷한 가맹점 원본과 매칭되고, 못 찾으면 이체 제외로 적재된다`() = runTest {
        transactions.state.value = listOf(expense(7, 18_900, "쿠팡(로켓배송)"))

        val matched = pending.getById(enqueue(draft(18_900, "쿠팡(로켓배송)", isCancel = true))!!)!!
        assertEquals(QueueKind.CANCEL_MATCHED, matched.queueKind)
        assertEquals(7L, matched.matchedTransactionId)
        assertEquals(TransactionType.EXPENSE, matched.parsedType)

        val unmatched = pending.getById(enqueue(draft(18_900, "다른가게", isCancel = true))!!)!!
        assertEquals(QueueKind.CANCEL_UNMATCHED, unmatched.queueKind)
        assertNull(unmatched.matchedTransactionId)
        assertEquals(TransactionType.TRANSFER, unmatched.parsedType)
    }

    @Test
    fun `3분 이내 다른 앱의 같은 금액 출금과 입금은 이체 쌍으로 묶인다`() = runTest {
        val outId = enqueue(draft(200_000, "출금", app = "토스", occurredAt = now))!!
        val inId = enqueue(draft(200_000, "입금", type = TransactionType.INCOME, app = "KB스타뱅킹", occurredAt = now + 60_000))!!

        val outItem = pending.getById(outId)!!
        val inItem = pending.getById(inId)!!
        assertEquals(QueueKind.TRANSFER_PAIR, outItem.queueKind)
        assertEquals(inId, outItem.pairedQueueId)
        assertEquals(TransactionType.TRANSFER, outItem.parsedType)
        assertEquals(QueueKind.TRANSFER_PAIR, inItem.queueKind)
        assertEquals(outId, inItem.pairedQueueId)
        assertEquals(TransactionType.TRANSFER, inItem.parsedType)

        // 3분이 지나면 묶지 않음
        val lateId = enqueue(draft(200_000, "입금", type = TransactionType.INCOME, app = "카카오뱅크", occurredAt = now + 600_000))!!
        assertEquals(QueueKind.NORMAL, pending.getById(lateId)!!.queueKind)
    }

    @Test
    fun `같은 dedupHash는 한 번만 적재된다`() = runTest {
        val d = draft(9_000, "편의점")
        assertTrue(enqueue(d) != null)
        assertNull(enqueue(d))
        assertEquals(1, pending.state.value.size)
    }

    @Test
    fun `일괄 승인은 매칭된 취소를 반영하고 이체 쌍은 한 번만 저장하며 미매칭 취소는 남긴다`() = runTest {
        transactions.state.value = listOf(expense(7, 18_900, "쿠팡(로켓배송)"))
        enqueue(draft(52_000, "이마트", occurredAt = now - 3_600_000))
        enqueue(draft(18_900, "쿠팡(로켓배송)", isCancel = true, occurredAt = now - 1_800_000))
        enqueue(draft(5_000, "모르는가게", isCancel = true, occurredAt = now - 1_000_000))
        enqueue(draft(100_000, "출금", app = "토스", occurredAt = now))
        enqueue(draft(100_000, "입금", type = TransactionType.INCOME, app = "KB스타뱅킹", occurredAt = now + 30_000))

        val result = approveAll()

        // 이마트 1 + 취소 반영 1 + 이체 쌍 1(상대 항목은 함께 승인)
        assertEquals(3, result.processedCount)
        assertEquals(1, result.remainingCount)
        val remaining = pending.state.value.single { it.status == QueueStatus.PENDING }
        assertEquals(QueueKind.CANCEL_UNMATCHED, remaining.queueKind)

        assertTrue(transactions.getById(7)!!.isCanceled)
        val saved = transactions.state.value.filter { it.id != 7L }
        assertEquals(listOf(52_000L, 100_000L), saved.map { it.amount }.sorted())
        assertEquals(TransactionType.TRANSFER, saved.single { it.amount == 100_000L }.type)
    }

    @Test
    fun `단건 승인은 가맹점 규칙을 학습하고 이미 처리된 항목은 다시 저장하지 않는다`() = runTest {
        val id = enqueue(draft(23_500, "배달의민족"))!!

        repeat(2) {
            approve(id, personal, TransactionType.EXPENSE, 23_500, " 배달의민족 ", "", 10, null)
        }

        assertEquals(1, transactions.state.value.size)
        val tx = transactions.state.value.single()
        assertEquals("배달의민족", tx.merchantOrTitle)
        assertEquals("홍길동", tx.authorName)
        assertEquals(id, tx.originQueueId)
        assertEquals(SyncStatus.SYNCED, tx.syncStatus)
        assertEquals(personal, rules.merchantRuleState.value.single { it.merchantKeyword == "배달의민족" }.targetSpaceId)
    }

    @Test
    fun `분할 승인은 합계가 다르면 거부하고, 공유 중인 공통 생활비 분할분만 업로드 대기로 둔다`() = runTest {
        settings.setUserTier(UserTier.PRO)
        spaces.updateShareConfig(shared, "나, 짝꿍", "HB-ABCD-EFGH", true)
        val id = enqueue(draft(50_000, "마트"))!!

        try {
            approveSplit(id, "마트", listOf(SplitApprovalItem(shared, 1, null, 40_000), SplitApprovalItem(personal, 10, null, 9_000)))
            fail("합계가 다른 분할은 거부되어야 함")
        } catch (expected: IllegalArgumentException) {
        }
        assertEquals(QueueStatus.PENDING, pending.getById(id)!!.status)

        approveSplit(id, "마트", listOf(SplitApprovalItem(shared, 1, null, 40_000), SplitApprovalItem(personal, 10, null, 10_000)))

        val splits = transactions.state.value
        assertEquals(2, splits.size)
        assertTrue(splits.all { it.originQueueId == id })
        assertEquals(SyncStatus.PENDING_UPLOAD, splits.single { it.spaceId == shared }.syncStatus)
        assertEquals(SyncStatus.SYNCED, splits.single { it.spaceId == personal }.syncStatus)
        assertEquals("마트 (분할 1)", splits.single { it.spaceId == shared }.merchantOrTitle)
        assertEquals(QueueStatus.APPROVED, pending.getById(id)!!.status)
    }

    @Test
    fun `직접 고른 원본에 취소를 연결하면 같은 금액은 전체 취소, 큰 원본은 부분 취소된다`() = runTest {
        transactions.state.value = listOf(
            expense(1, 30_000, "A가게"),
            expense(2, 52_000, "B가게", memo = "장보기")
        )
        val fullId = enqueue(draft(30_000, "환불", isCancel = true))!!
        val partialId = enqueue(draft(33_000, "환불2", isCancel = true))!!

        assertTrue(linkCancel(fullId, 1).isSuccess)
        assertTrue(linkCancel(partialId, 2).isSuccess)

        assertTrue(transactions.getById(1)!!.isCanceled)
        val partial = transactions.getById(2)!!
        assertFalse(partial.isCanceled)
        assertEquals(19_000L, partial.amount)
        assertEquals("장보기 · 부분 취소 33,000원", partial.memo)
        assertEquals(2L, pending.getById(partialId)!!.matchedTransactionId)
        assertEquals(QueueStatus.APPROVED, pending.getById(partialId)!!.status)

        // 이미 처리된 항목 / 환불이 더 큰 경우는 실패
        assertTrue(linkCancel(partialId, 2).isFailure)
        val tooBigId = enqueue(draft(99_000, "환불3", isCancel = true))!!
        assertTrue(linkCancel(tooBigId, 2).isFailure)
    }

    @Test
    fun `삭제 시 키워드를 지정하면 무시 규칙으로 저장된다`() = runTest {
        val id = enqueue(draft(1_000, "포인트"))!!

        dismiss(id, "  포인트  ")

        assertEquals(QueueStatus.DISMISSED, pending.getById(id)!!.status)
        assertEquals("포인트", rules.ignoredState.value.single().keyword)
        assertNull(enqueue(draft(2_000, "포인트 적립")))
    }

    @Test
    fun `대기 큐 항목은 확정 거래로 직접 들어가지 않는다`() = runTest {
        enqueue(draft(15_000, "식당"))
        assertTrue(transactions.state.value.isEmpty())
        assertEquals(1, pending.state.value.count { it.status == QueueStatus.PENDING })
    }
}
