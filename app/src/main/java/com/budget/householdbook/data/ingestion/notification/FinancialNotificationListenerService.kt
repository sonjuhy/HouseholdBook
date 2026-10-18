package com.budget.householdbook.data.ingestion.notification

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.budget.householdbook.data.ingestion.parser.FinancialParserEngine
import com.budget.householdbook.domain.model.IngestionSource
import com.budget.householdbook.domain.usecase.IngestRawNotificationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * KB스타뱅킹, KB Pay, 토스(Toss), 카카오뱅크 등 금융 앱의 푸시 알림을 실시간 감지하여
 * 즉시 장부에 등록하지 않고 등록 대기 큐(PendingQueue)에 적재하는 서비스
 */
class FinancialNotificationListenerService : NotificationListenerService() {

    private val ingestRawNotification: IngestRawNotificationUseCase by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        val packageName = notification.packageName ?: return

        // 본인 앱 알림 무시
        if (packageName == applicationContext.packageName) return

        // 지원되는 금융/결제 패키지인지 확인
        if (!FinancialParserEngine.isSupportedFinancialPackage(packageName)) return

        val extras = notification.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val body = bigText.ifBlank { text }

        if (title.isBlank() && body.isBlank()) return

        val appName = FinancialParserEngine.resolveAppName(packageName)
        serviceScope.launch {
            ingestRawNotification(
                sourceType = IngestionSource.APP_NOTIFICATION,
                sourceAppName = appName,
                title = title,
                body = body
            )
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Android 13~15+ 백그라운드 프로세스 재시작 시 알림 리스너 자동 재바인딩 요청
        runCatching {
            requestRebind(ComponentName(this, FinancialNotificationListenerService::class.java))
        }
    }

    companion object {
        fun isNotificationAccessGranted(context: Context): Boolean {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ).orEmpty()
            return enabledListeners.contains(context.packageName)
        }
    }
}
