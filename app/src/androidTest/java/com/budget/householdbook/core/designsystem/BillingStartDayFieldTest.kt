package com.budget.householdbook.core.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budget.householdbook.core.common.BillingCycleCalculator
import com.budget.householdbook.ui.theme.HouseholdBookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BillingStartDayFieldTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var lastValue = -1

    private fun setField(initial: Int) {
        composeRule.setContent {
            HouseholdBookTheme {
                var day by remember { mutableIntStateOf(initial) }
                BillingStartDayField(
                    startDay = day,
                    onStartDayChange = {
                        day = it
                        lastValue = it
                    }
                )
            }
        }
    }

    @Test
    fun 매월_말일을_고르면_99가_되고_날짜_입력칸이_사라진다() {
        setField(25)

        composeRule.onNodeWithText("매월 말일").performClick()

        assertEquals(BillingCycleCalculator.LAST_DAY_OF_MONTH, lastValue)
        composeRule.onNodeWithText("월 정산 시작일 (1 ~ 28일)").assertDoesNotExist()
    }

    @Test
    fun 날짜를_28일보다_크게_입력하면_28일로_보정된다() {
        setField(1)

        composeRule.onNodeWithText("월 정산 시작일 (1 ~ 28일)").performTextReplacement("31")

        assertEquals(28, lastValue)
    }

    @Test
    fun 말일에서_날짜_지정으로_돌아오면_입력값으로_복원된다() {
        setField(BillingCycleCalculator.LAST_DAY_OF_MONTH)

        composeRule.onNodeWithText("날짜 지정").performClick()

        // 말일 상태로 시작하면 기본 입력값 25일로 복원
        assertEquals(25, lastValue)
        composeRule.onNodeWithText("월 정산 시작일 (1 ~ 28일)").assertExists()
    }
}
