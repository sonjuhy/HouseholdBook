package com.budget.householdbook.data.sync

import android.app.Activity
import com.budget.householdbook.domain.model.SharedRoomInfo
import com.budget.householdbook.domain.model.UserTier
import com.budget.householdbook.domain.repository.SharedSyncRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * [RemoteSyncManager]를 도메인 [SharedSyncRepository]로 노출하는 어댑터
 */
class FirebaseSharedSyncRepository(
    private val syncManager: RemoteSyncManager
) : SharedSyncRepository {

    override val syncStatusMessage: StateFlow<String> = syncManager.lastSyncMessage

    override suspend fun syncIfEligible(userTier: UserTier, isRemoteShared: Boolean): Boolean =
        syncManager.syncIfEligible(userTier, isRemoteShared)

    override fun canSyncNow(): Boolean = syncManager.canSyncNow()

    override suspend fun signInWithGoogleAccount(
        uiHost: Any?,
        manualGoogleEmail: String?,
        manualDisplayName: String?
    ): Result<GoogleLoginResult> = syncManager.signInWithGoogleAccount(
        activity = uiHost as? Activity,
        manualGoogleEmail = manualGoogleEmail,
        manualDisplayName = manualDisplayName
    )

    override suspend fun sendGoogleAccountInvitation(
        inviterEmail: String,
        inviterName: String,
        partnerGoogleEmail: String,
        partnerName: String,
        inviteCode: String
    ): Result<String> = syncManager.sendGoogleAccountInvitation(
        inviterEmail = inviterEmail,
        inviterName = inviterName,
        partnerGoogleEmail = partnerGoogleEmail,
        partnerName = partnerName,
        inviteCode = inviteCode
    )

    override suspend fun signOutGoogleAccount() = syncManager.signOutGoogleAccount()

    override suspend fun syncUserProfileTierToCloud(
        email: String,
        displayName: String,
        tier: UserTier,
        inviteCode: String?,
        memberNamesCsv: String
    ) = syncManager.syncUserProfileTierToCloud(
        email = email,
        displayName = displayName,
        tier = tier,
        inviteCode = inviteCode,
        memberNamesCsv = memberNamesCsv
    )

    override fun isValidInviteCode(code: String?): Boolean = RemoteSyncManager.isValidInviteCode(code)

    override fun generateInviteCode(): String = RemoteSyncManager.generateInviteCode()

    override suspend fun loadRoomMembers(): Result<SharedRoomInfo> = syncManager.loadRoomMembers()
    override suspend fun cancelInvitation(partnerEmail: String): Result<SharedRoomInfo> = syncManager.cancelInvitation(partnerEmail)
    override suspend fun removeMember(memberEmail: String): Result<SharedRoomInfo> = syncManager.removeMember(memberEmail)
    override suspend fun leaveRoom(): Result<Unit> = syncManager.leaveRoom()
    override suspend fun transferOwnership(newOwnerEmail: String): Result<SharedRoomInfo> =
        syncManager.transferOwnership(newOwnerEmail)
    override suspend fun deleteAccount(uiHost: Any?): Result<Unit> = syncManager.deleteAccount(uiHost as? Activity)
}
