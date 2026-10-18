package com.budget.householdbook.data.ingestion.parser

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
    val dedupHash: String
)

/**
 * KB국민은행/KB카드, 토스(Toss), 카카오뱅크 등 금융 앱 알림 및 SMS 파서 +
 * 내 계좌 이체/카드대금 출금(TRANSFER) 및 결제 취소(Cancel) 감지 엔진
 */
object FinancialParserEngine {

    private val SUPPORTED_PACKAGES = mapOf(
        "com.kbstar.kbbank" to "KB스타뱅킹",
        "com.kbcard.cxh.appcard" to "KB Pay(국민카드)",
        "viva.republica.toss" to "토스(Toss)",
        "com.kakaobank.channel" to "카카오뱅크",
        "com.shinhancard.smartshinhan" to "신한 SOL페이",
        "com.hyundaicard.appcard" to "현대카드"
    )

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

    fun isSupportedFinancialPackage(packageName: String): Boolean {
        if (SUPPORTED_PACKAGES.containsKey(packageName)) return true
        val lower = packageName.lowercase()
        return lower.contains("bank") || lower.contains("card") || lower.contains("toss") || lower.contains("pay")
    }

    fun resolveAppName(packageName: String): String {
        return SUPPORTED_PACKAGES[packageName] ?: packageName.substringAfterLast('.').uppercase()
    }

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
        userRealName: String = "홍길동"
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

        // 가맹점명 / 적요 추출
        val merchant = extractMerchant(combinedText, amountMatch.value, sourceAppName)

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
            dedupHash = dedupHash
        )
    }

    private fun extractMerchant(
        fullText: String,
        matchedAmountToken: String,
        sourceAppName: String
    ): String {
        val cleaned = fullText
            .replace("[Web발신]", "")
            .replace(matchedAmountToken, "")
            .replace(Regex("""\[[^\]]*]"""), "")
            .replace(Regex("""\d{2}/\d{2}\s+\d{2}:\d{2}"""), "")
            .replace(Regex("""잔액\s*[0-9,]+원?"""), "")
            .replace("일시불", "")
            .replace("승인취소", "")
            .replace("결제취소", "")
            .replace("승인", "")
            .replace("결제", "")
            .replace("출금", "")
            .replace("입금", "")
            .replace(sourceAppName, "")
            .replace("KB국민카드", "")
            .replace("KB국민은행", "")
            .replace("토스뱅크", "")
            .replace("토스", "")
            .trim()
            .replace(Regex("""\s+"""), " ")

        return cleaned.takeIf { it.isNotBlank() }?.take(30) ?: "$sourceAppName 내역"
    }

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(32)
    }
}
