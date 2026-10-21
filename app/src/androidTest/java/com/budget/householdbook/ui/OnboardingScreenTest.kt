package com.budget.householdbook.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budget.householdbook.ui.onboarding.OnboardingPages
import com.budget.householdbook.ui.onboarding.OnboardingScreen
import com.budget.householdbook.ui.theme.HouseholdBookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var finished: Boolean? = null

    private fun show() {
        composeRule.setContent {
            HouseholdBookTheme {
                OnboardingScreen(onFinish = { finished = it })
            }
        }
    }

    private fun goToLastPage() {
        repeat(OnboardingPages.size - 1) {
            composeRule.onNodeWithText("다음").performClick()
            composeRule.waitForIdle()
        }
    }

    @Test
    fun 첫_장에서_건너뛰면_예시_데이터_없이_끝난다() {
        show()
        composeRule.onNodeWithText("환영해요!").assertExists()
        composeRule.onNodeWithText("건너뛰기").performClick()
        assertEquals(false, finished)
    }

    @Test
    fun 끝까지_넘기면_마지막_장에서_시작하기와_예시_데이터를_고를_수_있다() {
        show()
        goToLastPage()

        composeRule.onNodeWithText(OnboardingPages.last().title).assertExists()
        composeRule.onNodeWithText("건너뛰기").assertDoesNotExist()
        composeRule.onNodeWithText("다음").assertDoesNotExist()

        composeRule.onNodeWithText("예시 데이터로 둘러보기").performClick()
        assertEquals(true, finished)
    }

    @Test
    fun 마지막_장의_시작하기는_예시_데이터_없이_끝난다() {
        show()
        goToLastPage()
        composeRule.onNodeWithText("시작하기").performClick()
        assertEquals(false, finished)
    }
}
