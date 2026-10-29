package com.budget.householdbook.domain.usecase

import com.budget.householdbook.core.datastore.AppUserSettings
import com.budget.householdbook.data.sync.GoogleLoginResult
import com.budget.householdbook.domain.model.UserTier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 로그인해 둔 상태에서 받은 함께 쓰기 초대: 다시 로그인하지 않아도 앱을 열 때 수락
 */
class AcceptPendingInvitationUseCaseTest {

    private val sync = FakeSharedSyncRepository()

    private fun invitation() = GoogleLoginResult(
        uid = "uid-2",
        email = "partner@gmail.com",
        displayName = "지민",
        resolvedTier = UserTier.PRO,
        inviteCode = "HB-ABCD-EFGH",
        memberNamesCsv = "주현, 지민",
        isInvitedJoin = true,
        isExistingUser = true,
        inviterName = "주현"
    )

    @Test
    fun `받은 초대가 있으면 함께 쓰기로 바꾸고 공유 장부 동기화를 시작한다`() = runTest {
        val settings = FakeSettingsRepository(
            AppUserSettings(isLoggedIn = true, userUid = "uid-2", userEmail = "partner@gmail.com", userRealName = "지민", userTier = UserTier.FREE)
        )
        sync.pendingInvitation = invitation()

        val accepted = AcceptPendingInvitationUseCase(settings, sync)()

        assertEquals("주현", accepted?.inviterName)
        // 직접 바꾼 내 이름으로 수락 (구글 프로필 이름으로 덮어쓰지 않음)
        assertEquals("지민", sync.acceptInvitationName)
        val saved = settings.userSettingsFlow.first()
        assertEquals(UserTier.PRO, saved.userTier)
        assertTrue(saved.isLoggedIn)
        assertEquals(listOf(UserTier.PRO to true), sync.syncCalls)
    }

    @Test
    fun `받은 초대가 없으면 아무것도 바꾸지 않는다`() = runTest {
        val settings = FakeSettingsRepository(
            AppUserSettings(isLoggedIn = true, userUid = "uid-2", userEmail = "partner@gmail.com", userTier = UserTier.FREE)
        )

        assertNull(AcceptPendingInvitationUseCase(settings, sync)())
        assertEquals(UserTier.FREE, settings.userSettingsFlow.first().userTier)
        assertTrue(sync.syncCalls.isEmpty())
    }

    @Test
    fun `로그인하지 않았으면 초대를 확인하지 않는다`() = runTest {
        sync.pendingInvitation = invitation()

        assertNull(AcceptPendingInvitationUseCase(FakeSettingsRepository(), sync)())
        assertNull(sync.acceptInvitationName)
    }
}
