package com.budget.householdbook.domain.usecase

import com.budget.householdbook.core.common.BillingCycleCalculator
import com.budget.householdbook.core.common.BillingPeriod
import com.budget.householdbook.core.datastore.AppUserSettings
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.domain.model.FALLBACK_SHARED_SPACE
import com.budget.householdbook.domain.model.HouseholdOverview
import com.budget.householdbook.domain.model.groupedForSpace
import com.budget.householdbook.domain.repository.AutomationRuleRepository
import com.budget.householdbook.domain.repository.CategoryRepository
import com.budget.householdbook.domain.repository.KbQuickInquiryRepository
import com.budget.householdbook.domain.repository.LedgerSpaceRepository
import com.budget.householdbook.domain.repository.MealRepository
import com.budget.householdbook.domain.repository.PendingQueueRepository
import com.budget.householdbook.domain.repository.SettingsRepository
import com.budget.householdbook.domain.repository.SharedSyncRepository
import com.budget.householdbook.domain.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

/**
 * 현재 선택된 공간과 정산 주기(기준일 + 월 이동)에 맞춰 화면 전체 데이터를 구독합니다.
 * - 공간·기준일·월 이동이 바뀌면 해당 기간의 거래·집밥 기록 쿼리를 다시 구독 ([BillingCycleCalculator] 범위 쿼리)
 * - 예산 지표는 [CalculateBudgetSummaryUseCase]로 계산
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveHouseholdOverviewUseCase(
    private val settingsRepository: SettingsRepository,
    private val spaceRepository: LedgerSpaceRepository,
    private val categoryRepository: CategoryRepository,
    private val pendingQueueRepository: PendingQueueRepository,
    private val ruleRepository: AutomationRuleRepository,
    private val transactionRepository: TransactionRepository,
    private val mealRepository: MealRepository,
    private val kbRepository: KbQuickInquiryRepository,
    private val sharedSyncRepository: SharedSyncRepository,
    private val calculateBudgetSummary: CalculateBudgetSummaryUseCase
) {
    private data class PeriodQueryContext(
        val settings: AppUserSettings,
        val spaces: List<LedgerSpaceEntity>,
        val currentSpace: LedgerSpaceEntity,
        val monthOffset: Int,
        val period: BillingPeriod
    )

    /**
     * @param monthOffset 이번 정산 주기 기준 월 이동 (0 = 이번 주기, -1 = 지난 주기)
     */
    operator fun invoke(monthOffset: Flow<Int>): Flow<HouseholdOverview> {
        val periodContextFlow = combine(
            settingsRepository.userSettingsFlow,
            spaceRepository.observeAllSpaces(),
            monthOffset
        ) { settings, spaces, offset ->
            val currentSpace = spaces.firstOrNull { it.id == settings.currentSpaceId }
                ?: spaces.firstOrNull()
                ?: FALLBACK_SHARED_SPACE
            PeriodQueryContext(
                settings = settings,
                spaces = spaces,
                currentSpace = currentSpace,
                monthOffset = offset,
                period = BillingCycleCalculator.calculatePeriod(
                    billingStartDay = currentSpace.billingStartDay,
                    monthOffset = offset
                )
            )
        }

        val periodDataFlow = periodContextFlow.flatMapLatest { ctx ->
            combine(
                transactionRepository.observeTransactionsInPeriod(
                    spaceId = ctx.currentSpace.id,
                    startMillis = ctx.period.startMillis,
                    endExclusiveMillis = ctx.period.endExclusiveMillis
                ),
                mealRepository.observeMealLogsInPeriod(
                    spaceId = ctx.currentSpace.id,
                    startMillis = ctx.period.startMillis,
                    endExclusiveMillis = ctx.period.endExclusiveMillis
                )
            ) { txs, meals ->
                Triple(ctx, txs, meals)
            }
        }

        return combine(
            periodDataFlow,
            categoryRepository.observeAllActiveCategories(),
            pendingQueueRepository.observePendingQueue(),
            ruleRepository.observeIgnoredKeywords(),
            combine(kbRepository.credentialsFlow, sharedSyncRepository.syncStatusMessage) { creds, syncMsg ->
                creds to syncMsg
            }
        ) { (ctx, txs, meals), allCategories, pendingQueue, ignoredKeywords, (kbCreds, syncMsg) ->
            val groupsBySpace = ctx.spaces.associate { space -> space.id to allCategories.groupedForSpace(space.id) }

            HouseholdOverview(
                settings = ctx.settings,
                spaces = ctx.spaces,
                currentSpace = ctx.currentSpace,
                monthOffset = ctx.monthOffset,
                billingPeriod = ctx.period,
                allCategories = allCategories,
                currentSpaceCategoryGroups = groupsBySpace[ctx.currentSpace.id]
                    ?: allCategories.groupedForSpace(ctx.currentSpace.id),
                categoryGroupsBySpace = groupsBySpace,
                pendingQueue = pendingQueue,
                transactions = txs,
                mealLogs = meals,
                ignoredKeywords = ignoredKeywords,
                kbCredentials = kbCreds,
                syncStatusMessage = syncMsg,
                budget = calculateBudgetSummary(ctx.currentSpace, ctx.period, txs, allCategories),
                totalMealCount = meals.sumOf { it.count },
                totalMealSavedAmount = meals.sumOf { it.savedAmount }
            )
        }
    }
}
