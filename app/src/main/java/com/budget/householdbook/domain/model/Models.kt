package com.budget.householdbook.domain.model

import kotlinx.serialization.Serializable

/**
 * 사용자 구독 티어:
 * - FREE: 혼자 쓰기 (휴대폰 내부 저장)
 * - PRO: 함께 쓰기 (실시간 공유 및 오프라인 지원)
 */
@Serializable
enum class UserTier(val displayName: String) {
    FREE("기본 (혼자 쓰기)"),
    PRO("프리미엄 (함께 쓰기)")
}

/**
 * 화면 외관(Appearance) 테마 모드
 */
@Serializable
enum class ThemeMode(val displayName: String, val emoji: String) {
    SYSTEM("기기 설정", "📱"),
    LIGHT("밝은 화면", "☀️"),
    DARK("어두운 화면", "🌙")
}

/**
 * 장부 공간 구분:
 * - SHARED: 여러 사람이 함께 들어와 관리하는 공통 생활비
 * - PERSONAL: 타인에게 보이지 않는 개인 용돈
 */
@Serializable
enum class SpaceType(val displayName: String) {
    SHARED("공통 생활비"),
    PERSONAL("내 용돈")
}

/**
 * 거래 유형:
 * - EXPENSE: 지출 (목표 예산에서 차감)
 * - INCOME: 수입 (수입 합계 반영)
 * - TRANSFER: 내 계좌 간 이체 / 카드대금 출금 (예산 및 지출 합계에서 제외)
 */
@Serializable
enum class TransactionType(val displayName: String) {
    EXPENSE("지출"),
    INCOME("수입"),
    TRANSFER("이체/제외")
}

/**
 * 자동 수집 출처
 */
@Serializable
enum class IngestionSource(val displayName: String) {
    APP_NOTIFICATION("앱 알림"),
    SMS("문자 메시지"),
    KB_QUICK_WEB_BETA("KB 계좌 빠른조회"),
    MANUAL("직접 입력")
}

/**
 * 등록 대기 항목 성격
 */
@Serializable
enum class QueueKind(val badgeLabel: String) {
    NORMAL("확인 대기"),
    CANCEL_MATCHED("결제 취소 확인됨"),
    CANCEL_UNMATCHED("결제 취소 확인 필요"),
    TRANSFER_PAIR("내 계좌 이체")
}

/**
 * 대기 큐 처리 상태
 */
@Serializable
enum class QueueStatus {
    PENDING,
    APPROVED,
    DISMISSED
}

/**
 * 집밥(직접 해먹은 끼니) 종류
 */
@Serializable
enum class MealType(val displayName: String, val emoji: String, val defaultUnitPrice: Long) {
    BREAKFAST("아침 집밥", "🌅", 6_000L),
    LUNCH("점심 집밥", "☀️", 10_000L),
    DINNER("저녁 집밥", "🌙", 15_000L),
    SNACK("간식·홈카페", "☕", 6_000L),

    /** 금액을 그때그때 직접 입력 (정해진 단가 없음, 메모에 무엇을 아꼈는지 기록) */
    CUSTOM("직접 입력", "✏️", 0L);

    companion object {
        /** 원터치 +1 버튼으로 보여줄 끼니 (직접 입력은 별도 버튼) */
        val quickTypes: List<MealType> = listOf(BREAKFAST, LUNCH, DINNER, SNACK)
    }
}

/**
 * Pro 티어 오프라인 퍼스트 동기화 상태
 */
@Serializable
enum class SyncStatus {
    SYNCED,
    PENDING_UPLOAD,
    PENDING_DELETE
}

/**
 * 대기 큐에서 1건의 결제를 공통 생활비 + 개인 용돈 등으로 쪼개어(분할 등록) 승인할 때 사용하는 모델
 */
data class SplitApprovalItem(
    val spaceId: String,
    val categoryId: Long,
    val subCategoryId: Long?,
    val amount: Long,
    val memo: String = ""
)

/**
 * 끼니별 지정 단가 설정 모델
 */
data class MealPriceConfig(
    val breakfastPrice: Long = MealType.BREAKFAST.defaultUnitPrice,
    val lunchPrice: Long = MealType.LUNCH.defaultUnitPrice,
    val dinnerPrice: Long = MealType.DINNER.defaultUnitPrice,
    val snackPrice: Long = MealType.SNACK.defaultUnitPrice
) {
    fun priceFor(mealType: MealType): Long = when (mealType) {
        MealType.BREAKFAST -> breakfastPrice
        MealType.LUNCH -> lunchPrice
        MealType.DINNER -> dinnerPrice
        MealType.SNACK -> snackPrice
        MealType.CUSTOM -> 0L // 직접 입력은 기록할 때 금액을 받음
    }
}

/**
 * [Beta] KB국민은행 무로그인 빠른조회 자격증명 모델
 */
data class QuickInquiryCredentials(
    /** 조회할 은행 ([FinancialInstitutions] ID, 현재 앱 연동은 KB국민은행만 지원) */
    val institutionId: String = DEFAULT_QUICK_INQUIRY_INSTITUTION_ID,
    val accountNumber: String = "",
    val birthOrBizNumber: String = "",
    val quickPassword4Digit: String = ""
) {
    val isConfigured: Boolean
        get() = accountNumber.isNotBlank() && birthOrBizNumber.isNotBlank()
}

const val DEFAULT_QUICK_INQUIRY_INSTITUTION_ID = "KB"

/**
 * 정산 주기 내 집밥 횟수·절약 금액 합계 (위젯용, Room 집계 쿼리 결과)
 */
data class MealPeriodTotal(
    val totalCount: Int,
    val totalSaved: Long
)

/**
 * 집밥 위젯 표시 데이터
 */
data class MealWidgetSummary(
    val spaceName: String,
    val periodLabel: String,
    val totalMealCount: Int,
    val totalSavedAmount: Long
)

/**
 * [SyncWorker] 백그라운드 동기화 결과
 * - DONE: 업로드할 내역이 없거나 모두 반영됨
 * - NOT_ELIGIBLE: 혼자 쓰기 / 공유 꺼짐 / 로그인 안 됨 등으로 동기화 대상이 아님 (재시도 불필요)
 * - RETRY: 동기화 대상인데 아직 반영되지 않은 내역이 남아 있음
 */
enum class SharedSyncOutcome { DONE, NOT_ELIGIBLE, RETRY }
