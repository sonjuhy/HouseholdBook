package com.budget.householdbook.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budget.householdbook.domain.model.SharedRoomMembers
import com.budget.householdbook.ui.settings.SharedMembersDialog
import com.budget.householdbook.ui.theme.HouseholdBookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedMembersDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val owner = "owner@gmail.com"
    private val partner = "partner@gmail.com"
    private val invited = "invited@gmail.com"

    private class Calls {
        var canceled: String? = null
        var removed: String? = null
        var left = false
    }

    private fun show(myEmail: String): Calls {
        val calls = Calls()
        val info = SharedRoomMembers.build(
            inviteCode = "HB-ABCD-EFGH",
            ownerEmail = owner,
            memberEmails = listOf(owner, partner, invited),
            joinedEmails = listOf(owner, partner),
            memberNames = mapOf(owner to "엄마", partner to "아빠", invited to "동생"),
            myEmail = myEmail
        )
        composeRule.setContent {
            HouseholdBookTheme {
                SharedMembersDialog(
                    state = SharedMembersState.Loaded(info),
                    onRetry = {},
                    onInviteNew = {},
                    onCancelInvitation = { calls.canceled = it },
                    onRemoveMember = { calls.removed = it },
                    onLeave = { calls.left = true },
                    onDismiss = {}
                )
            }
        }
        return calls
    }

    @Test
    fun 방장은_초대를_취소하고_확인_후_멤버를_내보낸다() {
        val calls = show(owner)

        composeRule.onNodeWithText("엄마 (나)").assertExists()
        composeRule.onNodeWithText("수락 대기").assertExists()
        composeRule.onNodeWithText("초대 취소").performClick()
        assertEquals(invited, calls.canceled)

        composeRule.onNodeWithText("내보내기").performClick()
        composeRule.onNodeWithText("멤버 내보내기").assertExists()
        assertEquals(null, calls.removed)
        composeRule.onNodeWithText("내보내기").performClick()
        assertEquals(partner, calls.removed)
    }

    @Test
    fun 멤버는_내보내기_대신_나가기를_확인_후_할_수_있다() {
        val calls = show(partner)

        composeRule.onNodeWithText("내보내기").assertDoesNotExist()
        composeRule.onNodeWithText("이 공유 장부에서 나가기").performClick()
        composeRule.onNodeWithText("나가기").performClick()
        assertTrue(calls.left)
    }
}
