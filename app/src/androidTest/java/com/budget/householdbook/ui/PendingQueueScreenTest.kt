package com.budget.householdbook.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.data.local.entity.PendingTransactionEntity
import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.domain.model.IngestionSource
import com.budget.householdbook.domain.model.QueueKind
import com.budget.householdbook.domain.model.SpaceType
import com.budget.householdbook.domain.model.SplitApprovalItem
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.ui.queue.PendingQueueScreen
import com.budget.householdbook.ui.theme.HouseholdBookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 대기내역 화면 핵심 흐름 UI 테스트 (ViewModel 없이 상태·콜백만 주입)
 */
@RunWith(AndroidJUnit4::class)
class PendingQueueScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sharedSpace = LedgerSpaceEntity(
        id = AppDatabase.SPACE_ID_SHARED, name = "우리집 공통 생활비", type = SpaceType.SHARED
    )
    private val personalSpace = LedgerSpaceEntity(
        id = AppDatabase.SPACE_ID_PERSONAL, name = "내 개인 용돈", type = SpaceType.PERSONAL
    )
    private val categories = listOf(
        CategoryEntity(id = 1, spaceId = sharedSpace.id, name = "식비", emoji = "🍔"),
        CategoryEntity(id = 2, spaceId = sharedSpace.id, parentId = 1, name = "식재료/장보기", emoji = "🥬"),
        CategoryEntity(id = 3, spaceId = personalSpace.id, name = "식음료/간식", emoji = "☕"),
        CategoryEntity(id = 4, spaceId = personalSpace.id, parentId = 3, name = "커피/디저트", emoji = "🧋")
    )

    private fun pending(
        id: Long,
        merchant: String,
        amount: Long,
        kind: QueueKind = QueueKind.NORMAL,
        type: TransactionType = TransactionType.EXPENSE
    ) = PendingTransactionEntity(
        id = id,
        sourceType = IngestionSource.APP_NOTIFICATION,
        sourceAppName = "KB Pay(국민카드)",
        rawText = "$merchant ${amount}원",
        parsedAmount = amount,
        parsedType = type,
        parsedMerchant = merchant,
        occurredAt = 1_790_000_000_000L,
        queueKind = kind,
        suggestedSpaceId = sharedSpace.id,
        suggestedCategoryId = 1,
        suggestedSubCategoryId = 2,
        dedupHash = "hash-$id"
    )

    private fun state(vararg items: PendingTransactionEntity) = HouseholdUiState(
        spaces = listOf(sharedSpace, personalSpace),
        currentSpace = sharedSpace,
        allCategories = categories,
        pendingQueue = items.toList()
    )

    private class Calls {
        var approveAll = 0
        var split: Triple<Long, String, List<SplitApprovalItem>>? = null
        var link: Pair<Long, Long>? = null
    }

    private fun setScreen(uiState: HouseholdUiState, candidates: List<TransactionEntity> = emptyList()): Calls {
        val calls = Calls()
        composeRule.setContent {
            HouseholdBookTheme {
                PendingQueueScreen(
                    uiState = uiState,
                    onApproveSingle = { _, _, _, _, _, _, _, _ -> },
                    onApproveSplit = { id, merchant, splits -> calls.split = Triple(id, merchant, splits) },
                    onApplyMatchedCancel = {},
                    onLoadCancelCandidates = { _, onLoaded -> onLoaded(candidates) },
                    onLinkCancelTarget = { pendingId, txId -> calls.link = pendingId to txId },
                    onDismissItem = { _, _ -> },
                    onApproveAll = { calls.approveAll++ },
                    onSimulateNotification = {},
                    onRunKbQuickInquiryBeta = {}
                )
            }
        }
        return calls
    }

    @Test
    fun 일괄_승인_버튼은_대기_내역이_있을_때_콜백을_호출한다() {
        val calls = setScreen(state(pending(1, "이마트에브리데이", 52_000)))

        composeRule.onNodeWithText("확인 대기 1건").assertExists()
        composeRule.onNodeWithText("일괄 승인").performClick()

        assertEquals(1, calls.approveAll)
    }

    @Test
    fun 분할_등록은_공통과_개인_금액의_합이_원본과_같다() {
        val calls = setScreen(state(pending(7, "이마트에브리데이", 50_000)))

        composeRule.onNodeWithText("분할").performScrollTo().performClick()
        composeRule.onNodeWithText("① 공통 생활비 금액 (원)").performTextReplacement("40000")
        composeRule.onNodeWithText("② 내 개인 용돈 자동 배분액: 10,000원").assertExists()
        composeRule.onNodeWithText("분할 저장 완료").performClick()

        composeRule.waitForIdle()
        val (pendingId, _, splits) = requireNotNull(calls.split)
        assertEquals(7L, pendingId)
        assertEquals(listOf(40_000L, 10_000L), splits.map { it.amount })
        assertEquals(listOf(sharedSpace.id, personalSpace.id), splits.map { it.spaceId })
        assertEquals(50_000L, splits.sumOf { it.amount })
    }

    @Test
    fun 미매칭_취소는_원본_결제를_골라야_반영할_수_있다() {
        val candidate = TransactionEntity(
            id = 42,
            remoteId = "r-42",
            spaceId = sharedSpace.id,
            type = TransactionType.EXPENSE,
            amount = 52_000,
            categoryId = 1,
            merchantOrTitle = "올리브영 강남점",
            transactionDate = 1_789_000_000_000L
        )
        val calls = setScreen(
            state(pending(9, "신한카드 올리브영", 33_000, QueueKind.CANCEL_UNMATCHED, TransactionType.TRANSFER)),
            candidates = listOf(candidate)
        )

        composeRule.onNodeWithText("원본 결제 선택").performScrollTo().performClick()
        composeRule.onNodeWithText("취소된 원본 결제 선택").assertExists()
        composeRule.onNodeWithText("부분 취소").assertExists()
        composeRule.onNodeWithText("취소 반영").assertIsNotEnabled()

        composeRule.onNodeWithText("올리브영 강남점").performClick()
        composeRule.onNodeWithText("취소 반영").assertIsEnabled().performClick()

        composeRule.waitForIdle()
        assertEquals(9L to 42L, calls.link)
        assertTrue(composeRule.onAllNodesWithTextCount("취소된 원본 결제 선택") == 0)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
