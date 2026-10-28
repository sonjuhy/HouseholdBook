package com.budget.householdbook.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.budget.householdbook.domain.model.CategoryRemoteIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 스키마 버전 업 시 사용자 데이터가 보존되는지 검증
 * - app/schemas/의 버전별 JSON을 기준으로 이전 버전 DB를 만들고 마이그레이션을 실행합니다.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun v1에서_v2로_올려도_거래가_보존되고_중복_remoteId는_구분된다() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO ledger_spaces (id, name, type, billingStartDay, targetBudgetAmount, memberNamesCsv, inviteCode, isRemoteShared, syncStatus)
                VALUES ('space_shared_default', '우리집 공통 생활비', 'SHARED', 25, 1500000, '나', NULL, 0, 'SYNCED')
                """.trimIndent()
            )
            insertTransaction(id = 1, remoteId = "r-1", amount = 18_900)
            insertTransaction(id = 2, remoteId = "r-dup", amount = 52_000)
            // v1에는 고유 인덱스가 없어 중복 remoteId가 있을 수 있음
            insertTransaction(id = 3, remoteId = "r-dup", amount = 7_000)
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, Migrations.MIGRATION_1_2)

        db.query("SELECT id, remoteId, amount FROM transactions ORDER BY id").use { cursor ->
            assertEquals(3, cursor.count)
            val rows = buildList {
                while (cursor.moveToNext()) add(Triple(cursor.getLong(0), cursor.getString(1), cursor.getLong(2)))
            }
            assertEquals(Triple(1L, "r-1", 18_900L), rows[0])
            assertEquals(Triple(2L, "r-dup", 52_000L), rows[1])
            assertEquals(Triple(3L, "r-dup-3", 7_000L), rows[2])
        }
        db.query("SELECT billingStartDay FROM ledger_spaces").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(25, cursor.getInt(0))
        }
    }

    @Test
    fun v2에서_v3로_올리면_분류마다_장부와_이름으로_정한_원격_ID가_생긴다() {
        helper.createDatabase(TEST_DB, 2).apply {
            insertCategory(id = 1, spaceId = "space_shared_default", parentId = null, name = "식비")
            insertCategory(id = 2, spaceId = "space_shared_default", parentId = 1, name = "배달/외식")
            insertCategory(id = 3, spaceId = "space_personal_default", parentId = null, name = "식비")
            // 예전에는 같은 이름 분류를 막지 않았음 (숨긴 분류 + 다시 만든 분류)
            insertCategory(id = 4, spaceId = "space_shared_default", parentId = null, name = "식비", isArchived = true)
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, Migrations.MIGRATION_2_3)

        val remoteIds = db.query("SELECT id, remoteId, updatedAt FROM categories ORDER BY id").use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    assertEquals(0L, cursor.getLong(2))
                    put(cursor.getLong(0), cursor.getString(1))
                }
            }
        }
        val sharedFood = CategoryRemoteIds.named("space_shared_default", null, "식비")
        assertEquals(sharedFood, remoteIds[1])
        assertEquals(
            CategoryRemoteIds.named("space_shared_default", "식비", "배달/외식"),
            remoteIds[2]
        )
        assertEquals(
            CategoryRemoteIds.named("space_personal_default", null, "식비"),
            remoteIds[3]
        )
        // 숨긴 쪽에 로컬 id를 붙여 겹치지 않게
        assertEquals("$sharedFood-4", remoteIds[4])
    }

    @Test
    fun 모든_마이그레이션을_거친_DB를_Room이_정상적으로_연다() {
        helper.createDatabase(TEST_DB, 1).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(*Migrations.ALL)
            .build()
            .apply {
                openHelper.writableDatabase.close()
                close()
            }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertTransaction(id: Long, remoteId: String, amount: Long) {
        execSQL(
            """
            INSERT INTO transactions (id, remoteId, spaceId, type, amount, categoryId, subCategoryId, merchantOrTitle, memo, isCanceled, originQueueId, authorName, transactionDate, updatedAt, syncStatus)
            VALUES ($id, '$remoteId', 'space_shared_default', 'EXPENSE', $amount, 1, NULL, '테스트', '', 0, NULL, '나', 0, 0, 'SYNCED')
            """.trimIndent()
        )
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertCategory(
        id: Long,
        spaceId: String,
        parentId: Long?,
        name: String,
        isArchived: Boolean = false
    ) {
        execSQL(
            """
            INSERT INTO categories (id, spaceId, parentId, transactionType, name, emoji, colorHex, sortOrder, isArchived, syncStatus)
            VALUES ($id, '$spaceId', ${parentId ?: "NULL"}, 'EXPENSE', '$name', '🍔', '#EF4444', 0, ${if (isArchived) 1 else 0}, 'SYNCED')
            """.trimIndent()
        )
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
