package com.budget.householdbook.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budget.householdbook.ui.settings.NotificationSettingsContent
import com.budget.householdbook.ui.theme.HouseholdBookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationSettingsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var clicked: String? = null

    private fun show(
        allowed: Boolean = true,
        disabledKinds: List<String> = emptyList(),
        batteryRestricted: Boolean = false
    ) {
        composeRule.setContent {
            HouseholdBookTheme {
                NotificationSettingsContent(
                    allowed = allowed,
                    disabledKinds = disabledKinds,
                    batteryRestricted = batteryRestricted,
                    onEnableNotifications = { clicked = "enable" },
                    onOpenKinds = { clicked = "kinds" },
                    onOpenBattery = { clicked = "battery" }
                )
            }
        }
    }

    @Test
    fun 모두_켜져_있으면_알림_켜짐만_보인다() {
        show()
        composeRule.onNodeWithText("알림 켜짐").assertExists()
        composeRule.onNodeWithText("배터리 설정").assertDoesNotExist()
    }

    @Test
    fun 권한이_없으면_알림_켜기를_보여준다() {
        show(allowed = false)
        composeRule.onNodeWithText("알림 꺼짐").assertExists()
        composeRule.onNodeWithText("알림 켜기").performClick()
        assertEquals("enable", clicked)
    }

    @Test
    fun 꺼진_종류가_있으면_이름을_알려주고_종류별_설정으로_보낸다() {
        show(disabledKinds = listOf("새 결제", "예산"))
        composeRule.onNodeWithText("일부 알림 꺼짐").assertExists()
        composeRule.onNodeWithText("새 결제·예산 알림이 꺼져 있어요. 종류별 설정에서 켤 수 있어요.").assertExists()
        composeRule.onNodeWithText("종류별 설정").performClick()
        assertEquals("kinds", clicked)
    }

    @Test
    fun 배터리_제한이_있으면_배터리_설정으로_보낸다() {
        show(batteryRestricted = true)
        composeRule.onNodeWithText("배터리 사용 제한").assertExists()
        composeRule.onNodeWithText("배터리 설정").performClick()
        assertEquals("battery", clicked)
    }
}
