package com.budget.householdbook.core.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.budget.householdbook.MainActivity
import com.budget.householdbook.R
import com.budget.householdbook.core.common.CurrencyAndDateFormatter
import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.domain.model.BudgetAlert
import com.budget.householdbook.domain.model.BudgetAlertLevel
import com.budget.householdbook.domain.model.CollectionStopReason
import com.budget.householdbook.domain.model.PendingPaymentAlert
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.domain.repository.AppNotifier

/**
 * 휴대폰 알림 보내기
 * - 채널을 종류별로 나눠 사용자가 Android 설정에서 따로 끌 수 있게 함 (결제 대기 / 예산 / 자동 수집 상태)
 * - 금액·가게 이름은 금융 정보라 잠금화면에서는 내용 없이 보여줌 (VISIBILITY_PRIVATE + 공개용 문구)
 * - 알림 권한이 없으면 조용히 아무것도 하지 않음
 */
class AndroidAppNotifier(private val context: Context) : AppNotifier {

    private val manager = NotificationManagerCompat.from(context)

    init {
        createChannels()
    }

    fun canNotify(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    // ---------------- 새 결제 대기 ----------------

    override fun notifyPendingPayment(alert: PendingPaymentAlert) {
        val amountText = when (alert.type) {
            TransactionType.INCOME -> "+${CurrencyAndDateFormatter.formatWon(alert.amount)}"
            TransactionType.TRANSFER -> "이체 ${CurrencyAndDateFormatter.formatWon(alert.amount)}"
            TransactionType.EXPENSE -> "-${CurrencyAndDateFormatter.formatWon(alert.amount)}"
        }
        val builder = base(CHANNEL_PENDING)
            .setContentTitle(alert.merchant)
            .setContentText(
                if (alert.canQuickSave) "$amountText · 어디에 저장할까요?" else "$amountText · 대기내역에서 확인해 주세요"
            )
            .setContentIntent(openAppIntent(MainActivity.TAB_QUEUE, requestCode = alert.pendingId.toInt()))
            .setGroup(GROUP_PENDING)
            .setPublicVersion(publicVersion(CHANNEL_PENDING, "새 결제 1건", "대기내역에서 확인해 주세요"))
        if (alert.canQuickSave) {
            builder.addAction(0, "공용에 저장", quickSaveIntent(alert.pendingId, AppDatabase.SPACE_ID_SHARED))
            builder.addAction(0, "개인에 저장", quickSaveIntent(alert.pendingId, AppDatabase.SPACE_ID_PERSONAL))
        }
        post(TAG_PENDING, alert.pendingId.toInt(), builder)
        refreshPendingSummary()
    }

    override fun notifyPendingBatch(count: Int) {
        if (count <= 0) return
        val builder = base(CHANNEL_PENDING)
            .setContentTitle("새 결제 ${count}건")
            .setContentText("은행 내역에서 불러왔어요. 대기내역에서 확인해 주세요.")
            .setContentIntent(openAppIntent(MainActivity.TAB_QUEUE, requestCode = REQUEST_BATCH))
            .setPublicVersion(publicVersion(CHANNEL_PENDING, "새 결제 ${count}건", "대기내역에서 확인해 주세요"))
        post(TAG_PENDING_BATCH, 0, builder)
    }

    override fun cancelPendingExcept(activePendingIds: Set<Long>) {
        val active = activeNotifications()
        active.filter { it.tag == TAG_PENDING && it.id.toLong() !in activePendingIds }
            .forEach { manager.cancel(TAG_PENDING, it.id) }
        if (activePendingIds.isEmpty()) manager.cancel(TAG_PENDING_BATCH, 0)
        refreshPendingSummary()
    }

    /** 결제 알림이 2건 이상이면 '확인할 결제 N건' 묶음 알림 */
    private fun refreshPendingSummary() {
        val count = activeNotifications().count { it.tag == TAG_PENDING }
        if (count < 2) {
            manager.cancel(TAG_PENDING_SUMMARY, 0)
            return
        }
        val builder = base(CHANNEL_PENDING)
            .setContentTitle("확인할 결제 ${count}건")
            .setContentText("대기내역에서 확인해 주세요")
            .setContentIntent(openAppIntent(MainActivity.TAB_QUEUE, requestCode = REQUEST_SUMMARY))
            .setGroup(GROUP_PENDING)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .setPublicVersion(publicVersion(CHANNEL_PENDING, "확인할 결제 ${count}건", "대기내역에서 확인해 주세요"))
        post(TAG_PENDING_SUMMARY, 0, builder)
    }

    /** 알림 버튼으로 저장한 뒤: 해당 알림을 '저장했어요'로 바꿨다가 잠시 후 사라지게 */
    fun showQuickSaved(pendingId: Long, spaceName: String) {
        val builder = base(CHANNEL_PENDING)
            .setContentTitle("${spaceName}에 저장했어요")
            .setContentText("장부에서 확인할 수 있어요")
            .setContentIntent(openAppIntent(MainActivity.TAB_LEDGER, requestCode = pendingId.toInt()))
            .setGroup(GROUP_PENDING)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setTimeoutAfter(QUICK_SAVED_TIMEOUT_MILLIS)
        post(TAG_PENDING, pendingId.toInt(), builder)
    }

    fun cancelPending(pendingId: Long) {
        manager.cancel(TAG_PENDING, pendingId.toInt())
        refreshPendingSummary()
    }

    // ---------------- 예산 ----------------

    override fun notifyBudget(alert: BudgetAlert) {
        val won = CurrencyAndDateFormatter::formatWon
        val (title, text) = when (alert.level) {
            BudgetAlertLevel.EXCEEDED -> "${alert.spaceName} 예산을 넘었어요" to
                "목표 ${won(alert.target)}보다 ${won(-alert.remaining)} 더 썼어요"
            else -> "${alert.spaceName} 예산의 ${alert.level.percent}%를 썼어요" to buildString {
                append("남은 예산 ${won(alert.remaining)}")
                if (alert.remainingDays > 0) append(" · 하루 ${won(alert.remaining / alert.remainingDays)} 쓸 수 있어요")
            }
        }
        val builder = base(CHANNEL_BUDGET)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(MainActivity.TAB_HOME, requestCode = REQUEST_BUDGET + alert.spaceId.hashCode()))
            .setPublicVersion(publicVersion(CHANNEL_BUDGET, "예산 알림", "앱에서 확인해 주세요"))
        post(TAG_BUDGET, alert.spaceId.hashCode(), builder)
    }

    // ---------------- 자동 수집 멈춤 ----------------

    override fun notifyCollectionStopped(reason: CollectionStopReason) {
        val builder = base(CHANNEL_STATUS)
        when (reason) {
            CollectionStopReason.NOTIFICATION_ACCESS_OFF -> {
                val text = "결제 알림 읽기 권한이 꺼져 있어요. 눌러서 다시 켜 주세요."
                builder.setContentTitle("결제 자동 기록이 멈췄어요")
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(
                        PendingIntent.getActivity(
                            context,
                            REQUEST_STATUS,
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
            }
            CollectionStopReason.BANK_INQUIRY_FAILING -> {
                val text = "은행 내역 자동 불러오기가 계속 실패하고 있어요. 계좌 정보를 확인해 주세요."
                builder.setContentTitle("은행 내역을 불러오지 못하고 있어요")
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(openAppIntent(MainActivity.TAB_SETTINGS, requestCode = REQUEST_STATUS + 1))
            }
        }
        post(TAG_STATUS, reason.ordinal, builder)
    }

    override fun cancelCollectionStopped(reason: CollectionStopReason) {
        manager.cancel(TAG_STATUS, reason.ordinal)
    }

    // ---------------- 공통 ----------------

    private fun base(channelId: String) = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(ContextCompat.getColor(context, R.color.notification_accent))
        .setAutoCancel(true)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)

    private fun publicVersion(channelId: String, title: String, text: String) =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .build()

    @SuppressLint("MissingPermission") // canNotify()로 확인
    private fun post(tag: String, id: Int, builder: NotificationCompat.Builder) {
        if (!canNotify()) return
        runCatching { manager.notify(tag, id, builder.build()) }
    }

    private fun activeNotifications() = runCatching {
        context.getSystemService(NotificationManager::class.java).activeNotifications.toList()
    }.getOrDefault(emptyList())

    private fun openAppIntent(tab: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TAB, tab)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun quickSaveIntent(pendingId: Long, spaceId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            // 같은 알림의 두 버튼이 서로 덮어쓰지 않도록 공간마다 다른 요청 코드
            (pendingId * 2 + if (spaceId == AppDatabase.SPACE_ID_SHARED) 0 else 1).toInt(),
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(NotificationActionReceiver.ACTION_QUICK_SAVE)
                .putExtra(NotificationActionReceiver.EXTRA_PENDING_ID, pendingId)
                .putExtra(NotificationActionReceiver.EXTRA_SPACE_ID, spaceId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun createChannels() {
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        system.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_PENDING, "새 결제", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "결제 알림·문자가 대기내역에 들어오면 알려주고, 알림에서 바로 저장할 수 있어요"
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                },
                NotificationChannel(CHANNEL_BUDGET, "예산", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "정산 주기마다 예산의 80%, 100%를 쓰면 한 번씩 알려줘요"
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                },
                NotificationChannel(CHANNEL_STATUS, "자동 기록 상태", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "결제 알림 읽기 권한이 꺼지거나 은행 내역 불러오기가 계속 실패하면 알려줘요"
                }
            )
        )
    }

    companion object {
        const val CHANNEL_PENDING = "pending_payment"
        const val CHANNEL_BUDGET = "budget"
        const val CHANNEL_STATUS = "collection_status"

        private const val TAG_PENDING = "pending"
        private const val TAG_PENDING_SUMMARY = "pending_summary"
        private const val TAG_PENDING_BATCH = "pending_batch"
        private const val TAG_BUDGET = "budget"
        private const val TAG_STATUS = "status"
        private const val GROUP_PENDING = "com.budget.householdbook.PENDING"

        private const val REQUEST_SUMMARY = -1
        private const val REQUEST_BATCH = -2
        private const val REQUEST_STATUS = -10
        private const val REQUEST_BUDGET = 100_000

        private const val QUICK_SAVED_TIMEOUT_MILLIS = 4_000L
    }
}
