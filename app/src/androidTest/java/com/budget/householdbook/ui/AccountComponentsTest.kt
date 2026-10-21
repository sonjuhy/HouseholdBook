package com.budget.householdbook.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budget.householdbook.ui.settings.AccountProfileRow
import com.budget.householdbook.ui.settings.EditDisplayNameDialog
import com.budget.householdbook.ui.settings.InvitePartnerDialog
import com.budget.householdbook.ui.theme.HouseholdBookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun 계정_행은_이름과_이메일을_보여주고_이름_변경과_로그아웃을_누를_수_있다() {
        var edit = false
        var signOut = false
        composeRule.setContent {
            HouseholdBookTheme {
                AccountProfileRow(
                    name = "민수",
                    email = "minsu@gmail.com",
                    status = "혼자 쓰기 · 이 휴대폰에만 저장돼요",
                    onEditName = { edit = true },
                    onSignOut = { signOut = true }
                )
            }
        }
        composeRule.onNodeWithText("민수").assertExists()
        composeRule.onNodeWithText("minsu@gmail.com").assertExists()
        composeRule.onNodeWithText("이름 변경").performClick()
        composeRule.onNodeWithText("로그아웃").performClick()
        assertTrue(edit && signOut)
    }

    @Test
    fun 이름은_바뀌었을_때만_저장할_수_있다() {
        var saved: String? = null
        composeRule.setContent {
            HouseholdBookTheme {
                EditDisplayNameDialog(currentName = "minsu", onConfirm = { saved = it }, onDismiss = {})
            }
        }
        composeRule.onNodeWithText("저장").assertIsNotEnabled()
        val field = composeRule.onNode(hasSetTextAction() and hasText("이름"))
        field.performTextClearance()
        field.performTextInput(" 민수 ")
        composeRule.onNodeWithText("저장").assertIsEnabled().performClick()
        assertEquals("민수", saved)
    }

    @Test
    fun 초대는_이메일_형식일_때만_보낼_수_있고_이름은_선택이다() {
        var invited: Pair<String, String>? = null
        composeRule.setContent {
            HouseholdBookTheme {
                InvitePartnerDialog(onInvite = { email, name -> invited = email to name }, onDismiss = {})
            }
        }
        val email = composeRule.onNode(hasSetTextAction() and hasText("구글 계정 이메일"))
        email.performTextInput("partner")
        composeRule.onNodeWithText("초대 보내기").assertIsNotEnabled()
        email.performTextInput("@Gmail.com")
        composeRule.onNodeWithText("초대 보내기").assertIsEnabled().performClick()
        assertEquals("partner@gmail.com" to "", invited)
    }
}
