package com.budget.householdbook.domain.usecase

import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.core.datastore.AppUserSettings
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.domain.model.SpaceType
import com.budget.householdbook.domain.model.UserTier
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 회원 탈퇴: 클라우드 정리가 성공했을 때만 이 휴대폰을 로그아웃·혼자 쓰기로 돌리고, 고른 경우에만 기록을 지움
 */
class DeleteAccountUseCaseTest {

    private val shared = AppDatabase.SPACE_ID_SHARED

    private val settings = FakeSettingsRepository(
        AppUserSettings(isLoggedIn = true, userUid = "uid-1", userEmail = "me@gmail.com", userTier = UserTier.PRO)
    )
    private val spaces = FakeLedgerSpaceRepository(
        listOf(
            LedgerSpaceEntity(
                id = shared,
                name = "공통",
                type = SpaceType.SHARED,
                isRemoteShared = true,
                inviteCode = "HB-ABCD-EFGH",
                memberNamesCsv = "나, 짝꿍"
            )
        )
    )
    private val sync = FakeSharedSyncRepository()
    private val resetter = FakeLocalDataResetter()
    private val deleteAccount = DeleteAccountUseCase(settings, spaces, sync, resetter)

    @Test
    fun `탈퇴에 성공하면 로그아웃하고 공통 생활비를 혼자 쓰기로 돌리며 기록은 남긴다`() = runTest {
        val result = deleteAccount(uiHost = null, wipeLocalData = false)

        assertTrue(result.isSuccess)
        assertEquals(1, sync.deleteAccountCalls)
        val s = settings.state.value
        assertFalse(s.isLoggedIn)
        assertEquals("", s.userEmail)
        assertEquals(UserTier.FREE, s.userTier)
        val space = spaces.state.value.single()
        assertFalse(space.isRemoteShared)
        assertNull(space.inviteCode)
        assertFalse(resetter.wiped)
    }

    @Test
    fun `기록도 지우기를 고르면 탈퇴 뒤 이 휴대폰 데이터를 지운다`() = runTest {
        deleteAccount(uiHost = null, wipeLocalData = true)
        assertTrue(resetter.wiped)
    }

    @Test
    fun `클라우드 정리가 실패하면 로그인 상태와 기록을 그대로 둔다`() = runTest {
        sync.deleteAccountResult = Result.failure(IllegalStateException("방장을 넘긴 뒤 다시 시도해주세요."))

        val result = deleteAccount(uiHost = null, wipeLocalData = true)

        assertTrue(result.isFailure)
        assertTrue(settings.state.value.isLoggedIn)
        assertTrue(spaces.state.value.single().isRemoteShared)
        assertFalse(resetter.wiped)
    }
}
