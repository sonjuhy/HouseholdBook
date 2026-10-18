package com.budget.householdbook.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.budget.householdbook.domain.repository.BackgroundTaskScheduler
import java.util.concurrent.TimeUnit

/**
 * WorkManager 백그라운드 작업 예약 창구
 * - SyncWorker: 오프라인에서 쌓인 공통 생활비 변경(PENDING_UPLOAD / PENDING_DELETE)을 인터넷 연결 시 업로드
 * - QuickInquiryWorker: [Beta] KB 빠른조회 주기적 자동 갱신
 */
class BackgroundWorkScheduler(private val context: Context) : BackgroundTaskScheduler {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * 동기화 실패(오프라인 등) 시 호출: 인터넷이 연결되는 즉시 1회 업로드를 시도합니다.
     * 이미 예약된 작업이 있으면 그대로 둡니다.
     */
    fun requestSharedSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(SYNC_ONE_TIME, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * 놓친 변경을 보정하기 위한 주기 동기화 (앱 시작 시 1회 등록, 이미 있으면 유지)
     */
    override fun ensurePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIODIC_SYNC_HOURS, TimeUnit.HOURS)
            .setConstraints(networkConstraint)
            .build()
        workManager.enqueueUniquePeriodicWork(SYNC_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * KB 빠른조회 자동 갱신 켜기/끄기 및 주기 변경
     */
    override fun applyKbAutoInquiry(enabled: Boolean, intervalHours: Int) {
        if (!enabled) {
            workManager.cancelUniqueWork(KB_QUICK_INQUIRY)
            return
        }
        val request = PeriodicWorkRequestBuilder<QuickInquiryWorker>(
            intervalHours.coerceIn(MIN_KB_INTERVAL_HOURS, MAX_KB_INTERVAL_HOURS).toLong(),
            TimeUnit.HOURS
        )
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        // 주기가 바뀌었을 수 있으므로 UPDATE로 기존 예약을 갱신
        workManager.enqueueUniquePeriodicWork(KB_QUICK_INQUIRY, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    companion object {
        const val SYNC_ONE_TIME = "household_shared_sync_once"
        const val SYNC_PERIODIC = "household_shared_sync_periodic"
        const val KB_QUICK_INQUIRY = "household_kb_quick_inquiry"

        private const val PERIODIC_SYNC_HOURS = 6L
        const val MIN_KB_INTERVAL_HOURS = 1
        const val MAX_KB_INTERVAL_HOURS = 24

        /** 설정 화면에서 고를 수 있는 KB 자동 조회 주기(시간) */
        val KB_INTERVAL_OPTIONS = listOf(3, 6, 12, 24)
    }
}
