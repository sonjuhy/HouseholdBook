package com.budget.householdbook.data.ingestion.kbquick

import com.budget.householdbook.data.ingestion.parser.ParsedNotificationDraft
import com.budget.householdbook.domain.model.QuickInquiryCredentials

/**
 * 은행별 "로그인 없이 계좌 거래내역 조회"(빠른조회·스피드조회 등) 연동
 * - 은행을 추가할 때: 이 인터페이스를 구현하고 di/AppModule.kt의 provider 목록에 넣은 뒤,
 *   FinancialInstitutions의 해당 은행 quickInquiry.status를 SUPPORTED_BETA로 바꿉니다.
 * - 현재 구현: KB국민은행 [KbQuickInquiryScraper]
 */
interface QuickInquiryProvider {
    /** [com.budget.householdbook.domain.model.FinancialInstitution.id] */
    val institutionId: String

    suspend fun fetchRecentTransactions(
        credentials: QuickInquiryCredentials,
        userRealName: String
    ): Result<List<ParsedNotificationDraft>>
}
