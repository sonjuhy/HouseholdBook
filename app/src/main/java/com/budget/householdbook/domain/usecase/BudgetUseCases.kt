package com.budget.householdbook.domain.usecase

import com.budget.householdbook.core.common.BillingCycleCalculator
import com.budget.householdbook.core.common.BillingPeriod
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.domain.model.BudgetSummary
import com.budget.householdbook.domain.model.CategorySpendSummary
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.domain.repository.LedgerSpaceRepository

/**
 * 공간별 전체 목표 금액 및 월 정산 시작 기준일(1일~28일 또는 말일) 변경
 */
class UpdateSpaceBudgetUseCase(private val spaceRepository: LedgerSpaceRepository) {
    suspend operator fun invoke(spaceId: String, targetBudgetAmount: Long, billingStartDay: Int) {
        spaceRepository.updateBudgetAndCycle(
            spaceId = spaceId,
            targetAmount = targetBudgetAmount.coerceAtLeast(0L),
            billingStartDay = BillingCycleCalculator.sanitizeStartDay(billingStartDay)
        )
    }
}

/**
 * 정산 주기 내 확정 거래로 목표 예산 대비 지출 요약을 계산합니다 (순수 함수).
 * - 지출 합계는 취소되지 않은 EXPENSE만 (TRANSFER·미승인 대기 항목 제외)
 * - 오늘 권장 지출액 = 남은 금액 ÷ 정산일까지 남은 일수
 * - 현재 공간의 지출 상위 카테고리별 비중과 세부 카테고리 내역 (지출 큰 순)
 */
class CalculateBudgetSummaryUseCase {
    operator fun invoke(
        space: LedgerSpaceEntity,
        period: BillingPeriod,
        transactions: List<TransactionEntity>,
        allCategories: List<CategoryEntity>
    ): BudgetSummary {
        val activeTxs = transactions.filter { !it.isCanceled }
        val expenseTxs = activeTxs.filter { it.type == TransactionType.EXPENSE }
        val spent = expenseTxs.sumOf { it.amount }
        val income = activeTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val transfer = activeTxs.filter { it.type == TransactionType.TRANSFER }.sumOf { it.amount }

        val target = space.targetBudgetAmount
        val remaining = target - spent
        val usageRatio = if (target > 0L) (spent.toFloat() / target.toFloat()) else 0f
        val dailyRecommended = if (remaining > 0L && period.remainingDays > 0) {
            remaining / period.remainingDays
        } else {
            0L
        }

        val catMap = allCategories.associateBy { it.id }
        val summaries = allCategories
            .filter { it.spaceId == space.id && it.parentId == null && it.transactionType == TransactionType.EXPENSE }
            .mapNotNull { parent ->
                val parentTxs = expenseTxs.filter { it.categoryId == parent.id }
                val sum = parentTxs.sumOf { it.amount }
                if (sum <= 0L) return@mapNotNull null
                val pct = if (spent > 0L) (sum.toFloat() / spent.toFloat()) * 100f else 0f
                val subBreakdown = parentTxs
                    .groupBy { it.subCategoryId }
                    .map { (subId, list) ->
                        val subName = subId?.let { catMap[it]?.name } ?: "기타(${parent.name})"
                        subName to list.sumOf { it.amount }
                    }
                    .sortedByDescending { it.second }
                CategorySpendSummary(
                    parentCategory = parent,
                    totalSpent = sum,
                    percentageOfTotal = pct,
                    subBreakdown = subBreakdown
                )
            }
            .sortedByDescending { it.totalSpent }

        return BudgetSummary(
            totalSpentAmount = spent,
            totalIncomeAmount = income,
            totalTransferAmount = transfer,
            remainingBudgetAmount = remaining,
            budgetUsageRatio = usageRatio,
            recommendedDailyBudget = dailyRecommended,
            categorySpendSummaries = summaries
        )
    }
}
