package com.budget.householdbook.domain.usecase

import com.budget.householdbook.core.common.BillingCycleCalculator
import com.budget.householdbook.core.common.CurrencyAndDateFormatter
import com.budget.householdbook.data.local.entity.MealSavingLogEntity
import com.budget.householdbook.domain.model.MealPriceConfig
import com.budget.householdbook.domain.model.MealType
import com.budget.householdbook.domain.model.MealWidgetSummary
import com.budget.householdbook.domain.repository.LedgerSpaceRepository
import com.budget.householdbook.domain.repository.MealRepository
import com.budget.householdbook.domain.repository.MealWidgetRefresher
import com.budget.householdbook.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * 끼니 직접 해먹기(집밥) 원터치 +1 기록 (단순 횟수 × 끼니별 지정 단가)
 */
class RecordHomeCookedMealUseCase(
    private val settingsRepository: SettingsRepository,
    private val mealRepository: MealRepository,
    private val widgetRefresher: MealWidgetRefresher
) {
    /**
     * @param spaceId 기록할 공간 (null이면 현재 선택된 공간 — 홈 화면 위젯용)
     * @return 적용된 1회 단가
     */
    suspend operator fun invoke(
        mealType: MealType,
        spaceId: String? = null,
        count: Int = 1,
        memo: String = "",
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val settings = settingsRepository.userSettingsFlow.first()
        val unitPrice = settings.mealPriceConfig.priceFor(mealType)
        mealRepository.insertMealLog(
            MealSavingLogEntity(
                spaceId = spaceId ?: settings.currentSpaceId,
                dateIso = CurrencyAndDateFormatter.formatDateIso(nowMillis),
                mealType = mealType,
                count = count,
                unitAmount = unitPrice,
                savedAmount = unitPrice * count,
                memo = memo.ifBlank { "${mealType.displayName} 직접 해결" },
                occurredAt = nowMillis
            )
        )
        widgetRefresher.refresh()
        return unitPrice
    }
}

/** 집밥 기록 삭제 */
class DeleteMealLogUseCase(
    private val mealRepository: MealRepository,
    private val widgetRefresher: MealWidgetRefresher
) {
    suspend operator fun invoke(id: Long) {
        mealRepository.deleteMealLog(id)
        widgetRefresher.refresh()
    }
}

/** 끼니별 절약 단가 변경 */
class UpdateMealPriceConfigUseCase(
    private val settingsRepository: SettingsRepository,
    private val widgetRefresher: MealWidgetRefresher
) {
    suspend operator fun invoke(config: MealPriceConfig) {
        settingsRepository.updateMealPriceConfig(config)
        widgetRefresher.refresh()
    }
}

/**
 * 홈 화면 위젯 표시용: 현재 공간의 이번 정산 주기 집밥 합계
 */
class GetMealWidgetSummaryUseCase(
    private val settingsRepository: SettingsRepository,
    private val spaceRepository: LedgerSpaceRepository,
    private val mealRepository: MealRepository
) {
    suspend operator fun invoke(): MealWidgetSummary {
        val settings = settingsRepository.userSettingsFlow.first()
        val space = spaceRepository.getSpaceById(settings.currentSpaceId)
        val period = BillingCycleCalculator.calculatePeriod(billingStartDay = space?.billingStartDay ?: 1)
        val total = mealRepository.sumMealsInPeriod(settings.currentSpaceId, period.startMillis, period.endExclusiveMillis)
        return MealWidgetSummary(
            spaceName = space?.name ?: "내 장부",
            periodLabel = "${period.startDate.monthValue}.${period.startDate.dayOfMonth} ~ " +
                "${period.endDateInclusive.monthValue}.${period.endDateInclusive.dayOfMonth}",
            totalMealCount = total.totalCount,
            totalSavedAmount = total.totalSaved
        )
    }
}
