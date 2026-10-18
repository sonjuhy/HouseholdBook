package com.budget.householdbook.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.budget.householdbook.domain.model.SharedSyncOutcome
import com.budget.householdbook.domain.usecase.SyncPendingSharedChangesUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * [Pro] 오프라인에서 쌓인 공통 생활비 변경 내역을 인터넷 연결 시 Firestore에 반영하는 작업
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val syncPendingSharedChanges: SyncPendingSharedChangesUseCase by inject()

    override suspend fun doWork(): Result {
        return when (syncPendingSharedChanges()) {
            SharedSyncOutcome.DONE, SharedSyncOutcome.NOT_ELIGIBLE -> Result.success()
            // 일시적 실패(서버 응답 없음 등)는 지수 백오프로 재시도하되 무한 반복은 방지
            SharedSyncOutcome.RETRY -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 8
    }
}
