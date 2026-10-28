package com.budget.householdbook.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.budget.householdbook.data.ingestion.notification.FinancialNotificationListenerService
import com.budget.householdbook.domain.usecase.CheckCollectionStatusUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 결제 알림 읽기 권한이 꺼져 자동 기록이 멈췄는지 주기적으로 확인 (12시간마다)
 * - 한 번이라도 켜서 쓰던 사람에게만, 꺼진 동안 한 번만 알림
 */
class StatusCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val checkCollectionStatus: CheckCollectionStatusUseCase by inject()

    override suspend fun doWork(): Result {
        checkCollectionStatus(
            notificationAccessEnabled = FinancialNotificationListenerService.isNotificationAccessGranted(applicationContext),
            notify = true
        )
        return Result.success()
    }
}
