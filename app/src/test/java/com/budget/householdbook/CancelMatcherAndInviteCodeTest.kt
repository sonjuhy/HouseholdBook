package com.budget.householdbook

import com.budget.householdbook.core.common.BillingCycleCalculator
import com.budget.householdbook.data.ingestion.parser.CancelMatcher
import com.budget.householdbook.data.ingestion.parser.FinancialParserEngine
import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.data.sync.RemoteSyncManager
import com.budget.householdbook.domain.model.IngestionSource
import com.budget.householdbook.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CancelMatcherAndInviteCodeTest {

    private fun tx(id: Long, merchant: String, type: TransactionType = TransactionType.EXPENSE, canceled: Boolean = false) =
        TransactionEntity(
            id = id,
            remoteId = "r$id",
            spaceId = "s",
            type = type,
            amount = 18_900L,
            categoryId = 1L,
            merchantOrTitle = merchant,
            isCanceled = canceled,
            transactionDate = 0L
        )

    @Test
    fun `취소 문자는 가맹점명이 비슷한 원본 지출과만 매칭된다`() {
        val draft = FinancialParserEngine.parse(
            sourceType = IngestionSource.SMS,
            sourceAppName = "문자(SMS)",
            title = "",
            body = "[Web발신] KB국민카드 18,900원 승인취소 쿠팡(로켓배송)"
        )!!
        val candidates = listOf(tx(1, "이마트 트레이더스"), tx(2, "쿠팡(로켓배송)"))

        assertEquals(2L, CancelMatcher.findBestMatch(draft.merchant, candidates)?.id)
    }

    @Test
    fun `가맹점명이 다르면 금액이 같아도 매칭하지 않는다`() {
        val candidates = listOf(tx(1, "이마트 트레이더스"), tx(2, "스타벅스"))
        assertNull(CancelMatcher.findBestMatch("쿠팡(로켓배송)", candidates))
    }

    @Test
    fun `이미 취소된 거래나 수입·이체는 취소 매칭 대상이 아니다`() {
        val candidates = listOf(
            tx(1, "쿠팡", canceled = true),
            tx(2, "쿠팡", type = TransactionType.INCOME),
            tx(3, "쿠팡", type = TransactionType.TRANSFER)
        )
        assertNull(CancelMatcher.findBestMatch("쿠팡", candidates))
    }

    @Test
    fun `초대번호는 고정값이 아닌 추측하기 어려운 무작위 코드로 발급된다`() {
        val codes = (1..200).map { RemoteSyncManager.generateInviteCode() }
        codes.forEach { assertTrue(it, Regex("""HB-[A-Z2-9]{4}-[A-Z2-9]{4}""").matches(it)) }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.none { it == "HB-2026" })
    }

    @Test
    fun `발급된 초대번호는 모두 서버 규칙 형식을 통과하고 예전 고정값은 통과하지 못한다`() {
        repeat(1_000) {
            val code = RemoteSyncManager.generateInviteCode()
            assertTrue(code, RemoteSyncManager.isValidInviteCode(code))
        }
        // 발급 문자 31자가 모두 규칙 정규식에 포함되는지 (firestore.rules와 형식이 어긋나면 방 생성이 거부됨)
        "ABCDEFGHJKMNPQRSTUVWXYZ23456789".forEach { c ->
            assertTrue("$c", RemoteSyncManager.isValidInviteCode("HB-${c}AAA-AAA$c"))
        }
        listOf("HB-2026", "hb-abcd-efgh", "HB-ABCD-EFG0", "HB-ABCD-EFGI", "HB-ABCD-EFGO", "HB-ABCD-EFGL", "HB-ABCD-EFG1", "", null)
            .forEach { assertTrue("$it", !RemoteSyncManager.isValidInviteCode(it)) }
    }

    @Test
    fun `초대장 문서 ID는 초대번호와 소문자 이메일로 만든다`() {
        assertEquals(
            "HB-ABCD-EFGH__partner@gmail.com",
            RemoteSyncManager.invitationDocId("HB-ABCD-EFGH", " Partner@Gmail.com ")
        )
    }

    @Test
    fun `기준일 28일은 2월에도 정상적으로 한 달 주기를 만든다`() {
        val period = BillingCycleCalculator.calculatePeriod(
            baseDate = LocalDate.of(2027, 3, 10),
            billingStartDay = 28
        )
        assertEquals(LocalDate.of(2027, 2, 28), period.startDate)
        assertEquals(LocalDate.of(2027, 3, 27), period.endDateInclusive)

        val previous = BillingCycleCalculator.calculatePeriod(
            baseDate = LocalDate.of(2027, 3, 10),
            billingStartDay = 28,
            monthOffset = -1
        )
        assertEquals(previous.endExclusiveMillis, period.startMillis)
        assertNotEquals(previous.startDate, period.startDate)
    }
}
