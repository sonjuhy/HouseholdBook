package com.budget.householdbook.data.ingestion.kbquick

import com.budget.householdbook.core.security.SecureCredentialManager
import com.budget.householdbook.data.ingestion.parser.ParsedNotificationDraft
import com.budget.householdbook.domain.model.FinancialInstitutions
import com.budget.householdbook.domain.model.QuickInquiryCredentials
import com.budget.householdbook.domain.repository.KbQuickInquiryRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * [Beta] 은행 빠른조회: Keystore 암호화 자격증명([SecureCredentialManager]) + 은행별 연동([QuickInquiryProvider])
 * - 저장된 자격증명의 은행(institutionId)에 맞는 연동을 골라 실행 (현재 KB국민은행만 구현)
 */
class KbQuickInquiryRepositoryImpl(
    private val credentialManager: SecureCredentialManager,
    private val providers: List<QuickInquiryProvider>
) : KbQuickInquiryRepository {

    override val credentialsFlow: StateFlow<QuickInquiryCredentials> = credentialManager.credentialsFlow

    override fun saveCredentials(credentials: QuickInquiryCredentials) = credentialManager.saveCredentials(credentials)

    override suspend fun fetchRecentTransactions(userRealName: String): Result<List<ParsedNotificationDraft>> {
        val credentials = credentialManager.credentialsFlow.value
        val provider = providers.firstOrNull { it.institutionId == credentials.institutionId }
            ?: return Result.failure(IllegalStateException(unsupportedMessage(credentials.institutionId)))
        return provider.fetchRecentTransactions(credentials, userRealName)
    }

    private fun unsupportedMessage(institutionId: String): String {
        val institution = FinancialInstitutions.byId(institutionId)
        val serviceName = institution?.quickInquiry?.serviceName
        return if (institution != null && serviceName != null) {
            "${institution.displayName} '$serviceName' 연동은 아직 준비 중입니다. 알림 읽기로 내역을 가져와 주세요."
        } else {
            "선택한 은행은 계좌 내역 불러오기를 지원하지 않습니다."
        }
    }
}
