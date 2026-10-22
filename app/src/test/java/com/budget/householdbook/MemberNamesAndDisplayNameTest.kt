package com.budget.householdbook

import com.budget.householdbook.core.datastore.AppUserSettings
import com.budget.householdbook.domain.model.MemberNames
import com.budget.householdbook.domain.usecase.FakeSettingsRepository
import com.budget.householdbook.domain.usecase.FakeLedgerSpaceRepository
import com.budget.householdbook.domain.usecase.FakeSharedSyncRepository
import com.budget.householdbook.domain.usecase.FakeTransactionRepository
import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.domain.usecase.SyncOnAppStartUseCase
import com.budget.householdbook.domain.usecase.UpdateDisplayNameUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 예시 이름('홍길동')·꼬리표 없이 실제 이름으로 보여주기, 이름 바꾸기
 */
class MemberNamesAndDisplayNameTest {

    @Test
    fun `내 이름은 저장된 이름, 없으면 이메일 앞부분, 그것도 없으면 '나'`() {
        assertEquals("민수", AppUserSettings(userRealName = "민수", userEmail = "minsu@gmail.com").displayName)
        assertEquals("minsu", AppUserSettings(userRealName = "", userEmail = "minsu@gmail.com").displayName)
        assertEquals("나", AppUserSettings().displayName)
    }

    @Test
    fun `예전 버전의 예시 이름과 꼬리표를 걷어내고 실제 이름만 보여준다`() {
        assertEquals(listOf("민수"), MemberNames.clean("홍길동 (혼자 쓰기)", myName = "민수"))
        assertEquals(listOf("엄마", "아빠"), MemberNames.clean(" 엄마, 아빠 ,, 엄마", myName = "엄마"))
        assertEquals("나만 사용 중", MemberNames.summary("엄마, 아빠", isShared = false, myName = "엄마"))
        assertEquals("엄마, 아빠", MemberNames.summary("엄마, 아빠", isShared = true, myName = "엄마"))
        assertEquals("민수", MemberNames.summary("", isShared = true, myName = "민수"))
    }

    private fun tx(id: Long, spaceId: String, author: String) = TransactionEntity(
        id = id, remoteId = "r$id", spaceId = spaceId, type = TransactionType.EXPENSE,
        amount = 1_000, categoryId = 1, merchantOrTitle = "가게", authorName = author, transactionDate = 0
    )

    @Test
    fun `로그인했는데 이름이 비어 있으면 앱 시작 때 구글 프로필 이름으로 채우고, 직접 정한 이름은 그대로 둔다`() = runTest {
        val sync = FakeSharedSyncRepository().apply { googleDisplayName = "손민수" }
        val empty = FakeSettingsRepository(AppUserSettings(isLoggedIn = true, userEmail = "minsu@gmail.com"))
        val transactions = FakeTransactionRepository(
            listOf(
                tx(1, AppDatabase.SPACE_ID_PERSONAL, "홍길동"),
                tx(2, AppDatabase.SPACE_ID_SHARED, "홍길동"),
                tx(3, AppDatabase.SPACE_ID_PERSONAL, "나")
            )
        )
        SyncOnAppStartUseCase(empty, FakeLedgerSpaceRepository(), sync, transactions)()
        assertEquals("손민수", empty.state.value.userRealName)
        // 개인 용돈의 예전 예시 이름만 지금 이름으로 (공통 생활비·다른 이름은 그대로)
        assertEquals(listOf("손민수", "홍길동", "나"), transactions.state.value.sortedBy { it.id }.map { it.authorName })

        val named = FakeSettingsRepository(AppUserSettings(isLoggedIn = true, userRealName = "아빠"))
        SyncOnAppStartUseCase(named, FakeLedgerSpaceRepository(), sync, FakeTransactionRepository())()
        assertEquals("아빠", named.state.value.userRealName)
    }

    @Test
    fun `이름 바꾸기는 이 휴대폰과 함께 쓰는 장부에 반영하고 빈 이름·긴 이름은 거절한다`() = runTest {
        val settings = FakeSettingsRepository()
        val sync = FakeSharedSyncRepository()
        val update = UpdateDisplayNameUseCase(settings, sync)

        assertTrue(update("  민수  ").isSuccess)
        assertEquals("민수", settings.state.value.userRealName)
        assertEquals("민수", sync.updatedDisplayName)

        sync.updatedDisplayName = null
        assertTrue(update("   ").isFailure)
        assertTrue(update("가".repeat(UpdateDisplayNameUseCase.MAX_LENGTH + 1)).isFailure)
        assertNull(sync.updatedDisplayName)
        assertEquals("민수", settings.state.value.userRealName)
    }
}
