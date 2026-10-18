package com.budget.householdbook.domain.usecase

import com.budget.householdbook.data.ingestion.parser.CancelMatcher
import com.budget.householdbook.data.ingestion.parser.FinancialParserEngine
import com.budget.householdbook.data.ingestion.parser.ParsedNotificationDraft
import com.budget.householdbook.data.local.entity.PendingTransactionEntity
import com.budget.householdbook.domain.model.IngestionSource
import com.budget.householdbook.domain.model.QuickInquiryCredentials
import com.budget.householdbook.domain.model.QueueKind
import com.budget.householdbook.domain.model.QueueStatus
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.domain.repository.AutomationRuleRepository
import com.budget.householdbook.domain.repository.BackgroundTaskScheduler
import com.budget.householdbook.domain.repository.CategoryRepository
import com.budget.householdbook.domain.repository.KbQuickInquiryRepository
import com.budget.householdbook.domain.repository.PendingQueueRepository
import com.budget.householdbook.domain.repository.SettingsRepository
import com.budget.householdbook.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first

/** 자동 취소 매칭: 취소 알림 기준 최근 30일 */
internal const val CANCEL_MATCH_LOOKBACK_MILLIS = 30L * 24 * 3600 * 1000L

/** 내 계좌 간 이체 쌍 감지: 3분 이내 */
internal const val TRANSFER_PAIR_WINDOW_MILLIS = 180_000L

/**
 * 알림/SMS/빠른조회로부터 파싱된 항목을 대기 큐(PendingQueue)에 적재합니다.
 * - 절대 확정 거래로 바로 넣지 않고 무조건 대기 큐에 넣습니다.
 * - 무시 키워드 검사 -> 가맹점 자동 카테고리 추천 -> 취소 내역 매칭 -> 3분 이내 이체 쌍 감지를 수행합니다.
 * @return 새로 적재된 대기 항목 ID (무시되었거나 중복이면 null)
 */
class EnqueueParsedDraftUseCase(
    private val settingsRepository: SettingsRepository,
    private val ruleRepository: AutomationRuleRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val pendingQueueRepository: PendingQueueRepository
) {
    suspend operator fun invoke(draft: ParsedNotificationDraft): Long? {
        // 1. 무시 키워드 검사
        val ignoredKeywords = ruleRepository.getIgnoredKeywordsOnce()
        if (ignoredKeywords.any { rule -> draft.rawText.contains(rule.keyword, ignoreCase = true) }) {
            return null
        }

        // 2. 가맹점 자동 분류 규칙 탐색
        val settings = settingsRepository.userSettingsFlow.first()
        val merchantRules = ruleRepository.getAllMerchantRules()
        val matchedRule = merchantRules.firstOrNull { rule ->
            draft.merchant.contains(rule.merchantKeyword, ignoreCase = true) ||
                draft.rawText.contains(rule.merchantKeyword, ignoreCase = true)
        }

        val targetSpaceId = matchedRule?.targetSpaceId ?: settings.currentSpaceId
        val spaceCategories = categoryRepository.getCategoriesBySpaceOnce(targetSpaceId)
        val defaultParentCategory = spaceCategories.firstOrNull {
            it.parentId == null && (it.transactionType == draft.transactionType || draft.transactionType == TransactionType.TRANSFER)
        } ?: spaceCategories.firstOrNull { it.parentId == null }

        val suggestedCatId = matchedRule?.categoryId ?: defaultParentCategory?.id
        val suggestedSubId = matchedRule?.subCategoryId
            ?: spaceCategories.firstOrNull { it.parentId == suggestedCatId }?.id

        // 3. 결제 취소 알림인 경우 기존 확정 거래 매칭 시도 (최근 30일 내 동일 금액)
        var queueKind = QueueKind.NORMAL
        var matchedTxId: Long? = null
        if (draft.isCancelAlert) {
            val since = draft.occurredAt - CANCEL_MATCH_LOOKBACK_MILLIS
            val candidates = transactionRepository.findCancelCandidatesByAmount(draft.amount, since)
            val bestCandidate = CancelMatcher.findBestMatch(draft.merchant, candidates)

            if (bestCandidate != null) {
                queueKind = QueueKind.CANCEL_MATCHED
                matchedTxId = bestCandidate.id
            } else {
                queueKind = QueueKind.CANCEL_UNMATCHED
            }
        } else if (draft.transactionType == TransactionType.TRANSFER) {
            queueKind = QueueKind.TRANSFER_PAIR
        }

        // 4. 3분 이내 동일 금액 출금+입금 쌍(내 계좌 간 이체) 자동 감지
        val recentPendings = pendingQueueRepository.getRecentPendingSince(draft.occurredAt - TRANSFER_PAIR_WINDOW_MILLIS)
        val oppositePair = if (draft.isCancelAlert) null else recentPendings.firstOrNull { existing ->
            existing.parsedAmount == draft.amount &&
                existing.sourceAppName != draft.sourceAppName &&
                ((existing.parsedType == TransactionType.EXPENSE && draft.transactionType == TransactionType.INCOME) ||
                    (existing.parsedType == TransactionType.INCOME && draft.transactionType == TransactionType.EXPENSE))
        }

        // 원본을 못 찾은 취소 알림은 지출로 잘못 합산되지 않도록 기본값을 '이체/제외'로 둠
        val finalType = if (oppositePair != null || queueKind == QueueKind.CANCEL_UNMATCHED) {
            TransactionType.TRANSFER
        } else {
            draft.transactionType
        }
        if (oppositePair != null) {
            queueKind = QueueKind.TRANSFER_PAIR
        }

        val insertedId = pendingQueueRepository.insertIgnoreDuplicate(
            PendingTransactionEntity(
                sourceType = draft.sourceType,
                sourceAppName = draft.sourceAppName,
                rawText = draft.rawText,
                parsedAmount = draft.amount,
                parsedType = finalType,
                parsedMerchant = draft.merchant,
                occurredAt = draft.occurredAt,
                queueKind = queueKind,
                matchedTransactionId = matchedTxId,
                pairedQueueId = oppositePair?.id,
                suggestedSpaceId = targetSpaceId,
                suggestedCategoryId = suggestedCatId,
                suggestedSubCategoryId = suggestedSubId,
                status = QueueStatus.PENDING,
                dedupHash = draft.dedupHash
            )
        )

        if (insertedId > 0L && oppositePair != null) {
            pendingQueueRepository.markAsTransferPair(oppositePair.id, insertedId)
        }

        return insertedId.takeIf { it > 0L }
    }
}

/**
 * 외부 알림/SMS 원문을 받아 파싱 후 대기 큐에 넣습니다.
 * @return 대기 큐에 새로 추가되었으면 true
 */
class IngestRawNotificationUseCase(
    private val settingsRepository: SettingsRepository,
    private val enqueueParsedDraft: EnqueueParsedDraftUseCase
) {
    /**
     * @param sourcePackage 알림을 보낸 앱 패키지 (문자는 null → 본문으로 금융기관 판별)
     * @return 대기 큐에 새로 들어갔으면 true (꺼둔 금융기관·광고·중복이면 false)
     */
    suspend operator fun invoke(
        sourceType: IngestionSource,
        sourceAppName: String,
        title: String,
        body: String,
        sourcePackage: String? = null
    ): Boolean {
        val settings = settingsRepository.userSettingsFlow.first()
        val draft = FinancialParserEngine.parse(
            sourceType = sourceType,
            sourceAppName = sourceAppName,
            title = title,
            body = body,
            userRealName = settings.userRealName,
            sourcePackage = sourcePackage
        ) ?: return false

        // 설정에서 끈 금융기관의 알림·문자는 건너뜀
        if (draft.institutionId !in settings.enabledInstitutionIds) return false

        return enqueueParsedDraft(draft) != null
    }
}

/**
 * [Beta] KB국민은행 무로그인 빠른조회 실행 -> 신규 내역 대기 큐에 적재
 * @return 새로 추가된 건수
 */
class RunKbQuickInquiryUseCase(
    private val settingsRepository: SettingsRepository,
    private val kbRepository: KbQuickInquiryRepository,
    private val enqueueParsedDraft: EnqueueParsedDraftUseCase
) {
    suspend operator fun invoke(): Result<Int> {
        val settings = settingsRepository.userSettingsFlow.first()
        return kbRepository.fetchRecentTransactions(settings.userRealName).map { drafts ->
            drafts.count { draft -> enqueueParsedDraft(draft) != null }
        }
    }
}

/**
 * [Beta] KB 빠른조회 계좌 정보(암호화 저장)와 내 실명(이체 감지용) 저장
 */
class SaveKbCredentialsUseCase(
    private val kbRepository: KbQuickInquiryRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(
        institutionId: String,
        accountNo: String,
        birthOrBiz: String,
        pw4: String,
        userRealName: String
    ) {
        kbRepository.saveCredentials(
            QuickInquiryCredentials(
                institutionId = institutionId,
                accountNumber = accountNo,
                birthOrBizNumber = birthOrBiz,
                quickPassword4Digit = pw4
            )
        )
        settingsRepository.setUserRealName(userRealName)
    }
}

/**
 * [Beta] KB 빠른조회 백그라운드 자동 갱신 켜기/끄기 (계좌 정보가 없으면 켤 수 없음)
 */
class UpdateKbAutoInquiryUseCase(
    private val settingsRepository: SettingsRepository,
    private val kbRepository: KbQuickInquiryRepository,
    private val scheduler: BackgroundTaskScheduler
) {
    suspend operator fun invoke(enabled: Boolean, intervalHours: Int): Result<Unit> = runCatching {
        if (enabled) {
            check(kbRepository.credentialsFlow.value.isConfigured) { "먼저 KB 빠른조회 계좌를 등록해주세요." }
        }
        settingsRepository.setKbAutoInquiry(enabled, intervalHours)
        scheduler.applyKbAutoInquiry(enabled, intervalHours)
    }
}

/**
 * [QuickInquiryWorker] 자동 조회 실행 조건: 사용자가 켰고 계좌 정보가 등록되어 있을 때
 */
class IsKbAutoInquiryReadyUseCase(
    private val settingsRepository: SettingsRepository,
    private val kbRepository: KbQuickInquiryRepository
) {
    suspend operator fun invoke(): Boolean {
        val settings = settingsRepository.userSettingsFlow.first()
        return settings.kbAutoInquiryEnabled && kbRepository.credentialsFlow.value.isConfigured
    }
}

/**
 * 앱 시작 시 백그라운드 작업(주기 동기화, KB 자동 조회) 예약 상태를 설정값과 맞춥니다.
 */
class EnsureBackgroundWorkUseCase(
    private val settingsRepository: SettingsRepository,
    private val kbRepository: KbQuickInquiryRepository,
    private val scheduler: BackgroundTaskScheduler
) {
    suspend operator fun invoke() {
        val settings = settingsRepository.userSettingsFlow.first()
        scheduler.ensurePeriodicSync()
        scheduler.applyKbAutoInquiry(
            enabled = settings.kbAutoInquiryEnabled && kbRepository.credentialsFlow.value.isConfigured,
            intervalHours = settings.kbAutoInquiryIntervalHours
        )
    }
}
