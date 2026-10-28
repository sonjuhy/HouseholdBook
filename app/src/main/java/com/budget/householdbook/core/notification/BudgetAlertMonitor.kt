package com.budget.householdbook.core.notification

import com.budget.householdbook.core.common.BillingCycleCalculator
import com.budget.householdbook.domain.repository.LedgerSpaceRepository
import com.budget.householdbook.domain.repository.TransactionRepository
import com.budget.householdbook.domain.usecase.CheckBudgetAlertUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * 예산 80% · 100% 알림 감시 (앱 프로세스가 살아 있는 동안)
 * - 거래가 어디서 바뀌든(대기내역 저장, 직접 입력, 수정, 알림 버튼 저장, 함께 쓰는 멤버의 기록) 해당 공간을 다시 확인
 * - 정산 주기가 바뀌는 날에도 맞도록 한 시간마다 주기를 다시 계산
 * - 실제로 보낼지(주기·단계당 한 번)는 [CheckBudgetAlertUseCase]가 결정
 */
class BudgetAlertMonitor(
    private val spaceRepository: LedgerSpaceRepository,
    private val transactionRepository: TransactionRepository,
    private val checkBudgetAlert: CheckBudgetAlertUseCase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(spaceRepository.observeAllSpaces(), hourlyTick()) { spaces, _ -> spaces }
                .flatMapLatest { spaces ->
                    if (spaces.isEmpty()) return@flatMapLatest emptyFlow()
                    spaces.map { space ->
                        val period = BillingCycleCalculator.calculatePeriod(billingStartDay = space.billingStartDay)
                        transactionRepository
                            .observeTransactionsInPeriod(space.id, period.startMillis, period.endExclusiveMillis)
                            .map { space.id }
                    }.merge()
                }
                .collect { spaceId -> runCatching { checkBudgetAlert(spaceId) } }
        }
    }

    private fun hourlyTick() = flow {
        while (true) {
            emit(Unit)
            delay(60 * 60 * 1000L)
        }
    }
}
