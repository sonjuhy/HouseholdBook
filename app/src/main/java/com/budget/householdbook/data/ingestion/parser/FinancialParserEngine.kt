package com.budget.householdbook.data.ingestion.parser

import com.budget.householdbook.domain.model.FinancialInstitution
import com.budget.householdbook.domain.model.FinancialInstitutions
import com.budget.householdbook.domain.model.IngestionSource
import com.budget.householdbook.domain.model.TransactionType
import java.security.MessageDigest

/**
 * 알림/문자/빠른조회로부터 1차 파싱된 거래 초안 데이터
 */
data class ParsedNotificationDraft(
    val sourceType: IngestionSource,
    val sourceAppName: String,
    val rawText: String,
    val amount: Long,
    val transactionType: TransactionType,
    val merchant: String,
    val isCancelAlert: Boolean,
    val occurredAt: Long,
    val dedupHash: String,
    /** 판별된 금융기관 ID ([FinancialInstitutions], 모르면 OTHER) */
    val institutionId: String = FinancialInstitutions.OTHER_ID
)

/**
 * 시중·인터넷·지방은행, 상호금융, 카드사 앱 알림 및 SMS 파서 +
 * 내 계좌 이체/카드대금 출금(TRANSFER) 및 결제 취소(Cancel) 감지 엔진
 * - 지원 금융기관 목록과 앱 패키지는 [FinancialInstitutions]에서 관리
 */
object FinancialParserEngine {

    private val AMOUNT_REGEX = Regex("""([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{3,})\s*원""")
    private val CANCEL_KEYWORDS = listOf("취소", "승인취소", "결제취소", "환불")
    private val INCOME_KEYWORDS = listOf("입금", "받았어요", "충전", "급여", "월급", "이자")
    private val TRANSFER_KEYWORDS = listOf(
        "카드대금",
        "결제대금출금",
        "자동납부",
        "내계좌",
        "내 계좌",
        "본인계좌",
        "머니충전",
        "계좌이체(본인)"
    )

    fun isSupportedFinancialPackage(packageName: String): Boolean =
        FinancialInstitutions.byPackage(packageName) != null ||
            FinancialInstitutions.looksLikeFinancialPackage(packageName)

    fun resolveAppName(packageName: String): String =
        FinancialInstitutions.byPackage(packageName)?.displayName
            ?: packageName.substringAfterLast('.').uppercase()

    /**
     * 앱 패키지가 있으면 패키지로, 없으면(문자) 본문의 금융기관 이름으로 판별
     */
    fun detectInstitution(sourcePackage: String?, sourceAppName: String, text: String): FinancialInstitution? =
        sourcePackage?.let { FinancialInstitutions.byPackage(it) }
            ?: FinancialInstitutions.detectFromText("$sourceAppName $text")

    /**
     * 알림 또는 SMS 원문을 파싱하여 [ParsedNotificationDraft]를 반환합니다.
     * 금액이 없는 단순 광고성 알림은 null을 반환하여 필터링합니다.
     */
    fun parse(
        sourceType: IngestionSource,
        sourceAppName: String,
        title: String,
        body: String,
        occurredAt: Long = System.currentTimeMillis(),
        userRealName: String = "홍길동",
        sourcePackage: String? = null
    ): ParsedNotificationDraft? {
        val combinedText = listOf(title.trim(), body.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replace(Regex("""\s+"""), " ")

        // 광고성 메시지 제외
        if (combinedText.contains("(광고)") || combinedText.contains("[광고]")) {
            return null
        }

        // 금액 추출 (예: "24,000원" -> 24000L)
        val amountMatch = AMOUNT_REGEX.find(combinedText) ?: return null
        val rawNumber = amountMatch.groupValues[1].replace(",", "")
        val parsedAmount = rawNumber.toLongOrNull() ?: return null
        if (parsedAmount <= 0L) return null

        // 취소 알림 여부 판단
        val isCancel = CANCEL_KEYWORDS.any { combinedText.contains(it) }

        // 내 계좌 간 이체 / 카드대금 자동납부 여부 판단
        val isTransferByKeyword = TRANSFER_KEYWORDS.any { combinedText.contains(it) }
        val isSelfTransfer = userRealName.isNotBlank() &&
            combinedText.contains(userRealName) &&
            (combinedText.contains("입금") || combinedText.contains("출금") || combinedText.contains("이체"))

        // 거래 유형 결정
        val transactionType = when {
            isTransferByKeyword || isSelfTransfer -> TransactionType.TRANSFER
            INCOME_KEYWORDS.any { combinedText.contains(it) } && !combinedText.contains("출금") -> TransactionType.INCOME
            else -> TransactionType.EXPENSE
        }

        // 가맹점명 / 적요 추출 (판별된 금융기관 이름은 가맹점에서 제외)
        val institution = detectInstitution(sourcePackage, sourceAppName, combinedText)
        val merchant = extractMerchant(combinedText, amountMatch.value, sourceAppName, institution)

        // 1분 단위 타임버킷 기반 중복 방지 해시 (푸시 알림과 SMS가 동시에 올 때 중복 차단)
        val minuteBucket = occurredAt / 60_000L
        val dedupHash = sha256("$parsedAmount|$merchant|$isCancel|$minuteBucket")

        return ParsedNotificationDraft(
            sourceType = sourceType,
            sourceAppName = sourceAppName,
            rawText = combinedText,
            amount = parsedAmount,
            transactionType = transactionType,
            merchant = merchant,
            isCancelAlert = isCancel,
            occurredAt = occurredAt,
            dedupHash = dedupHash,
            institutionId = institution?.id ?: FinancialInstitutions.OTHER_ID
        )
    }

    private fun extractMerchant(
        fullText: String,
        matchedAmountToken: String,
        sourceAppName: String,
        institution: FinancialInstitution?
    ): String {
        // 금융기관 이름은 긴 것부터 지워야 "KB국민카드"가 "KB국민" 조각으로 남지 않음
        val institutionNames = (institution?.let { it.textAliases + it.displayName }.orEmpty() + sourceAppName)
            .filter { it.isNotBlank() }
            .sortedByDescending { it.length }

        var cleaned = fullText
            .replace("[Web발신]", "")
            .replace(matchedAmountToken, "")
            .replace(Regex("""\[[^\]]*]"""), "")
            .replace(Regex("""\d{1,2}/\d{1,2}\s+\d{1,2}:\d{2}"""), "")
            .replace(Regex("""\d{1,2}:\d{2}"""), "")
            .replace(Regex("""잔액\s*[0-9,]+원?"""), "")
            .replace(Regex("""누적\s*[0-9,]+원?"""), "")
            // 계좌·카드 번호 마스킹 (예: 110-***-123456, 352-****-1234-56, 1234*56**, (1*2*))
            .replace(Regex("""\d{2,6}(?:-[\d*]{2,8}){1,4}"""), "")
            .replace(Regex("""\(?[\d*]*\*[\d*]*\)?"""), "")
        for (name in institutionNames) {
            cleaned = cleaned.replace(name, "")
        }
        for (word in MERCHANT_NOISE_WORDS) {
            cleaned = cleaned.replace(word, "")
        }
        cleaned = cleaned
            .replace(Regex("""[|·:]"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")

        return cleaned.takeIf { it.isNotBlank() }?.take(30)
            ?: "${institution?.displayName ?: sourceAppName} 내역"
    }

    /** 가맹점명에서 지울 거래 안내 단어 (긴 것부터) */
    private val MERCHANT_NOISE_WORDS = listOf(
        "승인취소", "결제취소", "체크카드", "신용카드", "일시불", "할부", "승인", "결제", "출금", "입금", "사용"
    )

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(32)
    }
}
