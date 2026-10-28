package com.budget.householdbook.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.domain.model.SpaceType
import com.budget.householdbook.domain.model.groupedForSpace
import com.budget.householdbook.ui.settings.CategoryManagementSection
import com.budget.householdbook.ui.theme.HouseholdBookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryManagementSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val shared = AppDatabase.SPACE_ID_SHARED
    private val personal = AppDatabase.SPACE_ID_PERSONAL
    private val spaces = listOf(
        LedgerSpaceEntity(id = shared, name = "공통 생활비", type = SpaceType.SHARED, billingStartDay = 1, targetBudgetAmount = 0),
        LedgerSpaceEntity(id = personal, name = "내 용돈", type = SpaceType.PERSONAL, billingStartDay = 1, targetBudgetAmount = 0)
    )
    private val categories = listOf(
        CategoryEntity(id = 1, spaceId = shared, name = "장보기"),
        CategoryEntity(id = 10, spaceId = personal, name = "취미"),
        CategoryEntity(id = 11, spaceId = personal, parentId = 10, name = "게임")
    )

    private val added = mutableListOf<Pair<String, String>>()
    private val deleted = mutableListOf<Long>()

    private fun show(initialSpaceId: String = shared) {
        composeRule.setContent {
            HouseholdBookTheme {
                CategoryManagementSection(
                    spaces = spaces,
                    initialSpaceId = initialSpaceId,
                    categoryGroupsBySpace = spaces.associate { it.id to categories.groupedForSpace(it.id) },
                    onAddCategory = { spaceId, _, _, name, _, _ -> added += spaceId to name },
                    onUpdateCategory = {},
                    onDeleteCategory = { deleted += it }
                )
            }
        }
    }

    @Test
    fun 공용과_개인_탭을_바꾸면_그_장부의_분류만_보인다() {
        show()
        composeRule.onNodeWithText("장보기", substring = true).assertExists()
        composeRule.onNodeWithText("취미", substring = true).assertDoesNotExist()

        composeRule.onNodeWithText("개인 분류").performClick()

        composeRule.onNodeWithText("취미", substring = true).assertExists()
        composeRule.onNodeWithText("게임", substring = true).assertExists()
        composeRule.onNodeWithText("장보기", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("내 용돈 장부에서만 쓰는 분류예요", substring = true).assertExists()
    }

    @Test
    fun 개인_탭에서_추가하고_지우면_개인_분류에만_반영된다() {
        show()
        composeRule.onNodeWithText("개인 분류").performClick()

        composeRule.onNodeWithText("+ 대분류 추가").performClick()
        composeRule.onNodeWithText("개인 대분류 추가").assertExists()
        composeRule.onNode(hasSetTextAction() and hasText("대분류 이름")).performTextInput("운동")
        composeRule.onNodeWithText("추가").performClick()

        composeRule.onNodeWithContentDescription("게임 삭제").performClick()

        assertEquals(listOf(personal to "운동"), added)
        assertEquals(listOf(11L), deleted)
    }

    @Test
    fun 처음에는_지금_보고_있는_장부의_분류를_보여준다() {
        show(initialSpaceId = personal)
        composeRule.onNodeWithText("취미", substring = true).assertExists()
        composeRule.onNodeWithText("장보기", substring = true).assertDoesNotExist()
    }
}
