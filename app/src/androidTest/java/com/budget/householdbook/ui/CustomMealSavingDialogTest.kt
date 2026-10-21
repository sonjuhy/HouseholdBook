package com.budget.householdbook.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budget.householdbook.ui.mealsaver.CustomMealSavingDialog
import com.budget.householdbook.ui.theme.HouseholdBookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomMealSavingDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var saved: Pair<Long, String>? = null

    private fun show(initialLabel: String = "") {
        composeRule.setContent {
            HouseholdBookTheme {
                CustomMealSavingDialog(
                    initialLabel = initialLabel,
                    onConfirm = { amount, label -> saved = amount to label },
                    onDismiss = {}
                )
            }
        }
    }

    @Test
    fun 금액을_넣기_전에는_기록할_수_없고_빠른_더하기로_금액을_채울_수_있다() {
        show(initialLabel = "김치볶음밥")
        composeRule.onNodeWithText("기록하기").assertIsNotEnabled()

        composeRule.onNodeWithText("+5천").performClick()
        composeRule.onNodeWithText("+5천").performClick()
        composeRule.onNodeWithText("10,000원").assertExists()
        composeRule.onNodeWithText("기록하기").assertIsEnabled().performClick()

        // 메뉴 메모 칸에 적어 둔 내용을 그대로 가져옴
        assertEquals(10_000L to "김치볶음밥", saved)
    }

    @Test
    fun 금액과_내용을_직접_입력해_기록한다() {
        show()
        composeRule.onNode(hasSetTextAction() and hasText("아낀 금액 (원)")).performTextInput("8500")
        composeRule.onNode(hasSetTextAction() and hasText("무엇을 아꼈나요? (선택)")).performTextInput("회사에 도시락 싸 감")
        composeRule.onNodeWithText("8,500원").assertExists()
        composeRule.onNodeWithText("기록하기").performClick()
        assertEquals(8_500L to "회사에 도시락 싸 감", saved)
    }

    @Test
    fun 너무_큰_금액은_안내하고_기록할_수_없다() {
        show()
        composeRule.onNode(hasSetTextAction() and hasText("아낀 금액 (원)")).performTextInput("2000000")
        composeRule.onNodeWithText("한 번에 100만 원까지 입력할 수 있어요.").assertExists()
        composeRule.onNodeWithText("기록하기").assertIsNotEnabled()
    }
}
