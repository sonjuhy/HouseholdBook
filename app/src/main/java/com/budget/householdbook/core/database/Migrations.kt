package com.budget.householdbook.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.budget.householdbook.domain.model.CategoryRemoteIds

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

    /**
     * v2 → v3: 분류 원격 ID(remoteId) · 수정 시각(updatedAt) 추가 (공용 분류 동기화)
     * - 이미 있는 분류는 장부·대분류 이름·이름으로 정해지는 값을 넣어 두 사람의 같은 기본 분류가 하나로 합쳐지게 함
     * - 혹시 같은 값이 겹치면(같은 이름 분류가 둘) 뒤에 로컬 id를 붙여 구분
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE categories ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE categories ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")

            val used = mutableSetOf<String>()
            val assigned = mutableListOf<Pair<Long, String>>()
            db.query(
                """
                SELECT c.id, c.spaceId, c.name, p.name
                FROM categories c LEFT JOIN categories p ON c.parentId = p.id
                ORDER BY c.isArchived ASC, c.id ASC
                """.trimIndent()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val parentName = if (cursor.isNull(3)) null else cursor.getString(3)
                    val named = CategoryRemoteIds.named(cursor.getString(1), parentName, cursor.getString(2))
                    val remoteId = if (used.add(named)) named else "$named-$id".also { used.add(it) }
                    assigned += id to remoteId
                }
            }
            assigned.forEach { (id, remoteId) ->
                db.execSQL("UPDATE categories SET remoteId = ? WHERE id = ?", arrayOf<Any>(remoteId, id))
            }
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_remoteId` ON `categories` (`remoteId`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
