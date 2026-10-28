package com.budget.householdbook.domain.usecase

import com.budget.householdbook.domain.model.NotificationSources
import com.budget.householdbook.data.ingestion.parser.CancelMatcher
import com.budget.householdbook.data.ingestion.parser.FinancialParserEngine
import com.budget.householdbook.data.ingestion.parser.ParsedNotificationDraft
import com.budget.householdbook.data.local.entity.PendingTransactionEntity
import com.budget.householdbook.domain.model.IngestionSource
import com.budget.householdbook.domain.model.QuickInquiryCredentials
import com.budget.householdbook.domain.model.QueueKind
import com.budget.householdbook.domain.model.QueueStatus
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.domain.repository.AppNotifier
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

        // 같은 결제를 카드 앱·은행 앱·문자가 각각 알려주는 경우 처음 들어온 것만 남김
        if (settings.autoFilterDuplicateAlerts) {
            val recent = pendingQueueRepository.getRecentSince(draft.occurredAt - DUPLICATE_ALERT_WINDOW_MILLIS)
            if (recent.any { DuplicateAlertDetector.isSamePayment(it, draft) }) return null
        }
        val merchantRules = ruleRepository.getAllMerchantRules()
        // 여러 규칙이 맞으면 가장 최근에 학습한 규칙(사용자가 마지막으로 고른 공간·분류)을 우선
        // (처음 실행 때 만든 기본 규칙이 나중의 선택을 계속 덮어쓰지 않도록)
        val matchedRule = merchantRules
            .filter { rule ->
                draft.merchant.contains(rule.merchantKeyword, ignoreCase = true) ||
                    draft.rawText.contains(rule.merchantKeyword, ignoreCase = true)
            }
            .maxByOrNull { it.id }

        // 공간 우선순위: 가맹점 학습 규칙 > 설정의 '알림 내역 기본 저장 공간' > 마지막으로 보던 공간
        val targetSpaceId = matchedRule?.targetSpaceId
            ?: settings.defaultIngestSpaceId
            ?: settings.currentSpaceId
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
    private val enqueueParsedDraft: EnqueueParsedDraftUseCase,
    private val pendingQueueRepository: PendingQueueRepository,
    private val notifier: AppNotifier
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

        // 앱별 알림 제외: 출처는 앱 패키지, 문자는 하나의 출처(sms)로 다룸
        val sourceId = when {
            sourcePackage != null -> sourcePackage
            sourceType == IngestionSource.SMS -> NotificationSources.SMS_SOURCE_ID
            else -> "app:$sourceAppName" // 패키지를 모르는 앱 알림 (예: 예시 알림)
        }
        val sourceLabel = if (sourceId == NotificationSources.SMS_SOURCE_ID) NotificationSources.SMS_SOURCE_LABEL else sourceAppName
        settingsRepository.recordSeenSource(sourceId, sourceLabel)
        if (sourceId in settings.excludedSourceIds) return false

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

        val pendingId = enqueueParsedDraft(draft) ?: return false
        // 새 결제 알림 ([공용에 저장] [개인에 저장] 버튼)
        pendingQueueRepository.getById(pendingId)?.let { notifier.notifyPendingPayment(it.toPaymentAlert()) }
        return true
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
        scheduler.ensurePeriodicStatusCheck()
        scheduler.applyKbAutoInquiry(
            enabled = settings.kbAutoInquiryEnabled && kbRepository.credentialsFlow.value.isConfigured,
            intervalHours = settings.kbAutoInquiryIntervalHours
        )
    }
}

/** 다른 출처에서 온 같은 결제 알림으로 볼 시간 범위 */
private const val DUPLICATE_ALERT_WINDOW_MILLIS = 3L * 60 * 1000

/**
 * 같은 결제가 여러 앱(카드 앱·은행 앱·문자)에서 각각 알림으로 들어온 경우를 찾는 규칙
 * - 같은 금액 · 같은 방향(지출/수입, 결제/취소) · 3분 이내 · 서로 다른 출처
 * - 가맹점명이 비슷하거나, 한쪽이 가맹점을 알 수 없는 알림(예: "OO은행 내역")일 때만 중복으로 봄
 * - 같은 출처에서 연달아 온 같은 금액은 실제로 두 번 결제한 것일 수 있어 중복으로 보지 않음
 */
object DuplicateAlertDetector {

    fun isSamePayment(existing: PendingTransactionEntity, draft: ParsedNotificationDraft): Boolean {
        if (existing.parsedAmount != draft.amount) return false
        if (existing.sourceAppName == draft.sourceAppName) return false
        if (existing.occurredAt < draft.occurredAt - DUPLICATE_ALERT_WINDOW_MILLIS) return false

        val existingIsCancel = existing.queueKind == QueueKind.CANCEL_MATCHED || existing.queueKind == QueueKind.CANCEL_UNMATCHED
        if (existingIsCancel != draft.isCancelAlert) return false
        // 내 계좌 이체 쌍은 출금·입금이 각각 들어오는 정상 흐름이라 비교하지 않음
        if (existing.queueKind == QueueKind.TRANSFER_PAIR) return false
        if (!existingIsCancel && existing.parsedType != draft.transactionType) return false

        return isGenericMerchant(existing.parsedMerchant) ||
            isGenericMerchant(draft.merchant) ||
            CancelMatcher.isSameMerchant(existing.parsedMerchant, draft.merchant)
    }

    /** 파서가 가맹점을 못 찾았을 때 붙이는 이름 ("OO 내역") */
    private fun isGenericMerchant(merchant: String) = merchant.endsWith(" 내역")
}
