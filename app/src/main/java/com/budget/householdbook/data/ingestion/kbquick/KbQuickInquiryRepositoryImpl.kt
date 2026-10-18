package com.budget.householdbook.data.ingestion.kbquick

import com.budget.householdbook.core.security.SecureCredentialManager
import com.budget.householdbook.data.ingestion.parser.ParsedNotificationDraft
import com.budget.householdbook.domain.model.KbQuickCredentials
import com.budget.householdbook.domain.repository.KbQuickInquiryRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * [Beta] KB 빠른조회: Keystore 암호화 자격증명([SecureCredentialManager]) + 스크래퍼([KbQuickInquiryScraper])
 */
class KbQuickInquiryRepositoryImpl(
    private val credentialManager: SecureCredentialManager,
    private val scraper: KbQuickInquiryScraper
) : KbQuickInquiryRepository {

    override val credentialsFlow: StateFlow<KbQuickCredentials> = credentialManager.credentialsFlow

    override fun saveCredentials(credentials: KbQuickCredentials) = credentialManager.saveCredentials(credentials)

    override suspend fun fetchRecentTransactions(userRealName: String): Result<List<ParsedNotificationDraft>> =
        scraper.fetchRecentTransactions(credentialManager.credentialsFlow.value, userRealName)
}
