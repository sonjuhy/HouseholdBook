package com.budget.householdbook.data.ingestion.parser

import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.domain.model.TransactionType

/**
 * 결제 취소/환불 알림을 기존 확정 거래(원본 결제)와 연결하는 순수 매칭 로직
 * - 동일 금액 후보 중 '지출'이면서 아직 취소되지 않은 거래만 대상으로 함
 * - 가맹점명이 비슷해야만 매칭하며, 확신할 수 없으면 null(= CANCEL_UNMATCHED)을 반환
 */
object CancelMatcher {

    private const val MIN_KEY_LENGTH = 2

    fun findBestMatch(cancelMerchant: String, candidates: List<TransactionEntity>): TransactionEntity? {
        val cancelKey = normalize(cancelMerchant)
        if (cancelKey.length < MIN_KEY_LENGTH) return null

        return candidates
            .filter { it.type == TransactionType.EXPENSE && !it.isCanceled }
            .firstOrNull { tx -> isSimilarMerchant(cancelKey, normalize(tx.merchantOrTitle)) }
    }

    private fun isSimilarMerchant(a: String, b: String): Boolean {
        if (b.length < MIN_KEY_LENGTH) return false
        return a.contains(b) || b.contains(a) || a.take(3) == b.take(3)
    }

    /** 원문 가맹점명 두 개가 같은 곳으로 보이는지 (정규화 후 비교) */
    fun isSameMerchant(a: String, b: String): Boolean {
        val keyA = normalize(a)
        val keyB = normalize(b)
        if (keyA.length < MIN_KEY_LENGTH) return false
        return isSimilarMerchant(keyA, keyB)
    }

    /**
     * "(주)우아한형제들(배달의민족)" -> "우아한형제들배달의민족" 처럼 비교에 불필요한 기호/공백 제거
     */
    fun normalize(merchant: String): String =
        merchant
            .replace("(주)", "")
            .replace("㈜", "")
            .replace(Regex("""[\s()\[\]·.,\-_/]"""), "")
            .lowercase()
}
