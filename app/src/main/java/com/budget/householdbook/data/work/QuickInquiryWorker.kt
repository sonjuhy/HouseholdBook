package com.budget.householdbook.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.budget.householdbook.domain.usecase.IsKbAutoInquiryReadyUseCase
import com.budget.householdbook.domain.usecase.RunKbQuickInquiryUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * [Beta] KB국민은행 무로그인 빠른조회를 주기적으로 실행해 새 내역을 대기 큐에 적재하는 작업
 * - 결과는 대기내역 탭 배지로 확인 (장부에 바로 저장하지 않음)
 */
class QuickInquiryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val isKbAutoInquiryReady: IsKbAutoInquiryReadyUseCase by inject()
    private val runKbQuickInquiry: RunKbQuickInquiryUseCase by inject()

    override suspend fun doWork(): Result {
        if (!isKbAutoInquiryReady()) return Result.success()

        return runKbQuickInquiry().fold(
            onSuccess = { Result.success() },
            // 은행 페이지 응답 오류 등은 다음 주기까지 기다리지 않고 몇 번만 재시도
            onFailure = { if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure() }
        )
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
