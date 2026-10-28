package com.budget.householdbook.domain.usecase

import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.domain.model.CategoryRemoteIds
import com.budget.householdbook.domain.model.RemoteCategory
import com.budget.householdbook.domain.model.SyncStatus
import com.budget.householdbook.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 공용 분류 동기화: 휴대폰마다 다른 로컬 id 대신 원격 ID로 같은 분류를 맞춤
 */
class SharedCategorySyncTest {

    private val shared = AppDatabase.SPACE_ID_SHARED
    private val personal = AppDatabase.SPACE_ID_PERSONAL

    private val foodId = CategoryRemoteIds.named(shared, null, "식비")
    private val groceryId = CategoryRemoteIds.named(shared, "식비", "식재료/장보기")

    private val categories = FakeCategoryRepository(
        listOf(
            CategoryEntity(id = 1, spaceId = shared, name = "식비", sortOrder = 1, remoteId = foodId),
            CategoryEntity(id = 2, spaceId = shared, parentId = 1, name = "식재료/장보기", sortOrder = 1, remoteId = groceryId),
            CategoryEntity(id = 10, spaceId = personal, name = "취미", remoteId = "cat-personal")
        )
    )
    private val sync = SharedCategorySync(categories)

    private fun remote(
        remoteId: String,
        name: String,
        parentRemoteId: String? = null,
        updatedAt: Long = 0L,
        isArchived: Boolean = false
    ) = RemoteCategory(
        remoteId = remoteId, parentRemoteId = parentRemoteId, transactionType = TransactionType.EXPENSE,
        name = name, emoji = "🏷️", colorHex = "#2563EB", sortOrder = 5, isArchived = isArchived, updatedAt = updatedAt
    )

    @Test
    fun `기본 분류의 원격 ID는 휴대폰이 달라도 같고 장부가 다르면 다르다`() {
        assertEquals(CategoryRemoteIds.named(shared, null, "식비"), foodId)
        assertNotEquals(CategoryRemoteIds.named(personal, null, "식비"), foodId)
        assertNotEquals(CategoryRemoteIds.newId(), CategoryRemoteIds.newId())
    }

    @Test
    fun `처음 맞출 때 공유 장부에 없는 공용 분류만 올리고 개인 분류는 올리지 않는다`() = runTest {
        val uploads = sync.toUpload(remoteUpdatedAt = mapOf(foodId to 0L))

        assertEquals(listOf(groceryId), uploads.map { it.second.remoteId })
        assertEquals(foodId, uploads.single().second.parentRemoteId)
    }

    @Test
    fun `상대가 추가한 공용 분류는 대분류부터 내 휴대폰에 생기고 거래의 분류를 찾을 수 있다`() = runTest {
        // 세부 분류가 먼저 와도 대분류를 먼저 넣음
        val changed = sync.applyRemote(
            listOf(remote("cat-pet-food", "사료", parentRemoteId = "cat-pet", updatedAt = 5), remote("cat-pet", "반려동물", updatedAt = 5))
        )

        assertEquals(2, changed)
        val parent = categories.getByRemoteId("cat-pet")!!
        val sub = categories.getByRemoteId("cat-pet-food")!!
        assertEquals(shared, parent.spaceId)
        assertEquals(parent.id, sub.parentId)
        assertEquals(SyncStatus.SYNCED, sub.syncStatus)
        assertEquals(sub.id, sync.localIdOf("cat-pet-food"))
        assertEquals("cat-pet-food", sync.remoteIdOf(sub.id))
        assertNull(sync.localIdOf("cat-unknown"))
    }

    @Test
    fun `더 최근에 바뀐 쪽을 남기고, 내 쪽이 더 최근이면 다시 올린다`() = runTest {
        // 상대가 이름을 바꾸고 숨김 → 내 휴대폰에 반영 (로컬 id는 그대로)
        sync.applyRemote(listOf(remote(foodId, "밥값", updatedAt = 10, isArchived = true)))
        assertEquals("밥값", categories.getById(1)!!.name)
        assertTrue(categories.getById(1)!!.isArchived)

        // 내가 더 나중에 바꾼 분류는 예전 원격 값으로 덮어쓰지 않음
        categories.updateCategory(categories.getById(2)!!.copy(name = "장보기", updatedAt = 20, syncStatus = SyncStatus.PENDING_UPLOAD))
        sync.applyRemote(listOf(remote(groceryId, "식재료/장보기", parentRemoteId = foodId, updatedAt = 15)))
        assertEquals("장보기", categories.getById(2)!!.name)

        val remoteUpdatedAt = mapOf(foodId to 10L, groceryId to 15L)
        val uploads = sync.toUpload(remoteUpdatedAt)
        assertEquals(listOf(groceryId), uploads.map { it.second.remoteId })

        assertTrue(sync.hasPendingUploads())
        sync.markUploaded(uploads.map { it.first }, remoteUpdatedAt)
        assertFalse(sync.hasPendingUploads())
    }

    @Test
    fun `공유 장부 쪽이 같거나 더 최근인 변경 대기 분류도 동기화됨으로 정리한다`() = runTest {
        categories.updateCategory(categories.getById(1)!!.copy(updatedAt = 7, syncStatus = SyncStatus.PENDING_UPLOAD))

        sync.markUploaded(emptyList(), remoteUpdatedAt = mapOf(foodId to 7L))

        assertFalse(sync.hasPendingUploads())
    }

    @Test
    fun `두 휴대폰에서 따로 만든 같은 이름 분류는 공유 장부의 분류 하나로 합친다`() = runTest {
        categories.insertCategory(CategoryEntity(id = 3, spaceId = shared, name = "운동", remoteId = "cat-mine", updatedAt = 50))

        sync.applyRemote(
            listOf(remote(foodId, "식비"), remote(groceryId, "식재료/장보기", parentRemoteId = foodId), remote("cat-theirs", "운동", updatedAt = 40)),
            isFullSnapshot = true
        )

        // 로컬 id 3(내 거래가 가리키는 값)은 그대로, 원격 ID만 공유 장부의 것으로
        val merged = categories.getById(3)!!
        assertEquals("cat-theirs", merged.remoteId)
        assertEquals(1, categories.state.value.count { it.name == "운동" })
        // 내 쪽이 더 최근에 만들어서 다시 올림
        assertEquals(SyncStatus.PENDING_UPLOAD, merged.syncStatus)
    }

    @Test
    fun `대분류를 모르는 세부 분류는 건너뛴다`() = runTest {
        assertEquals(0, sync.applyRemote(listOf(remote("cat-orphan", "고아", parentRemoteId = "cat-missing", updatedAt = 1))))
        assertNull(categories.getByRemoteId("cat-orphan"))
    }
}
