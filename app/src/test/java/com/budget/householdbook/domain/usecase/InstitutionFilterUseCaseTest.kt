package com.budget.householdbook.domain.usecase

import com.budget.householdbook.core.datastore.AppUserSettings
import com.budget.householdbook.domain.model.FinancialInstitutions
import com.budget.householdbook.domain.model.IngestionSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstitutionFilterUseCaseTest {

    private val settings = FakeSettingsRepository(AppUserSettings())
    private val pending = FakePendingQueueRepository()
    private val enqueue = EnqueueParsedDraftUseCase(
        settings, FakeAutomationRuleRepository(), FakeCategoryRepository(), FakeTransactionRepository(), pending
    )
    private val notifier = FakeAppNotifier()
    private val ingest = IngestRawNotificationUseCase(settings, enqueue, pending, notifier)
    private val updateEnabled = UpdateEnabledInstitutionsUseCase(settings)

    @Test
    fun `처음에는 모든 금융기관 알림을 읽는다`() = runTest {
        assertTrue(ingest(IngestionSource.APP_NOTIFICATION, "우리은행", "출금", "출금 30,000원 스타벅스", "com.wooribank.smart.npib"))
        assertTrue(ingest(IngestionSource.SMS, "문자(1)", "", "[Web발신] 신협 출금 7,000원 편의점"))
        assertEquals(2, pending.state.value.size)
    }

    @Test
    fun `끈 금융기관의 알림과 문자는 대기내역에 넣지 않는다`() = runTest {
        updateEnabled(FinancialInstitutions.DEFAULT_ENABLED_IDS - "WOORI" - "CU")

        assertFalse(ingest(IngestionSource.APP_NOTIFICATION, "우리은행", "출금", "출금 30,000원 스타벅스", "com.wooribank.smart.npib"))
        assertFalse(ingest(IngestionSource.SMS, "문자(1)", "", "[Web발신] 신협 출금 7,000원 편의점"))
        assertTrue(ingest(IngestionSource.APP_NOTIFICATION, "KB국민은행", "출금", "출금 12,000원 GS25", "com.kbstar.kbbank"))
        assertEquals(listOf("GS25"), pending.state.value.map { it.parsedMerchant })
    }

    @Test
    fun `그 외 금융 앱·문자를 끄면 금융사를 알 수 없는 문자도 건너뛴다`() = runTest {
        updateEnabled(setOf("KB"))
        assertFalse(ingest(IngestionSource.SMS, "문자(1)", "", "[Web발신] 승인 15,000원 올리브영"))
        assertEquals(setOf("KB"), settings.state.value.enabledInstitutionIds)
    }

    @Test
    fun `목록에 없는 ID는 저장되지 않는다`() = runTest {
        updateEnabled(setOf("KB", "UNKNOWN_BANK"))
        assertEquals(setOf("KB"), settings.state.value.enabledInstitutionIds)
    }
}
