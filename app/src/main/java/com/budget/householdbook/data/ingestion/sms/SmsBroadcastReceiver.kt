package com.budget.householdbook.data.ingestion.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.budget.householdbook.domain.model.IngestionSource
import com.budget.householdbook.domain.usecase.IngestRawNotificationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 카드사 승인/취소 및 은행 입출금 SMS/LMS 문자 메시지를 수신하여
 * 등록 대기 큐(PendingQueue)에 적재하는 리시버
 */
class SmsBroadcastReceiver : BroadcastReceiver(), KoinComponent {

    private val ingestRawNotification: IngestRawNotificationUseCase by inject()
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages.firstOrNull()?.displayOriginatingAddress ?: "SMS"
        val combinedBody = messages.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        if (combinedBody.isBlank()) return

        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                ingestRawNotification(
                    sourceType = IngestionSource.SMS,
                    sourceAppName = "문자($sender)",
                    title = "",
                    body = combinedBody
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
