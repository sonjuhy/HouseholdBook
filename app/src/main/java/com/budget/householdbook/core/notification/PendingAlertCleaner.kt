package com.budget.householdbook.core.notification

import com.budget.householdbook.domain.repository.AppNotifier
import com.budget.householdbook.domain.repository.PendingQueueRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 대기내역에서 처리(저장·분할·취소 반영·삭제·일괄 승인)되어 사라진 항목의 '새 결제' 알림을 지움
 * - 어느 화면·어떤 방법으로 처리했든 대기내역 목록만 보고 맞추므로 처리 경로마다 따로 지울 필요 없음
 */
class PendingAlertCleaner(
    private val pendingQueueRepository: PendingQueueRepository,
    private val notifier: AppNotifier
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            pendingQueueRepository.observePendingQueue()
                .map { list -> list.map { it.id }.toSet() }
                .distinctUntilChanged()
                .collect { ids -> runCatching { notifier.cancelPendingExcept(ids) } }
        }
    }
}
