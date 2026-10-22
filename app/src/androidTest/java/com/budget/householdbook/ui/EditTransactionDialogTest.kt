package com.budget.householdbook.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.ui.ledger.EditTransactionDialog
import com.budget.householdbook.ui.ledger.TransactionEdit
import com.budget.householdbook.ui.theme.HouseholdBookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class EditTransactionDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zone = ZoneOffset.ofHours(9)
    private val original = LocalDateTime.of(2026, 10, 15, 12, 30)

    private val tx = TransactionEntity(
        id = 7, remoteId = "r7", spaceId = "space_shared_default", type = TransactionType.EXPENSE,
        amount = 18_900, categoryId = 1, merchantOrTitle = "쿠팡", memo = "생필품",
        authorName = "엄마", transactionDate = original.toInstant(zone).toEpochMilli()
    )
    private val categories = listOf(
        CategoryEntity(id = 1, spaceId = "space_shared_default", name = "식비"),
        CategoryEntity(id = 2, spaceId = "space_shared_default", name = "생활용품")
    )

    private var saved: TransactionEdit? = null

    private fun show() {
        composeRule.setContent {
            HouseholdBookTheme {
                EditTransactionDialog(
                    tx = tx,
                    spaceCategories = categories,
                    onSave = { saved = it },
                    onDismiss = {},
                    zoneId = zone
                )
            }
        }
    }

    @Test
    fun 원래_값이_채워져_있고_이름과_금액을_고쳐_저장한다() {
        show()
        composeRule.onNodeWithText("2026년 10월 15일 (목)").assertExists()
        composeRule.onNodeWithText("지출 · 엄마님이 기록").assertExists()

        // 입력 칸은 라벨로 찾음 (내용을 지우면 값으로는 다시 찾을 수 없음)
        val title = composeRule.onNode(hasSetTextAction() and hasText("이름 (가게·내역)"))
        title.performTextClearance()
        title.performTextInput("쿠팡 로켓배송")
        val amount = composeRule.onNode(hasSetTextAction() and hasText("금액 (원)"))
        amount.performTextClearance()
        amount.performTextInput("21000")
        composeRule.onNodeWithText("21,000원").assertExists()
        composeRule.onNodeWithText("저장").performClick()

        val edit = saved!!
        assertEquals("쿠팡 로켓배송", edit.merchantOrTitle)
        assertEquals(21_000L, edit.amount)
        assertEquals(tx.transactionDate, edit.transactionDate)
        assertEquals(1L, edit.categoryId)
        assertEquals("생필품", edit.memo)
    }

    @Test
    fun 금액을_지우면_저장할_수_없다() {
        show()
        composeRule.onNode(hasSetTextAction() and hasText("금액 (원)")).performTextClearance()
        composeRule.onNodeWithText("저장").assertIsNotEnabled()
        assertNull(saved)
    }

    @Test
    fun 날짜를_바꾸면_시각은_그대로_두고_날짜만_바꾼다() {
        show()
        composeRule.onNodeWithText("2026년 10월 15일 (목)").performClick()
        composeRule.onNodeWithText("날짜 선택").assertExists()
        // 같은 달 3일 선택 (달력 날짜 칸은 'Saturday, October 3, 2026'처럼 전체 날짜로 읽힘 — 휴대폰 언어에 따라 다름)
        composeRule.onNode(
            (hasText("October 3, 2026", substring = true) or hasText("10월 3일", substring = true)) and hasClickAction()
        ).performClick()
        composeRule.onNodeWithText("확인").performClick()

        composeRule.onNodeWithText("2026년 10월 3일 (토)").assertExists()
        composeRule.onNodeWithText("저장").performClick()
        val expected = LocalDateTime.of(2026, 10, 3, 12, 30).toInstant(zone).toEpochMilli()
        assertEquals(expected, saved!!.transactionDate)
    }
}
