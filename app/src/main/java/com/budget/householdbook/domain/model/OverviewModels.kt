package com.budget.householdbook.domain.model

import com.budget.householdbook.core.common.BillingPeriod
import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.core.datastore.AppUserSettings
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.data.local.entity.IgnoredKeywordEntity
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.data.local.entity.MealSavingLogEntity
import com.budget.householdbook.data.local.entity.PendingTransactionEntity
import com.budget.householdbook.data.local.entity.TransactionEntity

/**
 * 공간 목록이 아직 비어 있을 때(최초 실행 직후) 사용하는 기본 공통 생활비 공간
 */
val FALLBACK_SHARED_SPACE = LedgerSpaceEntity(
    id = AppDatabase.SPACE_ID_SHARED,
    name = "우리집 공통 생활비",
    type = SpaceType.SHARED,
    billingStartDay = 1,
    targetBudgetAmount = 1_500_000L
)

/**
 * 상위 카테고리와 그에 속한 세부 카테고리 묶음
 */
data class CategoryGroup(
    val parent: CategoryEntity,
    val subCategories: List<CategoryEntity>
)

/**
 * 상위 카테고리별 지출 합계 및 세부 카테고리 내역 요약
 */
data class CategorySpendSummary(
    val parentCategory: CategoryEntity,
    val totalSpent: Long,
    val percentageOfTotal: Float,
    val subBreakdown: List<Pair<String, Long>>
)

/**
 * 정산 주기 기준 목표 예산 대비 지출 요약 (대시보드 핵심 지표)
 */
data class BudgetSummary(
    val totalSpentAmount: Long = 0L,
    val totalIncomeAmount: Long = 0L,
    val totalTransferAmount: Long = 0L,
    val remainingBudgetAmount: Long = 0L,
    val budgetUsageRatio: Float = 0f,
    val recommendedDailyBudget: Long = 0L,
    val categorySpendSummaries: List<CategorySpendSummary> = emptyList()
)

/**
 * 현재 공간·정산 주기 기준으로 화면에 필요한 모든 데이터를 묶은 스냅샷
 */
data class HouseholdOverview(
    val settings: AppUserSettings,
    val spaces: List<LedgerSpaceEntity>,
    val currentSpace: LedgerSpaceEntity,
    val monthOffset: Int,
    val billingPeriod: BillingPeriod,
    val allCategories: List<CategoryEntity>,
    val currentSpaceCategoryGroups: List<CategoryGroup>,
    val pendingQueue: List<PendingTransactionEntity>,
    val transactions: List<TransactionEntity>,
    val mealLogs: List<MealSavingLogEntity>,
    val ignoredKeywords: List<IgnoredKeywordEntity>,
    val kbCredentials: QuickInquiryCredentials,
    val syncStatusMessage: String,
    val budget: BudgetSummary,
    val totalMealCount: Int,
    val totalMealSavedAmount: Long
)

/**
 * 대기 큐 일괄 승인 결과
 * @property processedCount 이번에 저장(또는 취소 반영)한 건수
 * @property remainingCount 직접 확인이 필요해 대기 목록에 남은 건수 (원본을 못 찾은 취소 알림 등)
 */
data class BulkApprovalResult(
    val processedCount: Int,
    val remainingCount: Int
)
