package com.budget.householdbook.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.domain.model.SpaceType
import com.budget.householdbook.ui.settings.CsvExportContent
import com.budget.householdbook.ui.settings.CsvExportRequest
import com.budget.householdbook.ui.theme.HouseholdBookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class CsvExportContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val shared = AppDatabase.SPACE_ID_SHARED
    private val personal = AppDatabase.SPACE_ID_PERSONAL
    private val spaces = listOf(
        LedgerSpaceEntity(id = shared, name = "공통 생활비", type = SpaceType.SHARED, billingStartDay = 1),
        LedgerSpaceEntity(id = personal, name = "내 용돈", type = SpaceType.PERSONAL, billingStartDay = 25)
    )
    private var request: CsvExportRequest? = null

    private fun show() {
        composeRule.setContent {
            HouseholdBookTheme {
                CsvExportContent(
                    spaces = spaces,
                    currentSpaceId = shared,
                    onRequestExport = { request = it },
                    today = LocalDate.of(2026, 11, 22)
                )
            }
        }
    }

    @Test
    fun 처음에는_지금_장부의_이번_달을_저장한다() {
        show()
        composeRule.onAllNodesWithText("2026년 11월 (11.1 ~ 11.30)").assertCountEquals(2)
        composeRule.onNodeWithText("CSV로 저장").performClick()
        assertEquals(CsvExportRequest(listOf(shared), 0, 0, "가계부_공통 생활비_2026-11.csv"), request)
    }

    @Test
    fun 최근_3개월을_고르면_9월부터_11월까지_저장한다() {
        show()
        composeRule.onNodeWithText("최근 3개월").performClick()
        composeRule.onNodeWithText("2026년 9월 (9.1 ~ 9.30)").assertExists()
        composeRule.onNodeWithText("3개월 ·", substring = true).assertExists()
        composeRule.onNodeWithText("CSV로 저장").performClick()
        assertEquals(CsvExportRequest(listOf(shared), -2, 0, "가계부_공통 생활비_2026-09_2026-11.csv"), request)
    }

    @Test
    fun 끝을_시작보다_앞으로_옮기면_시작도_같이_옮기고_앞으로의_달은_고를_수_없다() {
        show()
        composeRule.onNodeWithContentDescription("끝 다음 달").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("시작 다음 달").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("끝 이전 달").performClick()
        composeRule.onNodeWithContentDescription("끝 이전 달").performClick()
        composeRule.onAllNodesWithText("2026년 9월 (9.1 ~ 9.30)").assertCountEquals(2)
        composeRule.onNodeWithText("CSV로 저장").performClick()
        assertEquals(-2, request?.fromMonthOffset)
        assertEquals(-2, request?.toMonthOffset)
    }

    @Test
    fun 전체를_고르면_두_장부를_함께_저장하고_기준일이_다르다고_알려준다() {
        show()
        composeRule.onNodeWithText("전체").performClick()
        composeRule.onNodeWithText("자기 월 기준일", substring = true).assertExists()
        composeRule.onNodeWithText("CSV로 저장").performClick()
        assertEquals(CsvExportRequest(listOf(shared, personal), 0, 0, "가계부_전체_2026-11.csv"), request)
    }
}
