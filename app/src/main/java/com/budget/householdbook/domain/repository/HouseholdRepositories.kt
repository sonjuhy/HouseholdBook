package com.budget.householdbook.domain.repository

import com.budget.householdbook.core.datastore.AppUserSettings
import com.budget.householdbook.data.ingestion.parser.ParsedNotificationDraft
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.data.local.entity.IgnoredKeywordEntity
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.data.local.entity.MealSavingLogEntity
import com.budget.householdbook.data.local.entity.MerchantCategoryRuleEntity
import com.budget.householdbook.data.local.entity.PendingTransactionEntity
import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.data.sync.GoogleLoginResult
import com.budget.householdbook.domain.model.QuickInquiryCredentials
import com.budget.householdbook.domain.model.SharedRoomInfo
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.domain.model.MealPeriodTotal
import com.budget.householdbook.domain.model.MealPriceConfig
import com.budget.householdbook.domain.model.QueueStatus
import com.budget.householdbook.domain.model.ThemeMode
import com.budget.householdbook.domain.model.UserTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/*
 * 도메인 계층(UseCase)이 의존하는 저장소 인터페이스 모음.
 * - 구현체는 data 계층(Room DAO, DataStore, Firebase, WorkManager 등)에 있으며 di/AppModule.kt에서 연결합니다.
 * - Android 프레임워크 타입(Context, Activity 등)을 노출하지 않아 UseCase를 JVM 단위 테스트로 검증할 수 있습니다.
 * - Room Entity는 별도 매핑 없이 도메인 모델로 함께 사용합니다.
 */

/**
 * 여러 저장 작업을 하나의 DB 트랜잭션으로 원자적으로 실행 (예: 분할 승인 시 큐 상태 변경 + 다중 거래 삽입)
 */
interface TransactionRunner {
    suspend fun <R> runInTransaction(block: suspend () -> R): R
}

/** 장부 공간(공통 생활비 / 개인 용돈) */
interface LedgerSpaceRepository {
    fun observeAllSpaces(): Flow<List<LedgerSpaceEntity>>
    suspend fun getSpaceById(spaceId: String): LedgerSpaceEntity?
    suspend fun insertSpacesIfEmpty(spaces: List<LedgerSpaceEntity>)
    suspend fun updateBudgetAndCycle(spaceId: String, targetAmount: Long, billingStartDay: Int)
    suspend fun updateShareConfig(spaceId: String, memberNamesCsv: String, inviteCode: String?, isShared: Boolean)
}

/** 상위/세부 카테고리 */
interface CategoryRepository {
    fun observeAllActiveCategories(): Flow<List<CategoryEntity>>
    suspend fun getCategoriesBySpaceOnce(spaceId: String): List<CategoryEntity>
    suspend fun insertCategory(category: CategoryEntity): Long
    suspend fun updateCategory(category: CategoryEntity)
    suspend fun archiveCategoryAndSubs(categoryId: Long)
}

/** 등록 대기 큐 (자동 수집 내역은 반드시 여기로만 들어옴) */
interface PendingQueueRepository {
    fun observePendingQueue(): Flow<List<PendingTransactionEntity>>
    fun observePendingCount(): Flow<Int>
    suspend fun getById(id: Long): PendingTransactionEntity?
    suspend fun getRecentPendingSince(sinceMillis: Long): List<PendingTransactionEntity>
    /** 상태와 관계없이 최근 들어온 대기 항목 (다른 앱에서 온 같은 알림 중복 확인용) */
    suspend fun getRecentSince(sinceMillis: Long): List<PendingTransactionEntity>
    /** 검수 중인 항목의 공간·유형·분류 변경 (대기 상태일 때만) */
    suspend fun updateClassification(id: Long, spaceId: String, type: TransactionType, categoryId: Long?, subCategoryId: Long?)
    /** dedupHash가 겹치면 무시하고 0 이하를 반환 */
    suspend fun insertIgnoreDuplicate(item: PendingTransactionEntity): Long
    suspend fun updateStatus(id: Long, status: QueueStatus)
    suspend fun markCancelLinkedAndApproved(id: Long, transactionId: Long)
    suspend fun markAsTransferPair(id: Long, partnerId: Long)
}

/** 확정 거래 내역 */
interface TransactionRepository {
    fun observeTransactionsInPeriod(spaceId: String, startMillis: Long, endExclusiveMillis: Long): Flow<List<TransactionEntity>>
    suspend fun getById(transactionId: Long): TransactionEntity?
    suspend fun getByRemoteId(remoteId: String): TransactionEntity?
    /** 자동 취소 매칭 후보: 같은 금액의 최근 미취소 거래 */
    suspend fun findCancelCandidatesByAmount(amount: Long, sinceMillis: Long): List<TransactionEntity>
    /** 취소 원본 직접 선택 후보: 환불 금액 이상인 최근 미취소 지출 (같은 금액 우선) */
    suspend fun findManualCancelCandidates(refundAmount: Long, sinceMillis: Long): List<TransactionEntity>
    suspend fun insertTransaction(transaction: TransactionEntity): Long
    suspend fun insertTransactions(transactions: List<TransactionEntity>)
    suspend fun markTransactionCanceled(transactionId: Long)
    /** 취소 처리를 되돌림 (실수로 취소한 경우) */
    suspend fun restoreTransactionCanceled(transactionId: Long)
    suspend fun applyPartialCancel(transactionId: Long, newAmount: Long, memo: String)
    suspend fun markPendingDelete(transactionId: Long)
    suspend fun deleteById(transactionId: Long)
    suspend fun countUnsyncedInSpace(spaceId: String): Int
    /** 한 공간에서 작성자 이름을 일괄 변경 (예전 예시 이름 '홍길동' 정리용) */
    suspend fun renameAuthor(spaceId: String, oldName: String, newName: String)
}

/** 집밥 절약 기록 */
interface MealRepository {
    fun observeMealLogsInPeriod(spaceId: String, startMillis: Long, endExclusiveMillis: Long): Flow<List<MealSavingLogEntity>>
    suspend fun sumMealsInPeriod(spaceId: String, startMillis: Long, endExclusiveMillis: Long): MealPeriodTotal
    suspend fun insertMealLog(log: MealSavingLogEntity): Long
    suspend fun deleteMealLog(id: Long)
}

/** 자동화 규칙 (무시 키워드, 가맹점 → 분류 학습) */
interface AutomationRuleRepository {
    fun observeIgnoredKeywords(): Flow<List<IgnoredKeywordEntity>>
    suspend fun getIgnoredKeywordsOnce(): List<IgnoredKeywordEntity>
    suspend fun insertIgnoredKeyword(entity: IgnoredKeywordEntity)
    suspend fun deleteIgnoredKeyword(id: Long)
    suspend fun getAllMerchantRules(): List<MerchantCategoryRuleEntity>
    suspend fun upsertMerchantRule(rule: MerchantCategoryRuleEntity)
}

/** 사용자 설정 (DataStore) */
interface SettingsRepository {
    val userSettingsFlow: Flow<AppUserSettings>
    suspend fun setCurrentSpaceId(spaceId: String)
    suspend fun setUserTier(tier: UserTier)
    suspend fun setThemeMode(themeMode: ThemeMode)
    suspend fun setUserRealName(name: String)
    suspend fun saveGoogleLoginProfile(uid: String, email: String, displayName: String, resolvedTier: UserTier)
    suspend fun clearGoogleLogin()
    suspend fun updateMealPriceConfig(config: MealPriceConfig)
    suspend fun setKbAutoInquiry(enabled: Boolean, intervalHours: Int)
    suspend fun setEnabledInstitutions(ids: Set<String>)
    suspend fun setExcludedSources(ids: Set<String>)
    /** 금융 알림을 보낸 앱(또는 문자)을 '앱별 알림 제외' 목록에 남김 */
    suspend fun recordSeenSource(sourceId: String, label: String)
    suspend fun setAutoFilterDuplicateAlerts(enabled: Boolean)
    suspend fun setDefaultIngestSpaceId(spaceId: String?)
    suspend fun markSeedCompleted()
    /** 첫 실행 튜토리얼 확인 여부 (false로 되돌리면 다시 보여줌) */
    suspend fun setOnboardingSeen(seen: Boolean)
}

/** [Pro] 구글 로그인·초대·Firestore 공유 장부 동기화 */
interface SharedSyncRepository {
    val syncStatusMessage: StateFlow<String>
    /** PRO + 공유 활성화 상태일 때만 공통 생활비 변경을 반영. 성공 여부 반환 */
    suspend fun syncIfEligible(userTier: UserTier, isRemoteShared: Boolean): Boolean
    /** 지금 동기화 가능한 상태인지 (Firebase 설정 + 구글 로그인) */
    fun canSyncNow(): Boolean
    /**
     * @param uiHost 구글 인증 창을 띄울 화면 (Android에서는 Activity, 없으면 null)
     */
    suspend fun signInWithGoogleAccount(
        uiHost: Any?,
        manualGoogleEmail: String?,
        manualDisplayName: String?
    ): Result<GoogleLoginResult>
    suspend fun sendGoogleAccountInvitation(
        inviterEmail: String,
        inviterName: String,
        partnerGoogleEmail: String,
        partnerName: String,
        inviteCode: String
    ): Result<String>
    suspend fun signOutGoogleAccount()
    suspend fun syncUserProfileTierToCloud(
        email: String,
        displayName: String,
        tier: UserTier,
        inviteCode: String?,
        memberNamesCsv: String
    )
    fun isValidInviteCode(code: String?): Boolean
    fun generateInviteCode(): String

    // 멤버 관리
    suspend fun loadRoomMembers(): Result<SharedRoomInfo>
    suspend fun cancelInvitation(partnerEmail: String): Result<SharedRoomInfo>
    suspend fun removeMember(memberEmail: String): Result<SharedRoomInfo>
    suspend fun leaveRoom(): Result<Unit>
    /** 로그인한 구글 계정의 프로필 이름 (로그인하지 않았거나 이름이 없으면 null) */
    fun currentGoogleDisplayName(): String?
    /** 장부에 표시할 내 이름을 내 프로필·공유 장부 멤버 명단에 반영 (로그인하지 않았으면 아무것도 안 함) */
    suspend fun updateDisplayName(name: String): Result<Unit>
    /** 방장 넘기기 (방장 → 함께 쓰는 중인 멤버) */
    suspend fun transferOwnership(newOwnerEmail: String): Result<SharedRoomInfo>

    /**
     * 회원 탈퇴: 클라우드의 내 정보(프로필·초대장·공유 장부 참여)와 로그인 계정 삭제
     * @param uiHost 본인 재확인용 구글 인증 창을 띄울 화면
     */
    suspend fun deleteAccount(uiHost: Any?): Result<Unit>
}

/** 이 휴대폰에 저장된 앱 데이터 전체 삭제 (회원 탈퇴 시 선택). 호출 후 앱이 종료될 수 있음 */
interface LocalDataResetter {
    fun wipeAllLocalData()
}

/** [Beta] KB국민은행 무로그인 빠른조회 (암호화 자격증명 + 스크래퍼) */
interface KbQuickInquiryRepository {
    val credentialsFlow: StateFlow<QuickInquiryCredentials>
    fun saveCredentials(credentials: QuickInquiryCredentials)
    suspend fun fetchRecentTransactions(userRealName: String): Result<List<ParsedNotificationDraft>>
}

/** 백그라운드 작업 예약 (WorkManager) */
interface BackgroundTaskScheduler {
    fun ensurePeriodicSync()
    fun applyKbAutoInquiry(enabled: Boolean, intervalHours: Int)
}

/** 집밥 기록·공간·단가가 바뀌었을 때 홈 화면 위젯 갱신 */
fun interface MealWidgetRefresher {
    fun refresh()
}
