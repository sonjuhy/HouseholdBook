package com.budget.householdbook.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.budget.householdbook.domain.model.CategoryRemoteIds
import com.budget.householdbook.domain.model.IngestionSource
import com.budget.householdbook.domain.model.MealType
import com.budget.householdbook.domain.model.QueueKind
import com.budget.householdbook.domain.model.QueueStatus
import com.budget.householdbook.domain.model.SpaceType
import com.budget.householdbook.domain.model.SyncStatus
import com.budget.householdbook.domain.model.TransactionType

/**
 * 1. 장부 공간 엔티티 (공통 생활비 vs 개인 용돈 분리)
 * - billingStartDay: 월 기준일 (기본 1일, 1..28 또는 말일 99)
 * - targetBudgetAmount: 해당 공간의 전체 목표 금액
 */
@Entity(tableName = "ledger_spaces")
data class LedgerSpaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: SpaceType,
    val billingStartDay: Int = 1,
    val targetBudgetAmount: Long = 1_000_000L,
    val memberNamesCsv: String = "나",
    val inviteCode: String? = null,
    val isRemoteShared: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)

/**
 * 2. 상위 카테고리 & 세부 카테고리 엔티티
 * - parentId가 null이면 상위 카테고리(예: 식비, 주거/통신, 용돈)
 * - parentId가 존재하면 세부 카테고리(예: 배달/외식, 식재료, 카페/간식)
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["spaceId"]), Index(value = ["parentId"]), Index(value = ["remoteId"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val spaceId: String,
    val parentId: Long? = null,
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val name: String,
    val emoji: String = "💳",
    val colorHex: String = "#2563EB",
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    /** 휴대폰마다 다른 id 대신 함께 쓰는 사람끼리 같은 분류를 가리키는 값 ([CategoryRemoteIds]) */
    @ColumnInfo(defaultValue = "") val remoteId: String = CategoryRemoteIds.newId(),
    /** 마지막으로 바꾼 시각 (공용 분류 동기화에서 더 최근 것을 남김, 기본 분류는 0) */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L
)

/**
 * 3. 등록 대기 큐 엔티티 (무조건 즉시 등록이 아닌 대기 큐 경유 필수)
 */
@Entity(
    tableName = "pending_transactions",
    indices = [Index(value = ["dedupHash"], unique = true), Index(value = ["status"])]
)
data class PendingTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sourceType: IngestionSource,
    val sourceAppName: String,
    val rawText: String,
    val parsedAmount: Long,
    val parsedType: TransactionType,
    val parsedMerchant: String,
    val occurredAt: Long,
    val queueKind: QueueKind = QueueKind.NORMAL,
    val matchedTransactionId: Long? = null,
    val pairedQueueId: Long? = null,
    val suggestedSpaceId: String,
    val suggestedCategoryId: Long? = null,
    val suggestedSubCategoryId: Long? = null,
    val status: QueueStatus = QueueStatus.PENDING,
    val dedupHash: String
)

/**
 * 4. 확정 거래 내역 엔티티 (대기 큐 승인 또는 수동 입력)
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["spaceId", "transactionDate"]),
        Index(value = ["originQueueId"]),
        Index(value = ["remoteId"], unique = true)
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val remoteId: String,
    val spaceId: String,
    val type: TransactionType,
    val amount: Long,
    val categoryId: Long,
    val subCategoryId: Long? = null,
    val merchantOrTitle: String,
    val memo: String = "",
    val isCanceled: Boolean = false,
    val originQueueId: Long? = null,
    val authorName: String = "나",
    val transactionDate: Long,
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)

/**
 * 5. 끼니 직접 해먹기(집밥) 절약 카운트 기록 엔티티
 * - 단순 횟수 * 끼니별 지정 금액
 */
@Entity(
    tableName = "meal_saving_logs",
    indices = [Index(value = ["spaceId", "occurredAt"])]
)
data class MealSavingLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val spaceId: String,
    val dateIso: String,
    val mealType: MealType,
    val count: Int = 1,
    val unitAmount: Long,
    val savedAmount: Long,
    val memo: String = "",
    val occurredAt: Long = System.currentTimeMillis()
)

/**
 * 6. 자동 무시 키워드 엔티티 (대기 큐에서 '다음부터 이 키워드 자동 무시' 등록용)
 */
@Entity(tableName = "ignored_keywords")
data class IgnoredKeywordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val keyword: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 7. 가맹점명 -> 세부 카테고리 자동 학습 매핑 엔티티
 */
@Entity(
    tableName = "merchant_category_rules",
    indices = [Index(value = ["merchantKeyword"], unique = true)]
)
data class MerchantCategoryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val merchantKeyword: String,
    val targetSpaceId: String,
    val categoryId: Long,
    val subCategoryId: Long?
)
