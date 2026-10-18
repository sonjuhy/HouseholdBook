package com.budget.householdbook

import com.budget.householdbook.core.common.BillingCycleCalculator
import com.budget.householdbook.data.ingestion.kbquick.KbQuickInquiryScraper
import com.budget.householdbook.data.ingestion.parser.FinancialParserEngine
import com.budget.householdbook.domain.model.IngestionSource
import com.budget.householdbook.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HouseholdBookCoreTest {

    @Test
    fun `월 기준일 기본값(1일) 및 사용자 지정일(25일) 기간 계산 검증`() {
        val baseDate = LocalDate.of(2026, 10, 18)

        // 1. 기본 매월 1일 ~ 말일
        val defaultPeriod = BillingCycleCalculator.calculatePeriod(
            baseDate = baseDate,
            billingStartDay = 1
        )
        assertEquals(LocalDate.of(2026, 10, 1), defaultPeriod.startDate)
        assertEquals(LocalDate.of(2026, 10, 31), defaultPeriod.endDateInclusive)
        assertEquals(31, defaultPeriod.totalDays)

        // 2. 월급일(25일) 기준일이고 오늘이 10월 18일인 경우 -> 9월 25일 ~ 10월 24일
        val salaryPeriod = BillingCycleCalculator.calculatePeriod(
            baseDate = baseDate,
            billingStartDay = 25
        )
        assertEquals(LocalDate.of(2026, 9, 25), salaryPeriod.startDate)
        assertEquals(LocalDate.of(2026, 10, 24), salaryPeriod.endDateInclusive)
    }

    @Test
    fun `KB 및 토스 금융 알림 파싱과 결제취소 및 내계좌이체 감지 검증`() {
        // 1. 일반 지출 푸시 알림
        val expenseDraft = FinancialParserEngine.parse(
            sourceType = IngestionSource.APP_NOTIFICATION,
            sourceAppName = "KB Pay(국민카드)",
            title = "KB국민카드 승인",
            body = "KB국민카드 24,000원 일시불 승인 (주)우아한형제들"
        )
        assertNotNull(expenseDraft)
        assertEquals(24_000L, expenseDraft!!.amount)
        assertEquals(TransactionType.EXPENSE, expenseDraft.transactionType)
        assertTrue(expenseDraft.merchant.contains("우아한형제들"))

        // 2. 승인 취소 문자 감지
        val cancelDraft = FinancialParserEngine.parse(
            sourceType = IngestionSource.SMS,
            sourceAppName = "문자(SMS)",
            title = "",
            body = "[Web발신] KB국민카드 18,900원 승인취소 쿠팡(로켓배송)"
        )
        assertNotNull(cancelDraft)
        assertEquals(18_900L, cancelDraft!!.amount)
        assertTrue(cancelDraft.isCancelAlert)

        // 3. 카드대금 자동납부 및 본인 실명 이체 -> TRANSFER(집계 제외) 분류
        val transferDraft = FinancialParserEngine.parse(
            sourceType = IngestionSource.APP_NOTIFICATION,
            sourceAppName = "토스(Toss)",
            title = "출금 안내",
            body = "출금 350,000원 KB국민카드대금 자동납부",
            userRealName = "홍길동"
        )
        assertNotNull(transferDraft)
        assertEquals(TransactionType.TRANSFER, transferDraft!!.transactionType)

        // 4. 광고 알림 무시
        val adDraft = FinancialParserEngine.parse(
            sourceType = IngestionSource.APP_NOTIFICATION,
            sourceAppName = "토스(Toss)",
            title = "(광고) 행운복권",
            body = "지금 최대 10,000원 받으세요"
        )
        assertNull(adDraft)
    }

    @Test
    fun `KB 무로그인 빠른조회 HTML 테이블 파싱 검증`() {
        val sampleHtml = """
            <html>
            <body>
                <table>
                    <tbody>
                        <tr>
                            <td>2026.10.18 12:30</td>
                            <td>42,500</td>
                            <td>0</td>
                            <td>하나로마트</td>
                        </tr>
                        <tr>
                            <td>2026.10.18 09:00</td>
                            <td>0</td>
                            <td>500,000</td>
                            <td>생활비입금</td>
                        </tr>
                    </tbody>
                </table>
            </body>
            </html>
        """.trimIndent()

        val scraper = KbQuickInquiryScraper()
        val parsed = scraper.parseKbQuickInquiryHtml(sampleHtml, "홍길동")
        assertEquals(2, parsed.size)
        assertEquals(42_500L, parsed[0].amount)
        assertEquals(TransactionType.EXPENSE, parsed[0].transactionType)
        assertEquals(500_000L, parsed[1].amount)
        assertEquals(TransactionType.INCOME, parsed[1].transactionType)
    }
}
