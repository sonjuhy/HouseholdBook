package com.budget.householdbook.domain.model

/**
 * 금융기관 분류 (설정 화면 목록의 묶음 순서)
 */
enum class InstitutionGroup(val displayName: String) {
    COMMERCIAL("시중은행"),
    SPECIAL("특수은행"),
    INTERNET("인터넷은행"),
    REGIONAL("지방은행"),
    COOPERATIVE("상호금융·우체국"),
    CARD_PAY("카드·간편결제")
}

/**
 * 로그인 없이 계좌 거래내역을 조회하는 은행 서비스(빠른조회 등)의 앱 지원 상태
 * - SUPPORTED_BETA: 앱에서 불러오기 가능 [Beta]
 * - NOT_YET_SUPPORTED: 은행은 서비스를 제공하지만 앱 연동은 아직 준비 중
 * - NOT_OFFERED: 은행이 이런 서비스를 제공하지 않음 (알림·문자 수집으로만 가져옴)
 */
enum class QuickInquiryStatus { SUPPORTED_BETA, NOT_YET_SUPPORTED, NOT_OFFERED }

data class QuickInquiryInfo(
    val status: QuickInquiryStatus,
    /** 은행에서 부르는 서비스 이름 (예: 빠른조회, 스피드계좌조회) */
    val serviceName: String? = null,
    /** 은행 인터넷뱅킹에서 미리 등록하는 경로 */
    val registerPath: String? = null
)

/**
 * 결제·입출금 알림을 읽어올 수 있는 금융기관
 * @param packageNames Google Play 앱 패키지 이름 (2026-10 Play 스토어에서 확인)
 * @param textAliases 문자·알림 본문에서 이 금융기관을 알아보는 단어 (짧고 흔한 단어는 오인식이 생기므로 넣지 않음)
 */
data class FinancialInstitution(
    val id: String,
    val displayName: String,
    val group: InstitutionGroup,
    val packageNames: List<String>,
    val textAliases: List<String>,
    val quickInquiry: QuickInquiryInfo = QuickInquiryInfo(QuickInquiryStatus.NOT_OFFERED)
)

/**
 * 국내 은행·카드 금융기관 목록과 알림/문자 → 금융기관 판별
 */
object FinancialInstitutions {

    /** 목록에 없는 금융 앱(이름에 bank·card·pay 등이 들어간 앱)과 금융사를 알 수 없는 문자 */
    const val OTHER_ID = "OTHER"
    const val OTHER_DISPLAY_NAME = "그 외 금융 앱·문자"

    private fun notYet(serviceName: String, registerPath: String) =
        QuickInquiryInfo(QuickInquiryStatus.NOT_YET_SUPPORTED, serviceName, registerPath)

    val ALL: List<FinancialInstitution> = listOf(
        // 시중은행
        FinancialInstitution(
            id = "KB", displayName = "KB국민은행", group = InstitutionGroup.COMMERCIAL,
            packageNames = listOf("com.kbstar.kbbank"),
            textAliases = listOf("KB국민은행", "국민은행", "KB스타뱅킹", "KB국민", "[KB]"),
            quickInquiry = QuickInquiryInfo(QuickInquiryStatus.SUPPORTED_BETA, "빠른조회", "KB국민은행 인터넷뱅킹 > 빠른조회 > 빠른조회 서비스 등록")
        ),
        FinancialInstitution(
            id = "SHINHAN", displayName = "신한은행", group = InstitutionGroup.COMMERCIAL,
            packageNames = listOf("com.shinhan.sbanking"),
            textAliases = listOf("신한은행", "신한SOL", "슈퍼SOL", "[신한]"),
            quickInquiry = notYet("간편조회서비스", "신한은행 인터넷뱅킹 > 고객센터 > 회원서비스 > 간편조회서비스 > 계좌등록")
        ),
        FinancialInstitution(
            id = "WOORI", displayName = "우리은행", group = InstitutionGroup.COMMERCIAL,
            packageNames = listOf("com.wooribank.smart.npib"),
            textAliases = listOf("우리은행", "우리WON", "[우리]"),
            quickInquiry = notYet("스피드계좌조회", "우리은행 인터넷뱅킹 > 빠른메뉴 > 스피드계좌조회")
        ),
        FinancialInstitution(
            id = "HANA", displayName = "하나은행", group = InstitutionGroup.COMMERCIAL,
            packageNames = listOf("com.hanabank.oqf", "com.kebhana.hanapush"),
            textAliases = listOf("하나은행", "하나원큐", "[하나]"),
            quickInquiry = notYet("빠른조회서비스", "하나은행 인터넷뱅킹 > 빠른조회서비스 > 빠른조회계좌설정")
        ),
        FinancialInstitution(
            id = "SC", displayName = "SC제일은행", group = InstitutionGroup.COMMERCIAL,
            packageNames = listOf("com.scbank.ma30"),
            textAliases = listOf("SC제일은행", "SC제일", "제일은행"),
            quickInquiry = notYet("스피드조회", "SC제일은행 인터넷뱅킹 > 서비스 및 설정 > 통장관리 > 스피드조회등록")
        ),
        FinancialInstitution(
            id = "CITI", displayName = "한국씨티은행", group = InstitutionGroup.COMMERCIAL,
            packageNames = listOf("kr.co.citibank.citimobile"),
            textAliases = listOf("한국씨티은행", "씨티은행", "씨티"),
            quickInquiry = notYet("간편서비스", "씨티은행 인터넷뱅킹 > 거래내역조회 > 간편서비스")
        ),
        // 특수은행
        FinancialInstitution(
            id = "NH", displayName = "NH농협은행", group = InstitutionGroup.SPECIAL,
            packageNames = listOf("nh.smart.banking", "com.nonghyup.nhallonebank"),
            textAliases = listOf("NH농협은행", "농협은행", "NH농협", "NH올원", "[농협]", "농협"),
            quickInquiry = notYet("빠른조회", "농협 인터넷뱅킹 > 빠른조회 > 빠른조회 등록하기")
        ),
        FinancialInstitution(
            id = "IBK", displayName = "IBK기업은행", group = InstitutionGroup.SPECIAL,
            packageNames = listOf("com.ibk.android.ionebank"),
            textAliases = listOf("IBK기업은행", "기업은행", "i-ONE", "IBK"),
            quickInquiry = notYet("빠른서비스", "기업은행 인터넷뱅킹 > 계좌조회 > 빠른서비스 신청")
        ),
        FinancialInstitution(
            id = "KDB", displayName = "KDB산업은행", group = InstitutionGroup.SPECIAL,
            packageNames = listOf("co.kr.kdb.android.smartkdb"),
            textAliases = listOf("KDB산업은행", "산업은행", "KDB"),
            quickInquiry = notYet("빠른조회", "산업은행 인터넷뱅킹 > 뱅킹관리 > 고객정보관리 > 조회계좌 등록")
        ),
        FinancialInstitution(
            id = "SUHYUP", displayName = "Sh수협은행", group = InstitutionGroup.SPECIAL,
            packageNames = listOf("com.suhyup.psmb", "com.suhyup.pesmb"),
            textAliases = listOf("Sh수협은행", "수협은행", "수협"),
            quickInquiry = notYet("간편조회서비스", "수협은행 인터넷뱅킹 > My정보 > 계좌정보관리 > 간편조회서비스")
        ),
        // 인터넷은행 (로그인 없는 웹 조회 서비스 없음 → 알림 수집)
        FinancialInstitution(
            id = "KAKAOBANK", displayName = "카카오뱅크", group = InstitutionGroup.INTERNET,
            packageNames = listOf("com.kakaobank.channel"),
            textAliases = listOf("카카오뱅크")
        ),
        FinancialInstitution(
            id = "KBANK", displayName = "케이뱅크", group = InstitutionGroup.INTERNET,
            packageNames = listOf("com.kbankwith.smartbank"),
            textAliases = listOf("케이뱅크", "K뱅크")
        ),
        FinancialInstitution(
            id = "TOSS", displayName = "토스뱅크·토스", group = InstitutionGroup.INTERNET,
            packageNames = listOf("viva.republica.toss"),
            textAliases = listOf("토스뱅크", "토스")
        ),
        // 지방은행
        FinancialInstitution(
            id = "BUSAN", displayName = "BNK부산은행", group = InstitutionGroup.REGIONAL,
            packageNames = listOf("kr.co.busanbank.mbp"),
            textAliases = listOf("BNK부산은행", "부산은행"),
            quickInquiry = notYet("빠른조회서비스", "부산은행 인터넷뱅킹 > 빠른조회서비스 > 빠른조회계좌 등록")
        ),
        FinancialInstitution(
            id = "KYONGNAM", displayName = "BNK경남은행", group = InstitutionGroup.REGIONAL,
            packageNames = listOf("com.knb.psb"),
            textAliases = listOf("BNK경남은행", "경남은행"),
            quickInquiry = notYet("빠른서비스", "경남은행 영업점 또는 인터넷뱅킹에서 신청")
        ),
        FinancialInstitution(
            id = "IM", displayName = "iM뱅크(대구은행)", group = InstitutionGroup.REGIONAL,
            packageNames = listOf("kr.co.dgb.dgbm"),
            textAliases = listOf("iM뱅크", "아이엠뱅크", "대구은행", "DGB"),
            quickInquiry = notYet("안전계좌조회", "iM뱅크 인터넷뱅킹 > 안전계좌조회 > 안전계좌 신청")
        ),
        FinancialInstitution(
            id = "KWANGJU", displayName = "광주은행", group = InstitutionGroup.REGIONAL,
            packageNames = listOf("com.kjbank.asb.pbanking"),
            textAliases = listOf("광주은행"),
            quickInquiry = notYet("빠른서비스", "영업점 신청 후 광주은행 인터넷뱅킹 > 마이뱅킹관리 > 빠른서비스 사용계좌관리")
        ),
        FinancialInstitution(
            id = "JEONBUK", displayName = "전북은행", group = InstitutionGroup.REGIONAL,
            packageNames = listOf("kr.co.jbbank.privatebank"),
            textAliases = listOf("전북은행"),
            quickInquiry = notYet("빠른서비스", "전북은행 영업점 또는 인터넷뱅킹에서 신청")
        ),
        FinancialInstitution(
            id = "JEJU", displayName = "제주은행", group = InstitutionGroup.REGIONAL,
            packageNames = listOf("com.jejubank.smartnew"),
            textAliases = listOf("제주은행"),
            quickInquiry = notYet("바로바로서비스", "제주은행 인터넷뱅킹 > 사용자관리 > 바로바로서비스 신청")
        ),
        // 상호금융·우체국
        FinancialInstitution(
            id = "NHCOK", displayName = "지역농축협(NH콕뱅크)", group = InstitutionGroup.COOPERATIVE,
            packageNames = listOf("nh.smart.nhcok"),
            textAliases = listOf("콕뱅크", "농축협")
        ),
        FinancialInstitution(
            id = "MG", displayName = "새마을금고", group = InstitutionGroup.COOPERATIVE,
            packageNames = listOf("com.smg.spbs"),
            textAliases = listOf("새마을금고", "MG더뱅킹"),
            quickInquiry = notYet("빠른조회", "새마을금고 인터넷뱅킹 > 뱅킹관리 > 계좌관리 > 즉시조회계좌관리")
        ),
        FinancialInstitution(
            id = "CU", displayName = "신협", group = InstitutionGroup.COOPERATIVE,
            packageNames = listOf("kr.co.cu.onbank"),
            textAliases = listOf("신협"),
            quickInquiry = notYet("조회전용서비스", "신협 인터넷뱅킹 > 조회전용서비스 > 신규등록")
        ),
        FinancialInstitution(
            id = "EPOST", displayName = "우체국", group = InstitutionGroup.COOPERATIVE,
            packageNames = listOf("com.epost.psf.sdsi"),
            textAliases = listOf("우체국"),
            quickInquiry = notYet("간편조회", "우체국 인터넷뱅킹 > 예금간편서비스 > 간편조회 계좌등록")
        ),
        // 카드·간편결제
        FinancialInstitution(
            id = "KBCARD", displayName = "KB국민카드(KB Pay)", group = InstitutionGroup.CARD_PAY,
            packageNames = listOf("com.kbcard.cxh.appcard"),
            textAliases = listOf("KB국민카드", "KB Pay", "KB카드")
        ),
        FinancialInstitution(
            id = "SHINHANCARD", displayName = "신한카드(SOL페이)", group = InstitutionGroup.CARD_PAY,
            packageNames = listOf("com.shcard.smartpay"),
            textAliases = listOf("신한카드", "SOL페이")
        ),
        FinancialInstitution(
            id = "HYUNDAICARD", displayName = "현대카드", group = InstitutionGroup.CARD_PAY,
            packageNames = listOf("com.hyundaicard.appcard"),
            textAliases = listOf("현대카드")
        )
    )

    /** 처음에는 모든 금융기관 + 그 외 금융 앱을 읽음 (이전 버전과 같은 동작) */
    val DEFAULT_ENABLED_IDS: Set<String> = ALL.map { it.id }.toSet() + OTHER_ID

    private val byId = ALL.associateBy { it.id }
    private val byPackageName = ALL.flatMap { inst -> inst.packageNames.map { it to inst } }.toMap()

    /** 긴 별칭부터 비교해야 "KB국민카드"가 "KB국민"(은행)보다 먼저 잡힘 */
    private val aliasesLongestFirst: List<Pair<String, FinancialInstitution>> =
        ALL.flatMap { inst -> inst.textAliases.map { it to inst } }.sortedByDescending { it.first.length }

    fun byId(id: String): FinancialInstitution? = byId[id]

    fun byPackage(packageName: String): FinancialInstitution? = byPackageName[packageName]

    /** 문자·알림 본문에 들어있는 금융기관 이름으로 판별 (못 찾으면 null) */
    fun detectFromText(text: String): FinancialInstitution? =
        aliasesLongestFirst.firstOrNull { (alias, _) -> text.contains(alias, ignoreCase = true) }?.second

    /** 목록에 없지만 이름으로 보아 금융·결제 앱으로 보이는 패키지 (그 외 금융 앱으로 처리) */
    fun looksLikeFinancialPackage(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return FINANCIAL_PACKAGE_HINTS.any { lower.contains(it) }
    }

    private val FINANCIAL_PACKAGE_HINTS = listOf("bank", "card", "pay", "toss")
}
