package com.budget.householdbook.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budget.householdbook.ui.settings.DeleteAccountDialog
import com.budget.householdbook.ui.theme.HouseholdBookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeleteAccountDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var confirmed: Boolean? = null
    private var state by mutableStateOf<AccountDeletionState>(AccountDeletionState.Idle)

    private fun show() {
        composeRule.setContent {
            HouseholdBookTheme {
                DeleteAccountDialog(
                    email = "me@gmail.com",
                    state = state,
                    onConfirm = { confirmed = it },
                    onDismiss = {}
                )
            }
        }
    }

    @Test
    fun 기본은_이_휴대폰_기록을_남기고_탈퇴한다() {
        show()
        composeRule.onNodeWithText("me@gmail.com 계정으로 탈퇴합니다.").assertExists()
        composeRule.onNodeWithText("탈퇴하기").performClick()
        assertEquals(false, confirmed)
    }

    @Test
    fun 기록도_지우기를_고르면_경고를_보여주고_함께_전달한다() {
        show()
        composeRule.onNodeWithText("이 휴대폰의 기록도 모두 지우기").performClick()
        composeRule.onNodeWithText("장부·설정이 모두 지워지고 앱이 닫혀요. 되돌릴 수 없어요.").assertExists()
        composeRule.onNodeWithText("탈퇴하기").performClick()
        assertEquals(true, confirmed)
    }

    @Test
    fun 실패하면_이유를_보여주고_진행_중에는_다시_누를_수_없다() {
        show()
        state = AccountDeletionState.Failed("함께 쓰는 멤버가 있어 바로 탈퇴할 수 없습니다.")
        composeRule.onNodeWithText("함께 쓰는 멤버가 있어 바로 탈퇴할 수 없습니다.").assertExists()

        state = AccountDeletionState.InProgress
        composeRule.onNodeWithText("취소").assertIsNotEnabled()
    }
}
