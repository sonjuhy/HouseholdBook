package com.budget.householdbook.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.budget.householdbook.data.local.dao.CategoryDao
import com.budget.householdbook.data.local.dao.LedgerSpaceDao
import com.budget.householdbook.data.local.dao.MealSavingDao
import com.budget.householdbook.data.local.dao.PendingTransactionDao
import com.budget.householdbook.data.local.dao.RuleDao
import com.budget.householdbook.data.local.dao.TransactionDao
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.data.local.entity.IgnoredKeywordEntity
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.data.local.entity.MealSavingLogEntity
import com.budget.householdbook.data.local.entity.MerchantCategoryRuleEntity
import com.budget.householdbook.data.local.entity.PendingTransactionEntity
import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.domain.model.IngestionSource
import com.budget.householdbook.domain.model.MealType
import com.budget.householdbook.domain.model.QueueKind
import com.budget.householdbook.domain.model.QueueStatus
import com.budget.householdbook.domain.model.SpaceType
import com.budget.householdbook.domain.model.SyncStatus
import com.budget.householdbook.domain.model.TransactionType

class HouseholdTypeConverters {
    @TypeConverter
    fun fromSpaceType(value: SpaceType): String = value.name

    @TypeConverter
    fun toSpaceType(value: String): SpaceType = SpaceType.valueOf(value)

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromIngestionSource(value: IngestionSource): String = value.name

    @TypeConverter
    fun toIngestionSource(value: String): IngestionSource = IngestionSource.valueOf(value)

    @TypeConverter
    fun fromQueueKind(value: QueueKind): String = value.name

    @TypeConverter
    fun toQueueKind(value: String): QueueKind = QueueKind.valueOf(value)

    @TypeConverter
    fun fromQueueStatus(value: QueueStatus): String = value.name

    @TypeConverter
    fun toQueueStatus(value: String): QueueStatus = QueueStatus.valueOf(value)

    @TypeConverter
    fun fromMealType(value: MealType): String = value.name

    @TypeConverter
    fun toMealType(value: String): MealType = MealType.valueOf(value)

    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)
}

@Database(
    entities = [
        LedgerSpaceEntity::class,
        CategoryEntity::class,
        PendingTransactionEntity::class,
        TransactionEntity::class,
        MealSavingLogEntity::class,
        IgnoredKeywordEntity::class,
        MerchantCategoryRuleEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(HouseholdTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ledgerSpaceDao(): LedgerSpaceDao
    abstract fun categoryDao(): CategoryDao
    abstract fun pendingTransactionDao(): PendingTransactionDao
    abstract fun transactionDao(): TransactionDao
    abstract fun mealSavingDao(): MealSavingDao
    abstract fun ruleDao(): RuleDao

    companion object {
        const val SPACE_ID_SHARED = "space_shared_default"
        const val SPACE_ID_PERSONAL = "space_personal_default"
    }
}
