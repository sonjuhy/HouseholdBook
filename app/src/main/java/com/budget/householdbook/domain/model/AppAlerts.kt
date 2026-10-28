package com.budget.householdbook.domain.model

/**
 * 앱이 휴대폰 알림으로 알려주는 일 (1차: 새 결제 대기 · 예산 80%/100% · 자동 수집 멈춤)
 * - 사용자가 무언가를 해야 할 때만 알림 (저장 완료 같은 확인은 화면 안 스낵바로 충분)
 */

/** 대기내역에 새로 들어온 결제 한 건 */
data class PendingPaymentAlert(
    val pendingId: Long,
    val merchant: String,
    val amount: Long,
    val type: TransactionType,
    /** 알림의 [공용에 저장] [개인에 저장] 버튼으로 바로 저장해도 되는 항목인지 (취소·이체는 앱에서 확인) */
    val canQuickSave: Boolean
)

/** 예산 알림 단계: 정산 주기마다 80%, 100%에서 한 번씩 */
enum class BudgetAlertLevel(val percent: Int) {
    NONE(0),
    WARNING(80),
    EXCEEDED(100);

    companion object {
        fun of(spent: Long, target: Long): BudgetAlertLevel = when {
            target <= 0L -> NONE
            spent * 100 >= target * EXCEEDED.percent -> EXCEEDED
            spent * 100 >= target * WARNING.percent -> WARNING
            else -> NONE
        }
    }
}

data class BudgetAlert(
    val spaceId: String,
    val spaceName: String,
    val level: BudgetAlertLevel,
    val spent: Long,
    val target: Long,
    val remainingDays: Int
) {
    val remaining: Long get() = target - spent
}

/** 마지막으로 보낸 예산 알림 (같은 주기에 같은 단계를 다시 보내지 않기 위함) */
data class BudgetAlertRecord(
    /** 정산 주기 시작일 (yyyy-MM-dd) */
    val periodStart: String,
    val level: BudgetAlertLevel
)

/** 결제 내역 자동 수집이 멈춘 이유 */
enum class CollectionStopReason {
    /** 결제 알림 읽기 권한이 꺼짐 */
    NOTIFICATION_ACCESS_OFF,

    /** 은행 계좌 자동 불러오기가 계속 실패 */
    BANK_INQUIRY_FAILING
}

/** 자동 수집 상태 알림을 위한 기록 */
data class CollectionAlertState(
    /** 알림 읽기 권한이 한 번이라도 켜져 있었는지 (처음부터 안 쓰는 사람에게는 알리지 않음) */
    val notificationAccessEverEnabled: Boolean = false,
    val notificationAccessAlertSent: Boolean = false,
    /** 은행 자동 불러오기 연속 실패 횟수 */
    val bankInquiryFailures: Int = 0,
    val bankInquiryAlertSent: Boolean = false
)
