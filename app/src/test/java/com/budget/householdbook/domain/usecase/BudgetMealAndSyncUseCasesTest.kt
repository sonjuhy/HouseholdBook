package com.budget.householdbook.domain.usecase

import com.budget.householdbook.core.common.BillingCycleCalculator
import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.core.datastore.AppUserSettings
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.domain.model.MealPriceConfig
import com.budget.householdbook.domain.model.MealType
import com.budget.householdbook.domain.model.SharedSyncOutcome
import com.budget.householdbook.domain.model.SpaceType
import com.budget.householdbook.domain.model.SyncStatus
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.domain.model.UserTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 대시보드 예산 계산, 집밥 카운트, 거래 삭제·동기화 UseCase 검증
 */
class BudgetMealAndSyncUseCasesTest {

    private val shared = AppDatabase.SPACE_ID_SHARED
    private val personal = AppDatabase.SPACE_ID_PERSONAL

    private val sharedSpace = LedgerSpaceEntity(
        id = shared,
        name = "우리집 공통 생활비",
        type = SpaceType.SHARED,
        billingStartDay = 1,
        targetBudgetAmount = 1_500_000L
    )

    private val categories = listOf(
        CategoryEntity(id = 1, spaceId = shared, name = "식비"),
        CategoryEntity(id = 2, spaceId = shared, parentId = 1, name = "식재료/장보기"),
        CategoryEntity(id = 3, spaceId = shared, parentId = 1, name = "배달/외식"),
        CategoryEntity(id = 4, spaceId = shared, name = "주거/생활"),
        CategoryEntity(id = 5, spaceId = shared, transactionType = TransactionType.INCOME, name = "생활비 입금"),
        CategoryEntity(id = 9, spaceId = personal, name = "취미")
    )

    private fun tx(
        id: Long,
        amount: Long,
        categoryId: Long,
        subCategoryId: Long? = null,
        type: TransactionType = TransactionType.EXPENSE,
        canceled: Boolean = false,
        spaceId: String = shared
    ) = TransactionEntity(
        id = id,
        remoteId = "r$id",
        spaceId = spaceId,
        type = type,
        amount = amount,
        categoryId = categoryId,
        subCategoryId = subCategoryId,
        merchantOrTitle = "t$id",
        isCanceled = canceled,
        transactionDate = 0L
    )

    @Test
    fun `예산 요약은 취소되지 않은 지출만 합산하고 이체와 수입은 따로 집계한다`() {
        val period = BillingCycleCalculator.calculatePeriod(baseDate = LocalDate.of(2026, 10, 18), billingStartDay = 1)
        val transactions = listOf(
            tx(1, 145_000, 1, 2),
            tx(2, 30_000, 1, 3),
            tx(3, 20_000, 1, null),
            tx(4, 18_900, 4),
            tx(5, 50_000, 4, canceled = true),
            tx(6, 100_000, 1, type = TransactionType.TRANSFER),
            tx(7, 500_000, 5, type = TransactionType.INCOME)
        )

        val summary = CalculateBudgetSummaryUseCase()(sharedSpace, period, transactions, categories)

        assertEquals(213_900L, summary.totalSpentAmount)
        assertEquals(500_000L, summary.totalIncomeAmount)
        assertEquals(100_000L, summary.totalTransferAmount)
        assertEquals(1_286_100L, summary.remainingBudgetAmount)
        assertEquals(213_900f / 1_500_000f, summary.budgetUsageRatio, 0.0001f)
        // 10.18 기준 남은 일수 14일 (10.18 ~ 10.31)
        assertEquals(14, period.remainingDays)
        assertEquals(1_286_100L / 14, summary.recommendedDailyBudget)

        assertEquals(listOf("식비", "주거/생활"), summary.categorySpendSummaries.map { it.parentCategory.name })
        val food = summary.categorySpendSummaries.first()
        assertEquals(195_000L, food.totalSpent)
        assertEquals(
            listOf("식재료/장보기" to 145_000L, "배달/외식" to 30_000L, "기타(식비)" to 20_000L),
            food.subBreakdown
        )
    }

    @Test
    fun `예산을 초과하면 남은 금액은 음수이고 권장 지출액은 0이다`() {
        val period = BillingCycleCalculator.calculatePeriod(baseDate = LocalDate.of(2026, 10, 18), billingStartDay = 1)
        val summary = CalculateBudgetSummaryUseCase()(
            sharedSpace.copy(targetBudgetAmount = 100_000L),
            period,
            listOf(tx(1, 120_000, 1)),
            categories
        )
        assertEquals(-20_000L, summary.remainingBudgetAmount)
        assertEquals(0L, summary.recommendedDailyBudget)
        assertTrue(summary.budgetUsageRatio > 1f)
    }

    @Test
    fun `목표 예산은 음수가 될 수 없고 기준일은 1~28 또는 말일로 보정된다`() = runTest {
        val spaces = FakeLedgerSpaceRepository(listOf(sharedSpace))
        val update = UpdateSpaceBudgetUseCase(spaces)

        update(shared, -5_000L, 31)
        assertEquals(0L, spaces.getSpaceById(shared)!!.targetBudgetAmount)
        assertEquals(28, spaces.getSpaceById(shared)!!.billingStartDay)

        update(shared, 900_000L, BillingCycleCalculator.LAST_DAY_OF_MONTH)
        assertEquals(BillingCycleCalculator.LAST_DAY_OF_MONTH, spaces.getSpaceById(shared)!!.billingStartDay)
    }

    @Test
    fun `집밥 +1은 끼니별 단가로 기록하고 위젯을 갱신하며, 공간을 생략하면 현재 공간에 기록한다`() = runTest {
        val settings = FakeSettingsRepository(
            AppUserSettings(currentSpaceId = personal, mealPriceConfig = MealPriceConfig(dinnerPrice = 15_000L))
        )
        val meals = FakeMealRepository()
        val widget = CountingWidgetRefresher()
        val record = RecordHomeCookedMealUseCase(settings, meals, widget)

        val unitPrice = record(MealType.DINNER, spaceId = shared, count = 2, nowMillis = 1_000L)
        record(MealType.DINNER, nowMillis = 2_000L)

        assertEquals(15_000L, unitPrice)
        val (first, second) = meals.state.value
        assertEquals(shared, first.spaceId)
        assertEquals(30_000L, first.savedAmount)
        assertEquals("${MealType.DINNER.displayName} 직접 해결", first.memo)
        assertEquals(personal, second.spaceId)
        assertEquals(15_000L, second.savedAmount)
        assertEquals(2, widget.count)

        DeleteMealLogUseCase(meals, widget)(first.id)
        assertEquals(1, meals.state.value.size)
        assertEquals(3, widget.count)
    }

    @Test
    fun `아낀 금액 직접 입력은 입력한 금액과 내용으로 기록하고 위젯 합계에도 들어간다`() = runTest {
        val settings = FakeSettingsRepository(AppUserSettings(currentSpaceId = shared))
        val spaces = FakeLedgerSpaceRepository(listOf(sharedSpace))
        val meals = FakeMealRepository()
        val widget = CountingWidgetRefresher()
        val recordCustom = RecordCustomMealSavingUseCase(settings, meals, widget)

        recordCustom(amount = 8_500L, label = "  회사에 도시락 싸 감 ")
        recordCustom(amount = 4_000L, label = "", spaceId = personal)

        val (lunchBox, tumbler) = meals.state.value
        assertEquals(MealType.CUSTOM, lunchBox.mealType)
        assertEquals(8_500L, lunchBox.unitAmount)
        assertEquals(8_500L, lunchBox.savedAmount)
        assertEquals("회사에 도시락 싸 감", lunchBox.memo)
        assertEquals(shared, lunchBox.spaceId)
        assertEquals("직접 입력으로 아낀 금액", tumbler.memo)
        assertEquals(personal, tumbler.spaceId)
        assertEquals(2, widget.count)

        RecordHomeCookedMealUseCase(settings, meals, widget)(MealType.LUNCH)
        val summary = GetMealWidgetSummaryUseCase(settings, spaces, meals)()
        assertEquals(2, summary.totalMealCount)
        assertEquals(8_500L + MealType.LUNCH.defaultUnitPrice, summary.totalSavedAmount)
    }

    @Test
    fun `직접 입력 금액이 0원이거나 너무 크면 기록하지 않고, 원터치 기록으로는 직접 입력을 쓸 수 없다`() = runTest {
        val settings = FakeSettingsRepository()
        val meals = FakeMealRepository()
        val recordCustom = RecordCustomMealSavingUseCase(settings, meals, CountingWidgetRefresher())

        assertTrue(runCatching { recordCustom(amount = 0L, label = "") }.isFailure)
        assertTrue(runCatching { recordCustom(amount = RecordCustomMealSavingUseCase.MAX_AMOUNT + 1, label = "") }.isFailure)
        assertTrue(runCatching { RecordHomeCookedMealUseCase(settings, meals, CountingWidgetRefresher())(MealType.CUSTOM) }.isFailure)
        assertTrue(meals.state.value.isEmpty())

        recordCustom(amount = RecordCustomMealSavingUseCase.MAX_AMOUNT, label = "")
        assertEquals(1, meals.state.value.size)
        assertEquals(listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK), MealType.quickTypes)
    }

    @Test
    fun `위젯 요약은 현재 공간의 이번 정산 주기 합계를 보여준다`() = runTest {
        val settings = FakeSettingsRepository(AppUserSettings(currentSpaceId = shared))
        val spaces = FakeLedgerSpaceRepository(listOf(sharedSpace))
        val meals = FakeMealRepository()
        val record = RecordHomeCookedMealUseCase(settings, meals, CountingWidgetRefresher())
        record(MealType.LUNCH)
        record(MealType.DINNER)
        record(MealType.DINNER, spaceId = personal)

        val summary = GetMealWidgetSummaryUseCase(settings, spaces, meals)()

        assertEquals("우리집 공통 생활비", summary.spaceName)
        assertEquals(2, summary.totalMealCount)
        assertEquals(MealType.LUNCH.defaultUnitPrice + MealType.DINNER.defaultUnitPrice, summary.totalSavedAmount)
    }

    @Test
    fun `공유 중인 공통 생활비 거래 삭제는 원격 삭제 대기로, 그 외는 즉시 삭제된다`() = runTest {
        val settings = FakeSettingsRepository(AppUserSettings(userTier = UserTier.PRO))
        val spaces = FakeLedgerSpaceRepository(listOf(sharedSpace.copy(isRemoteShared = true)))
        val transactions = FakeTransactionRepository(listOf(tx(1, 10_000, 1), tx(2, 20_000, 9, spaceId = personal)))
        val sync = FakeSharedSyncRepository()
        val delete = DeleteTransactionUseCase(transactions, SyncSharedChangesUseCase(settings, spaces, sync))

        delete(1)
        delete(2)

        assertEquals(SyncStatus.PENDING_DELETE, transactions.getById(1)!!.syncStatus)
        assertNull(transactions.getById(2))
        assertEquals(listOf(UserTier.PRO to true), sync.syncCalls)
    }

    @Test
    fun `백그라운드 동기화 결과는 대상 여부와 남은 미반영 건수로 결정된다`() = runTest {
        val settings = FakeSettingsRepository(AppUserSettings(userTier = UserTier.FREE))
        val spaces = FakeLedgerSpaceRepository(listOf(sharedSpace.copy(isRemoteShared = true)))
        val transactions = FakeTransactionRepository(
            listOf(tx(1, 10_000, 1).copy(syncStatus = SyncStatus.PENDING_UPLOAD))
        )
        val sync = FakeSharedSyncRepository()
        val useCase = SyncPendingSharedChangesUseCase(settings, spaces, transactions, sync)

        assertEquals(SharedSyncOutcome.NOT_ELIGIBLE, useCase())

        settings.setUserTier(UserTier.PRO)
        sync.syncResult = false
        assertEquals(SharedSyncOutcome.RETRY, useCase())

        sync.canSync = false
        assertEquals(SharedSyncOutcome.NOT_ELIGIBLE, useCase())

        transactions.state.value = transactions.state.value.map { it.copy(syncStatus = SyncStatus.SYNCED) }
        assertEquals(SharedSyncOutcome.DONE, useCase())
    }

    @Test
    fun `함께 쓰기 설정은 유효한 초대번호만 쓰고 형식이 틀리면 새로 발급한다`() = runTest {
        val settings = FakeSettingsRepository(AppUserSettings(userTier = UserTier.PRO))
        val spaces = FakeLedgerSpaceRepository(listOf(sharedSpace.copy(inviteCode = "HB-2026")))
        val sync = FakeSharedSyncRepository()
        val update = UpdateSharedMembersUseCase(settings, spaces, sync)

        update("나, 짝꿍", isShared = true, customInviteCode = "직접입력")
        assertEquals("HB-TEST-CODE", spaces.getSpaceById(shared)!!.inviteCode)

        update("나, 짝꿍", isShared = true, customInviteCode = "hb-abcd-efgh")
        assertEquals("HB-ABCD-EFGH", spaces.getSpaceById(shared)!!.inviteCode)

        UpdateUserTierUseCase(settings, update)(UserTier.FREE)
        val space = spaces.getSpaceById(shared)!!
        assertNull(space.inviteCode)
        assertEquals(false, space.isRemoteShared)
        // 이름이 없고 로그인 전이면 '나' (예전처럼 예시 이름·'(혼자 쓰기)' 꼬리표를 붙이지 않음)
        assertEquals("나", space.memberNamesCsv)
    }

    @Test
    fun `대시보드 구독은 현재 공간과 월 이동에 맞춰 예산 지표를 계산한다`() = runTest {
        val settings = FakeSettingsRepository(AppUserSettings(currentSpaceId = shared))
        val spaces = FakeLedgerSpaceRepository(listOf(sharedSpace))
        val now = System.currentTimeMillis()
        val transactions = FakeTransactionRepository(
            listOf(tx(1, 145_000, 1, 2).copy(transactionDate = now), tx(2, 18_900, 4).copy(transactionDate = now))
        )
        val observe = ObserveHouseholdOverviewUseCase(
            settingsRepository = settings,
            spaceRepository = spaces,
            categoryRepository = FakeCategoryRepository(categories),
            pendingQueueRepository = FakePendingQueueRepository(),
            ruleRepository = FakeAutomationRuleRepository(),
            transactionRepository = transactions,
            mealRepository = FakeMealRepository(),
            kbRepository = FakeKbQuickInquiryRepository(),
            sharedSyncRepository = FakeSharedSyncRepository(),
            calculateBudgetSummary = CalculateBudgetSummaryUseCase()
        )
        val monthOffset = MutableStateFlow(0)

        val current = observe(monthOffset).first()
        assertEquals(shared, current.currentSpace.id)
        assertEquals(163_900L, current.budget.totalSpentAmount)
        assertEquals(1_336_100L, current.budget.remainingBudgetAmount)
        assertEquals(listOf("식비", "주거/생활", "생활비 입금"), current.currentSpaceCategoryGroups.map { it.parent.name })
        assertEquals(2, current.currentSpaceCategoryGroups.first().subCategories.size)

        monthOffset.value = -1
        val previous = observe(monthOffset).first()
        assertEquals(-1, previous.monthOffset)
        assertEquals(0L, previous.budget.totalSpentAmount)
    }
}
