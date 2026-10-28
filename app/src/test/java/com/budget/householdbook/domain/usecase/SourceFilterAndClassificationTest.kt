package com.budget.householdbook.domain.usecase

import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.core.datastore.AppUserSettings
import com.budget.householdbook.data.ingestion.parser.ParsedNotificationDraft
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.data.local.entity.MerchantCategoryRuleEntity
import com.budget.householdbook.domain.model.CategoryDefaults
import com.budget.householdbook.domain.model.IngestionSource
import com.budget.householdbook.domain.model.NotificationSources
import com.budget.householdbook.domain.model.SpaceType
import com.budget.householdbook.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 앱별 알림 제외 · 다른 앱 중복 알림 거르기 · 대기 항목 공간/분류 변경 검증
 */
class SourceFilterAndClassificationTest {

    private val shared = AppDatabase.SPACE_ID_SHARED
    private val personal = AppDatabase.SPACE_ID_PERSONAL
    private val now = 1_800_000_000_000L

    private val settings = FakeSettingsRepository(AppUserSettings(currentSpaceId = shared, userRealName = "홍길동"))
    private val spaces = FakeLedgerSpaceRepository(
        listOf(
            LedgerSpaceEntity(id = shared, name = "공통", type = SpaceType.SHARED),
            LedgerSpaceEntity(id = personal, name = "용돈", type = SpaceType.PERSONAL)
        )
    )
    private val categories = FakeCategoryRepository(
        listOf(
            CategoryEntity(id = 1, spaceId = shared, name = "식비"),
            CategoryEntity(id = 2, spaceId = shared, parentId = 1, name = "식재료"),
            CategoryEntity(id = 10, spaceId = personal, name = "식음료/간식"),
            CategoryEntity(id = 11, spaceId = personal, parentId = 10, name = "커피"),
            CategoryEntity(id = 12, spaceId = personal, transactionType = TransactionType.INCOME, name = "용돈수입", sortOrder = 10)
        )
    )
    private val rules = FakeAutomationRuleRepository()
    private val transactions = FakeTransactionRepository()
    private val pending = FakePendingQueueRepository()
    private val sync = FakeSharedSyncRepository()
    private val runner = ImmediateTransactionRunner()

    private val enqueue = EnqueueParsedDraftUseCase(settings, rules, categories, transactions, pending)
    private val notifier = FakeAppNotifier()
    private val ingest = IngestRawNotificationUseCase(settings, enqueue, pending, notifier)
    private val syncShared = SyncSharedChangesUseCase(settings, spaces, sync)
    private val approve = ApprovePendingItemUseCase(runner, pending, transactions, spaces, rules, settings, sync)
    private val applyMatchedCancel = ApplyMatchedCancelUseCase(runner, pending, transactions, syncShared)
    private val approveAll = ApproveAllPendingItemsUseCase(pending, categories, approve, applyMatchedCancel)
    private val updateClassification = UpdatePendingClassificationUseCase(pending, categories)

    private fun draft(
        amount: Long,
        merchant: String,
        app: String,
        type: TransactionType = TransactionType.EXPENSE,
        occurredAt: Long = now,
        isCancel: Boolean = false
    ) = ParsedNotificationDraft(
        sourceType = IngestionSource.APP_NOTIFICATION,
        sourceAppName = app,
        rawText = "$app $merchant $amount",
        amount = amount,
        transactionType = type,
        merchant = merchant,
        isCancelAlert = isCancel,
        occurredAt = occurredAt,
        dedupHash = "$app|$merchant|$amount|$occurredAt"
    )

    // ---- 다른 앱에서 온 같은 결제 알림 ----

    @Test
    fun `카드 앱과 은행 앱이 같은 결제를 알리면 처음 것만 남긴다`() = runTest {
        assertNotNull(enqueue(draft(5_800, "스타벅스코리아", app = "KB Pay")))
        assertNull(enqueue(draft(5_800, "스타벅스", app = "KB스타뱅킹", occurredAt = now + 40_000)))
        assertEquals(1, pending.state.value.size)
    }

    @Test
    fun `가맹점을 알 수 없는 은행 출금 알림도 같은 금액이면 중복으로 본다`() = runTest {
        enqueue(draft(12_000, "GS25역삼점", app = "신한카드"))
        assertNull(enqueue(draft(12_000, "신한은행 내역", app = "신한 슈퍼SOL", occurredAt = now + 10_000)))
    }

    @Test
    fun `같은 앱에서 연달아 온 같은 금액은 실제 두 번 결제일 수 있어 둘 다 남긴다`() = runTest {
        enqueue(draft(4_500, "메가커피", app = "KB Pay"))
        assertNotNull(enqueue(draft(4_500, "메가커피", app = "KB Pay", occurredAt = now + 20_000)))
        assertEquals(2, pending.state.value.size)
    }

    @Test
    fun `3분이 지났거나 금액·가맹점·방향이 다르면 중복이 아니다`() = runTest {
        enqueue(draft(9_000, "올리브영", app = "KB Pay"))
        assertNotNull(enqueue(draft(9_000, "올리브영", app = "KB스타뱅킹", occurredAt = now + 4 * 60_000)))
        assertNotNull(enqueue(draft(9_100, "올리브영", app = "토스", occurredAt = now + 4 * 60_000 + 1)))
        assertNotNull(enqueue(draft(9_000, "다이소", app = "현대카드", occurredAt = now + 4 * 60_000 + 2)))
        assertNotNull(enqueue(draft(9_000, "올리브영", app = "토스", type = TransactionType.INCOME, occurredAt = now + 4 * 60_000 + 3)))
    }

    @Test
    fun `중복 거르기를 끄면 다른 앱 알림도 모두 들어온다`() = runTest {
        settings.setAutoFilterDuplicateAlerts(false)
        enqueue(draft(5_800, "스타벅스", app = "KB Pay"))
        assertNotNull(enqueue(draft(5_800, "스타벅스", app = "KB스타뱅킹", occurredAt = now + 1_000)))
    }

    // ---- 앱별 알림 제외 ----

    @Test
    fun `제외한 앱과 문자는 건너뛰지만 목록에는 남는다`() = runTest {
        settings.setExcludedSources(setOf("com.kbstar.kbbank", NotificationSources.SMS_SOURCE_ID))

        assertFalse(ingest(IngestionSource.APP_NOTIFICATION, "KB스타뱅킹", "출금", "출금 5,800원 스타벅스", "com.kbstar.kbbank"))
        assertFalse(ingest(IngestionSource.SMS, "문자(1588)", "", "[Web발신] KB국민카드 5,800원 승인 스타벅스"))
        assertTrue(ingest(IngestionSource.APP_NOTIFICATION, "KB Pay", "결제", "KB국민카드 5,800원 승인 스타벅스", "com.kbcard.cxh.appcard"))

        assertEquals(1, pending.state.value.size)
        assertEquals(
            setOf("com.kbstar.kbbank", NotificationSources.SMS_SOURCE_ID, "com.kbcard.cxh.appcard"),
            settings.state.value.seenSources.keys
        )
        assertEquals(NotificationSources.SMS_SOURCE_LABEL, settings.state.value.seenSources[NotificationSources.SMS_SOURCE_ID])
    }

    // ---- 기본 저장 공간 ----

    @Test
    fun `새 알림은 가맹점 규칙 다음으로 설정한 기본 공간에 담긴다`() = runTest {
        settings.setDefaultIngestSpaceId(personal)
        enqueue(draft(3_000, "편의점", app = "KB Pay"))
        assertEquals(personal, pending.state.value.single().suggestedSpaceId)

        rules.upsertMerchantRule(MerchantCategoryRuleEntity(merchantKeyword = "이마트", targetSpaceId = shared, categoryId = 1, subCategoryId = 2))
        enqueue(draft(50_000, "이마트", app = "KB Pay", occurredAt = now + 10 * 60_000))
        assertEquals(shared, pending.state.value.first { it.parsedMerchant == "이마트" }.suggestedSpaceId)
    }

    @Test
    fun `기본 공간을 정하지 않으면 마지막으로 보던 공간에 담긴다`() = runTest {
        settings.setCurrentSpaceId(personal)
        enqueue(draft(3_000, "편의점", app = "KB Pay"))
        assertEquals(personal, pending.state.value.single().suggestedSpaceId)
    }

    @Test
    fun `처음 만든 기본 규칙보다 나중에 저장한 공간이 우선한다`() = runTest {
        // 첫 실행 시드 규칙: 우아한형제들 → 공통
        rules.upsertMerchantRule(MerchantCategoryRuleEntity(merchantKeyword = "우아한형제들", targetSpaceId = shared, categoryId = 1, subCategoryId = 2))
        val firstId = requireNotNull(enqueue(draft(23_500, "(주)우아한형제들(배달의민족)", app = "KB Pay")))
        assertEquals(shared, pending.state.value.single().suggestedSpaceId)

        // 사용자가 개인 용돈으로 바꿔 저장 → 가맹점 규칙 학습
        updateClassification(firstId, personal, TransactionType.EXPENSE, null, null)
        approveAll()

        // 다음 배달 알림은 개인 용돈으로 추천
        enqueue(draft(18_000, "(주)우아한형제들(배달의민족)", app = "KB Pay", occurredAt = now + 60 * 60_000))
        assertEquals(personal, pending.state.value.last().suggestedSpaceId)
    }

    // ---- 검수 카드에서 공간 변경 ----

    @Test
    fun `카드에서 공간을 바꾸면 카테고리도 그 공간 것으로 바뀌고 일괄 승인에 반영된다`() = runTest {
        val id = requireNotNull(enqueue(draft(5_800, "스타벅스", app = "KB Pay")))
        assertEquals(shared, pending.state.value.single().suggestedSpaceId)
        assertEquals(1L, pending.state.value.single().suggestedCategoryId)

        updateClassification(id, personal, TransactionType.EXPENSE, null, null)
        val updated = pending.state.value.single()
        assertEquals(personal, updated.suggestedSpaceId)
        assertEquals(10L, updated.suggestedCategoryId)
        assertEquals(11L, updated.suggestedSubCategoryId)

        approveAll()
        val tx = transactions.state.value.single()
        assertEquals(personal, tx.spaceId)
        assertEquals(10L, tx.categoryId)
    }

    @Test
    fun `다른 공간 카테고리를 넘겨도 저장 공간의 카테고리로 맞춘다`() = runTest {
        val id = requireNotNull(enqueue(draft(5_800, "스타벅스", app = "KB Pay")))
        updateClassification(id, personal, TransactionType.EXPENSE, 1L, 2L)
        assertEquals(10L, pending.state.value.single().suggestedCategoryId)
    }

    @Test
    fun `카테고리 기본값은 수입이면 수입 카테고리를 고른다`() {
        val personalCats = categories.state.value.filter { it.spaceId == personal }
        assertEquals(12L to null, CategoryDefaults.pick(personalCats, TransactionType.INCOME, null, null))
        assertEquals(10L to 11L, CategoryDefaults.pick(personalCats, TransactionType.EXPENSE, 99L, null))
        assertEquals(10L to 11L, CategoryDefaults.pick(personalCats, TransactionType.TRANSFER, 10L, 11L))
    }
}
