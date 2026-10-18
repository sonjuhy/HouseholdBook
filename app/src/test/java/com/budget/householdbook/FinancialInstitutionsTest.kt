package com.budget.householdbook

import com.budget.householdbook.data.ingestion.parser.FinancialParserEngine
import com.budget.householdbook.domain.model.FinancialInstitutions
import com.budget.householdbook.domain.model.IngestionSource
import com.budget.householdbook.domain.model.InstitutionGroup
import com.budget.householdbook.domain.model.QuickInquiryStatus
import com.budget.householdbook.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialInstitutionsTest {

    @Test
    fun `금융기관 ID와 앱 패키지는 서로 겹치지 않는다`() {
        val ids = FinancialInstitutions.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        val packages = FinancialInstitutions.ALL.flatMap { it.packageNames }
        assertEquals(packages.size, packages.toSet().size)
        assertFalse(FinancialInstitutions.OTHER_ID in ids)
    }

    @Test
    fun `시중·특수·인터넷·지방은행과 상호금융이 모두 들어 있다`() {
        val names = FinancialInstitutions.ALL.map { it.displayName }
        listOf(
            "KB국민은행", "신한은행", "우리은행", "하나은행", "SC제일은행", "한국씨티은행",
            "NH농협은행", "IBK기업은행", "KDB산업은행", "Sh수협은행",
            "카카오뱅크", "케이뱅크", "토스뱅크·토스",
            "BNK부산은행", "BNK경남은행", "iM뱅크(대구은행)", "광주은행", "전북은행", "제주은행",
            "새마을금고", "신협", "우체국"
        ).forEach { assertTrue(it, it in names) }
    }

    @Test
    fun `앱 패키지로 금융기관을 찾고 예전 신한 SOL페이 패키지 오류가 고쳐졌다`() {
        assertEquals("SHINHAN", FinancialInstitutions.byPackage("com.shinhan.sbanking")?.id)
        assertEquals("HANA", FinancialInstitutions.byPackage("com.hanabank.oqf")?.id)
        assertEquals("HANA", FinancialInstitutions.byPackage("com.kebhana.hanapush")?.id)
        assertEquals("SHINHANCARD", FinancialInstitutions.byPackage("com.shcard.smartpay")?.id)
        assertEquals("JEJU", FinancialInstitutions.byPackage("com.jejubank.smartnew")?.id)
        assertEquals(null, FinancialInstitutions.byPackage("com.shinhancard.smartshinhan"))
    }

    @Test
    fun `문자 본문은 긴 이름부터 비교해 카드와 은행을 구분한다`() {
        assertEquals("KBCARD", FinancialInstitutions.detectFromText("[Web발신] KB국민카드 18,900원 승인")?.id)
        assertEquals("KB", FinancialInstitutions.detectFromText("[KB]10/18 12:30 출금 5,000원")?.id)
        assertEquals("SHINHANCARD", FinancialInstitutions.detectFromText("신한카드(1234)승인 홍*동 9,900원")?.id)
        assertEquals("SHINHAN", FinancialInstitutions.detectFromText("신한은행 입금 300,000원")?.id)
        assertEquals("KBANK", FinancialInstitutions.detectFromText("[케이뱅크] 출금 1,000원")?.id)
        assertEquals(null, FinancialInstitutions.detectFromText("오늘 점심 12,000원"))
    }

    @Test
    fun `목록에 없는 금융 앱은 이름으로 그 외 금융 앱 처리`() {
        assertTrue(FinancialParserEngine.isSupportedFinancialPackage("com.kakaopay.app"))
        assertTrue(FinancialParserEngine.isSupportedFinancialPackage("com.kbstar.kbbank"))
        assertFalse(FinancialParserEngine.isSupportedFinancialPackage("com.kakao.talk"))
        assertEquals("우리은행", FinancialParserEngine.resolveAppName("com.wooribank.smart.npib"))
    }

    @Test
    fun `빠른조회 앱 연동은 KB만 지원이고 인터넷은행은 서비스 없음`() {
        val supported = FinancialInstitutions.ALL.filter { it.quickInquiry.status == QuickInquiryStatus.SUPPORTED_BETA }
        assertEquals(listOf("KB"), supported.map { it.id })
        FinancialInstitutions.ALL.filter { it.group == InstitutionGroup.INTERNET }
            .forEach { assertEquals(it.id, QuickInquiryStatus.NOT_OFFERED, it.quickInquiry.status) }
        FinancialInstitutions.ALL.filter { it.quickInquiry.status == QuickInquiryStatus.NOT_YET_SUPPORTED }
            .forEach {
                assertNotNull(it.id, it.quickInquiry.serviceName)
                assertNotNull(it.id, it.quickInquiry.registerPath)
            }
    }

    // ---- 은행별 알림·문자 문구 파싱 (실제 문구를 본뜬 예시) ----

    private fun parse(app: String, body: String, pkg: String? = null, title: String = "") =
        requireNotNull(
            FinancialParserEngine.parse(
                sourceType = if (pkg == null) IngestionSource.SMS else IngestionSource.APP_NOTIFICATION,
                sourceAppName = app,
                title = title,
                body = body,
                userRealName = "홍길동",
                sourcePackage = pkg
            )
        )

    @Test
    fun `신한은행 앱 입금 알림`() {
        val d = parse("신한은행", "[입금] 300,000원 김철수 | 110-***-123456 | 잔액 1,234,000원", pkg = "com.shinhan.sbanking")
        assertEquals("SHINHAN", d.institutionId)
        assertEquals(300_000L, d.amount)
        assertEquals(TransactionType.INCOME, d.transactionType)
        assertEquals("김철수", d.merchant)
    }

    @Test
    fun `우리은행 문자 출금`() {
        val d = parse("문자(15885000)", "[Web발신] 우리 10/18 12:30 1002-***-123456 출금 45,000원 이마트 잔액 500,000원")
        assertEquals(45_000L, d.amount)
        assertEquals(TransactionType.EXPENSE, d.transactionType)
        assertTrue(d.merchant, d.merchant.contains("이마트"))
        assertFalse(d.merchant, d.merchant.contains("잔액"))
    }

    @Test
    fun `NH농협 문자 출금은 농협 이름이 가맹점에 남지 않는다`() {
        val d = parse("문자(15882100)", "[Web발신] 농협 출금12,500원 10/18 12:30 352-****-1234-56 GS25역삼점 잔액88,000원")
        assertEquals("NH", d.institutionId)
        assertEquals(12_500L, d.amount)
        assertEquals("GS25역삼점", d.merchant)
    }

    @Test
    fun `카카오뱅크 앱 출금 알림`() {
        val d = parse("카카오뱅크", "체크카드 결제 8,900원 배달의민족", pkg = "com.kakaobank.channel", title = "출금")
        assertEquals("KAKAOBANK", d.institutionId)
        assertEquals(TransactionType.EXPENSE, d.transactionType)
        assertTrue(d.merchant, d.merchant.contains("배달의민족"))
    }

    @Test
    fun `하나은행 카드대금 출금은 이체로 분류된다`() {
        val d = parse("하나은행", "출금 350,000원 하나카드대금", pkg = "com.hanabank.oqf")
        assertEquals("HANA", d.institutionId)
        assertEquals(TransactionType.TRANSFER, d.transactionType)
    }

    @Test
    fun `새마을금고 본인 이름 입금은 내 계좌 이체로 분류된다`() {
        val d = parse("MG더뱅킹", "입금 200,000원 홍길동", pkg = "com.smg.spbs")
        assertEquals("MG", d.institutionId)
        assertEquals(TransactionType.TRANSFER, d.transactionType)
    }

    @Test
    fun `금융사를 알 수 없는 문자는 그 외 금융 문자로 처리`() {
        val d = parse("문자(1544xxxx)", "[Web발신] 승인 15,000원 올리브영")
        assertEquals(FinancialInstitutions.OTHER_ID, d.institutionId)
    }
}
