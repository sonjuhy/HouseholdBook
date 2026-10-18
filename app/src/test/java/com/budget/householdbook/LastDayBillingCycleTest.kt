package com.budget.householdbook

import com.budget.householdbook.core.common.BillingCycleCalculator
import com.budget.householdbook.core.common.BillingCycleCalculator.LAST_DAY_OF_MONTH
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LastDayBillingCycleTest {

    @Test
    fun `말일 기준일은 전월 말일부터 당월 말일 전날까지다`() {
        val period = BillingCycleCalculator.calculatePeriod(
            baseDate = LocalDate.of(2026, 10, 18),
            billingStartDay = LAST_DAY_OF_MONTH
        )
        assertEquals(LocalDate.of(2026, 9, 30), period.startDate)
        assertEquals(LocalDate.of(2026, 10, 30), period.endDateInclusive)
        assertEquals(31, period.totalDays)
        assertEquals(13, period.remainingDays)
    }

    @Test
    fun `오늘이 말일이면 오늘부터 새 주기가 시작된다`() {
        val period = BillingCycleCalculator.calculatePeriod(
            baseDate = LocalDate.of(2026, 10, 31),
            billingStartDay = LAST_DAY_OF_MONTH
        )
        assertEquals(LocalDate.of(2026, 10, 31), period.startDate)
        assertEquals(LocalDate.of(2026, 11, 29), period.endDateInclusive)
    }

    @Test
    fun `2월과 윤년에도 매달 말일로 이어지고 주기 사이에 빈틈이 없다`() {
        val base = LocalDate.of(2028, 3, 10) // 2028년은 윤년
        val feb = BillingCycleCalculator.calculatePeriod(base, LAST_DAY_OF_MONTH, monthOffset = -1)
        val mar = BillingCycleCalculator.calculatePeriod(base, LAST_DAY_OF_MONTH, monthOffset = 0)
        val apr = BillingCycleCalculator.calculatePeriod(base, LAST_DAY_OF_MONTH, monthOffset = 1)

        assertEquals(LocalDate.of(2028, 1, 31), feb.startDate)
        assertEquals(LocalDate.of(2028, 2, 29), mar.startDate)
        assertEquals(LocalDate.of(2028, 3, 31), apr.startDate)
        assertEquals(feb.endExclusiveMillis, mar.startMillis)
        assertEquals(mar.endExclusiveMillis, apr.startMillis)
    }

    @Test
    fun `저장 가능한 기준일 보정과 화면 표기`() {
        assertEquals(LAST_DAY_OF_MONTH, BillingCycleCalculator.sanitizeStartDay(LAST_DAY_OF_MONTH))
        assertEquals(28, BillingCycleCalculator.sanitizeStartDay(31))
        assertEquals(1, BillingCycleCalculator.sanitizeStartDay(0))
        assertEquals("말일", BillingCycleCalculator.startDayLabel(LAST_DAY_OF_MONTH))
        assertEquals("25일", BillingCycleCalculator.startDayLabel(25))
    }
}
