package com.budget.householdbook.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.budget.householdbook.domain.repository.AppNotifier
import com.budget.householdbook.domain.usecase.IsKbAutoInquiryReadyUseCase
import com.budget.householdbook.domain.usecase.RecordBankInquiryResultUseCase
import com.budget.householdbook.domain.usecase.RunKbQuickInquiryUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * [Beta] KB국민은행 무로그인 빠른조회를 주기적으로 실행해 새 내역을 대기 큐에 적재하는 작업
 * - 결과는 대기내역 탭 배지와 '새 결제 N건' 알림으로 확인 (장부에 바로 저장하지 않음)
 * - 회차가 연속으로 실패하면 '은행 내역을 불러오지 못하고 있어요' 알림
 */
class QuickInquiryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val isKbAutoInquiryReady: IsKbAutoInquiryReadyUseCase by inject()
    private val runKbQuickInquiry: RunKbQuickInquiryUseCase by inject()
    private val recordBankInquiryResult: RecordBankInquiryResultUseCase by inject()
    private val notifier: AppNotifier by inject()

    override suspend fun doWork(): Result {
        if (!isKbAutoInquiryReady()) return Result.success()

        return runKbQuickInquiry().fold(
            onSuccess = { added ->
                recordBankInquiryResult(success = true)
                // 앱을 보고 있지 않을 때 들어온 새 내역 알림
                notifier.notifyPendingBatch(added)
                Result.success()
            },
            // 은행 페이지 응답 오류 등은 다음 주기까지 기다리지 않고 몇 번만 재시도
            onFailure = {
                if (runAttemptCount < MAX_ATTEMPTS) {
                    Result.retry()
                } else {
                    // 재시도까지 모두 실패한 회차만 '연속 실패'로 셈 (여러 회차 연속이면 알림)
                    recordBankInquiryResult(success = false)
                    Result.failure()
                }
            }
        )
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
