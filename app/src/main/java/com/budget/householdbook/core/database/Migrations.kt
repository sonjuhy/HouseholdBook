package com.budget.householdbook.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 스키마 버전별 마이그레이션
 *
 * 규칙
 * - 엔티티(테이블·컬럼·인덱스)를 바꾸면 AppDatabase.version을 1 올리고, 여기에 MIGRATION_n_(n+1)을 추가합니다.
 * - 스키마 JSON(app/schemas/)이 자동으로 생성되므로 함께 커밋하고, MigrationTest에 검증을 추가합니다.
 * - 사용자 데이터를 지우는 fallbackToDestructiveMigration은 쓰지 않습니다.
 */
object Migrations {

    /**
     * v1 → v2: 거래 원격 ID(remoteId) 고유 인덱스 추가
     * - Firebase 동기화가 remoteId로 거래를 찾고 덮어쓰므로 중복을 막아야 함
     * - 혹시 이미 중복된 remoteId가 있으면 지우지 않고 뒤에 로컬 ID를 붙여 구분한 뒤 인덱스를 만듦
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                UPDATE transactions
                SET remoteId = remoteId || '-' || id
                WHERE id NOT IN (SELECT MIN(id) FROM transactions GROUP BY remoteId)
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_remoteId` ON `transactions` (`remoteId`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
