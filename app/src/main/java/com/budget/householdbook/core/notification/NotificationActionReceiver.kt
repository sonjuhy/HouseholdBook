package com.budget.householdbook.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.budget.householdbook.domain.repository.LedgerSpaceRepository
import com.budget.householdbook.domain.usecase.CheckBudgetAlertUseCase
import com.budget.householdbook.domain.usecase.QuickSavePendingUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 새 결제 알림의 [공용에 저장] / [개인에 저장] 버튼
 * - 앱을 열지 않고 대기 항목을 장부에 저장한 뒤, 알림을 '저장했어요'로 바꿔 잠시 보여줌
 * - 앱에서 이미 처리한 항목이면 알림만 지움
 */
class NotificationActionReceiver : BroadcastReceiver(), KoinComponent {

    private val quickSavePending: QuickSavePendingUseCase by inject()
    private val checkBudgetAlert: CheckBudgetAlertUseCase by inject()
    private val spaceRepository: LedgerSpaceRepository by inject()
    private val notifier: AndroidAppNotifier by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_QUICK_SAVE) return
        val pendingId = intent.getLongExtra(EXTRA_PENDING_ID, -1L)
        val spaceId = intent.getStringExtra(EXTRA_SPACE_ID) ?: return
        if (pendingId <= 0L) return

        // 저장(DB·동기화)이 끝날 때까지 리시버를 살려 둠
        val result = goAsync()
        scope.launch {
            try {
                val saved = runCatching { quickSavePending(pendingId, spaceId) }.getOrDefault(false)
                if (saved) {
                    val spaceName = spaceRepository.getSpaceById(spaceId)?.name ?: "장부"
                    notifier.showQuickSaved(pendingId, spaceName)
                    runCatching { checkBudgetAlert(spaceId) }
                } else {
                    notifier.cancelPending(pendingId)
                }
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val ACTION_QUICK_SAVE = "com.budget.householdbook.action.QUICK_SAVE_PENDING"
        const val EXTRA_PENDING_ID = "pending_id"
        const val EXTRA_SPACE_ID = "space_id"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
