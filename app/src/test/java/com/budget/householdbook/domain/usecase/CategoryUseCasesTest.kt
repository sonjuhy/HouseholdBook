package com.budget.householdbook.domain.usecase

import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.core.datastore.AppUserSettings
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.domain.model.SpaceType
import com.budget.householdbook.domain.model.SyncStatus
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.domain.model.groupedForSpace
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 공용·개인 분류를 따로 관리: 한쪽을 바꿔도 다른 쪽 분류는 그대로
 */
class CategoryUseCasesTest {

    private val shared = AppDatabase.SPACE_ID_SHARED
    private val personal = AppDatabase.SPACE_ID_PERSONAL

    private val categories = FakeCategoryRepository(
        listOf(
            CategoryEntity(id = 1, spaceId = shared, name = "식비", sortOrder = 1),
            CategoryEntity(id = 2, spaceId = shared, parentId = 1, name = "식재료", sortOrder = 1),
            CategoryEntity(id = 3, spaceId = shared, name = "생활비 입금", transactionType = TransactionType.INCOME, sortOrder = 2),
            CategoryEntity(id = 10, spaceId = personal, name = "식비", sortOrder = 1),
            CategoryEntity(id = 11, spaceId = personal, parentId = 10, name = "커피", sortOrder = 1)
        )
    )
    private val sync = FakeSharedSyncRepository()
    private val syncShared = SyncSharedChangesUseCase(
        FakeSettingsRepository(AppUserSettings(currentSpaceId = shared)),
        FakeLedgerSpaceRepository(
            listOf(LedgerSpaceEntity(id = shared, name = "공통 생활비", type = SpaceType.SHARED, isRemoteShared = true))
        ),
        sync
    )
    private val add = AddCategoryUseCase(categories, syncShared)
    private val update = UpdateCategoryUseCase(categories, syncShared)
    private val delete = DeleteCategoryUseCase(categories, syncShared)

    private fun names(spaceId: String) =
        categories.state.value.groupedForSpace(spaceId).map { g -> g.parent.name to g.subCategories.map { it.name } }

    @Test
    fun `공용과 개인 분류는 공간별로 따로 묶인다`() {
        assertEquals(listOf("식비" to listOf("식재료"), "생활비 입금" to emptyList<String>()), names(shared))
        assertEquals(listOf("식비" to listOf("커피")), names(personal))
    }

    @Test
    fun `개인에 대분류를 추가해도 공용 분류는 그대로다`() = runTest {
        val before = names(shared)

        assertEquals(AddCategoryResult.ADDED, add(personal, null, TransactionType.EXPENSE, " 취미 ", "🎮", "#007AFF"))

        assertEquals(listOf("식비" to listOf("커피"), "취미" to emptyList<String>()), names(personal))
        assertEquals(before, names(shared))
    }

    @Test
    fun `같은 이름은 다른 공간에는 만들 수 있지만 같은 공간 같은 대분류 안에서는 막는다`() = runTest {
        assertEquals(AddCategoryResult.DUPLICATE_NAME, add(personal, null, TransactionType.EXPENSE, "식비", "🍚", "#007AFF"))
        assertEquals(AddCategoryResult.DUPLICATE_NAME, add(shared, 1, TransactionType.EXPENSE, "식재료", "🥬", "#007AFF"))
        assertEquals(AddCategoryResult.ADDED, add(personal, 10, TransactionType.EXPENSE, "식재료", "🥬", "#007AFF"))
        assertEquals(AddCategoryResult.ADDED, add(shared, null, TransactionType.EXPENSE, "취미", "🎮", "#007AFF"))
        assertEquals(AddCategoryResult.BLANK_NAME, add(shared, null, TransactionType.EXPENSE, "  ", "🎮", "#007AFF"))
    }

    @Test
    fun `세부 분류는 같은 공간의 대분류 아래에만 붙고 대분류의 수입 지출 유형을 따른다`() = runTest {
        // 공용 대분류(id=1)를 개인 공간 세부 분류의 부모로 쓸 수 없음
        assertEquals(AddCategoryResult.PARENT_NOT_FOUND, add(personal, 1, TransactionType.EXPENSE, "외식", "🍜", "#007AFF"))
        // 세부 분류(id=2) 아래에는 또 세부 분류를 만들지 않음
        assertEquals(AddCategoryResult.PARENT_NOT_FOUND, add(shared, 2, TransactionType.EXPENSE, "채소", "🥕", "#007AFF"))

        assertEquals(AddCategoryResult.ADDED, add(shared, 3, TransactionType.EXPENSE, "용돈 이체", "💸", "#007AFF"))
        val created = categories.state.value.single { it.name == "용돈 이체" }
        assertEquals(shared, created.spaceId)
        assertEquals(TransactionType.INCOME, created.transactionType)
    }

    @Test
    fun `새 분류는 같은 공간 목록의 맨 뒤에 붙는다`() = runTest {
        add(shared, null, TransactionType.EXPENSE, "교통", "🚌", "#007AFF")
        assertEquals("교통", categories.state.value.groupedForSpace(shared).last().parent.name)
    }

    @Test
    fun `개인 분류를 지워도 같은 이름의 공용 분류는 남는다`() = runTest {
        delete(10)

        assertTrue(names(personal).isEmpty())
        assertEquals("식비" to listOf("식재료"), names(shared).first())
    }

    // ---------------- 공용 분류는 함께 쓰는 사람에게 올림 ----------------

    @Test
    fun `공용 분류를 추가 수정 삭제하면 올릴 표시를 하고 동기화를 요청한다`() = runTest {
        add(shared, null, TransactionType.EXPENSE, "교통", "🚌", "#007AFF", nowMillis = 100)
        val created = categories.state.value.single { it.name == "교통" }
        assertEquals(SyncStatus.PENDING_UPLOAD, created.syncStatus)
        assertEquals(100L, created.updatedAt)

        categories.markSynced(listOf(created.id))
        update(created.copy(name = "교통/주유"), nowMillis = 200)
        assertEquals(SyncStatus.PENDING_UPLOAD, categories.getById(created.id)!!.syncStatus)

        categories.markSynced(listOf(created.id))
        delete(1, nowMillis = 300)
        val archived = categories.state.value.filter { it.id == 1L || it.parentId == 1L }
        assertTrue(archived.all { it.isArchived && it.syncStatus == SyncStatus.PENDING_UPLOAD && it.updatedAt == 300L })

        assertEquals(3, sync.syncCalls.size)
    }

    @Test
    fun `개인 분류를 바꿔도 올리지 않는다`() = runTest {
        add(personal, null, TransactionType.EXPENSE, "취미", "🎮", "#007AFF")
        update(categories.getById(10)!!.copy(name = "먹거리"))
        delete(11)

        assertTrue(categories.state.value.filter { it.spaceId == personal }.none { it.syncStatus == SyncStatus.PENDING_UPLOAD })
        assertTrue(sync.syncCalls.isEmpty())
        assertFalse(SharedCategorySync(categories).hasPendingUploads())
    }

    @Test
    fun `분류 이름은 50자까지만 저장한다`() = runTest {
        add(shared, null, TransactionType.EXPENSE, "가".repeat(60), "🏷️", "#007AFF")
        assertEquals(50, categories.state.value.last().name.length)
    }
}
