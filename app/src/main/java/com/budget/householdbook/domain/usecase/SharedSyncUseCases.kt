package com.budget.householdbook.domain.usecase

import com.budget.householdbook.domain.model.SharedRoomInfo
import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.data.sync.GoogleLoginResult
import com.budget.householdbook.domain.model.SharedSyncOutcome
import com.budget.householdbook.domain.model.UserTier
import com.budget.householdbook.domain.repository.LedgerSpaceRepository
import com.budget.householdbook.domain.repository.LocalDataResetter
import com.budget.householdbook.domain.repository.SettingsRepository
import com.budget.householdbook.domain.repository.SharedSyncRepository
import com.budget.householdbook.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first

/**
 * 공통 생활비 변경(취소·삭제 등) 직후 PRO + 공유 활성화 상태이면 Firestore에 반영
 */
class SyncSharedChangesUseCase(
    private val settingsRepository: SettingsRepository,
    private val spaceRepository: LedgerSpaceRepository,
    private val sharedSyncRepository: SharedSyncRepository
) {
    suspend operator fun invoke() {
        val settings = settingsRepository.userSettingsFlow.first()
        val sharedSpace = spaceRepository.getSpaceById(AppDatabase.SPACE_ID_SHARED)
        sharedSyncRepository.syncIfEligible(settings.userTier, sharedSpace?.isRemoteShared == true)
    }

    /** 해당 공간이 원격 공유 중인 공통 생활비인지 (삭제 시 PENDING_DELETE 처리 여부 판단) */
    suspend fun isSharedSyncActive(spaceId: String): Boolean {
        if (spaceId != AppDatabase.SPACE_ID_SHARED) return false
        val settings = settingsRepository.userSettingsFlow.first()
        val space = spaceRepository.getSpaceById(spaceId)
        return settings.userTier == UserTier.PRO && space?.isRemoteShared == true
    }
}

/**
 * 앱 시작 시 프리미엄(함께 쓰기) 상태이면 즉시 실시간 공유 연결
 */
class SyncOnAppStartUseCase(
    private val settingsRepository: SettingsRepository,
    private val spaceRepository: LedgerSpaceRepository,
    private val sharedSyncRepository: SharedSyncRepository
) {
    suspend operator fun invoke() {
        val settings = settingsRepository.userSettingsFlow.first()
        // 로그인했는데 이름이 비어 있으면(예전 예시 이름을 지운 경우 등) 구글 프로필 이름으로 채움
        if (settings.isLoggedIn && settings.userRealName.isBlank()) {
            sharedSyncRepository.currentGoogleDisplayName()?.let { settingsRepository.setUserRealName(it) }
        }
        val sharedSpace = spaceRepository.getSpaceById(AppDatabase.SPACE_ID_SHARED)
        sharedSyncRepository.syncIfEligible(
            userTier = settings.userTier,
            isRemoteShared = sharedSpace?.isRemoteShared == true || settings.userTier == UserTier.PRO
        )
    }
}

/**
 * [SyncWorker] 백그라운드에서 공통 생활비의 미반영 변경을 업로드합니다.
 */
class SyncPendingSharedChangesUseCase(
    private val settingsRepository: SettingsRepository,
    private val spaceRepository: LedgerSpaceRepository,
    private val transactionRepository: TransactionRepository,
    private val sharedSyncRepository: SharedSyncRepository
) {
    suspend operator fun invoke(): SharedSyncOutcome {
        val settings = settingsRepository.userSettingsFlow.first()
        val sharedSpace = spaceRepository.getSpaceById(AppDatabase.SPACE_ID_SHARED)
        if (settings.userTier != UserTier.PRO || sharedSpace?.isRemoteShared != true) {
            return SharedSyncOutcome.NOT_ELIGIBLE
        }
        val synced = sharedSyncRepository.syncIfEligible(settings.userTier, isRemoteShared = true)
        val remaining = transactionRepository.countUnsyncedInSpace(AppDatabase.SPACE_ID_SHARED)
        return when {
            remaining == 0 -> SharedSyncOutcome.DONE
            // 로그인 안 됨 / 권한 없음 등은 재시도해도 해결되지 않으므로 다음 쓰기·앱 실행 때 다시 시도
            !synced && !sharedSyncRepository.canSyncNow() -> SharedSyncOutcome.NOT_ELIGIBLE
            else -> SharedSyncOutcome.RETRY
        }
    }
}

/**
 * 프리미엄(함께 쓰기) 공통 생활비 멤버 초대/공유 설정 변경 및 실시간 동기화 시작
 */
class UpdateSharedMembersUseCase(
    private val settingsRepository: SettingsRepository,
    private val spaceRepository: LedgerSpaceRepository,
    private val sharedSyncRepository: SharedSyncRepository
) {
    suspend operator fun invoke(
        memberNamesCsv: String,
        isShared: Boolean,
        customInviteCode: String? = null,
        spaceId: String = AppDatabase.SPACE_ID_SHARED
    ) {
        val current = spaceRepository.getSpaceById(spaceId)
        // 서버 규칙이 허용하는 HB-XXXX-XXXX 형식만 사용 (예전 고정값·직접 입력값은 새 번호로 교체)
        val code = if (isShared) {
            customInviteCode?.trim()?.uppercase()?.takeIf { sharedSyncRepository.isValidInviteCode(it) }
                ?: current?.inviteCode?.takeIf { sharedSyncRepository.isValidInviteCode(it) }
                ?: sharedSyncRepository.generateInviteCode()
        } else {
            null
        }
        spaceRepository.updateShareConfig(spaceId, memberNamesCsv, code, isShared)
        val settings = settingsRepository.userSettingsFlow.first()
        if (settings.isLoggedIn && settings.userEmail.isNotBlank()) {
            sharedSyncRepository.syncUserProfileTierToCloud(
                email = settings.userEmail,
                displayName = settings.displayName,
                tier = settings.userTier,
                inviteCode = code,
                memberNamesCsv = memberNamesCsv
            )
        }
        sharedSyncRepository.syncIfEligible(settings.userTier, isShared)
    }
}

/**
 * 플랜(혼자 쓰기 / 함께 쓰기) 변경: 공통 생활비 공유 설정을 함께 맞춥니다.
 */
class UpdateUserTierUseCase(
    private val settingsRepository: SettingsRepository,
    private val updateSharedMembers: UpdateSharedMembersUseCase
) {
    suspend operator fun invoke(tier: UserTier) {
        settingsRepository.setUserTier(tier)
        // 멤버 이름 목록은 실제 참여자 기준 (함께 쓰는 사람이 들어오면 동기화 때 채워짐)
        val currentName = settingsRepository.userSettingsFlow.first().displayName
        updateSharedMembers(currentName, isShared = tier == UserTier.PRO)
    }
}

/**
 * 구글 계정으로 로그인하고, 서버에서 PRO / FREE 여부와 초대 내역을 확인해 자동 반영합니다.
 */
class SignInWithGoogleUseCase(
    private val settingsRepository: SettingsRepository,
    private val sharedSyncRepository: SharedSyncRepository
) {
    /** @param uiHost 구글 인증 창을 띄울 Activity */
    suspend operator fun invoke(
        uiHost: Any?,
        manualGoogleEmail: String? = null,
        manualDisplayName: String? = null
    ): Result<GoogleLoginResult> {
        val result = sharedSyncRepository.signInWithGoogleAccount(
            uiHost = uiHost,
            manualGoogleEmail = manualGoogleEmail,
            manualDisplayName = manualDisplayName
        )
        result.onSuccess { loginInfo ->
            settingsRepository.saveGoogleLoginProfile(
                uid = loginInfo.uid,
                email = loginInfo.email,
                displayName = loginInfo.displayName,
                resolvedTier = loginInfo.resolvedTier
            )
            sharedSyncRepository.syncIfEligible(
                userTier = loginInfo.resolvedTier,
                isRemoteShared = loginInfo.resolvedTier == UserTier.PRO
            )
        }
        return result
    }
}

/**
 * 상대방의 구글 계정 이메일로 함께 쓰기 초대장을 보냅니다.
 * @return 초대번호
 */
class InvitePartnerUseCase(
    private val settingsRepository: SettingsRepository,
    private val sharedSyncRepository: SharedSyncRepository
) {
    suspend operator fun invoke(
        partnerGoogleEmail: String,
        partnerName: String,
        customInviteCode: String
    ): Result<String> {
        val settings = settingsRepository.userSettingsFlow.first()
        val result = sharedSyncRepository.sendGoogleAccountInvitation(
            inviterEmail = settings.userEmail,
            inviterName = settings.displayName,
            partnerGoogleEmail = partnerGoogleEmail,
            partnerName = partnerName,
            inviteCode = customInviteCode
        )
        result.onSuccess {
            settingsRepository.setUserTier(UserTier.PRO)
            sharedSyncRepository.syncIfEligible(UserTier.PRO, true)
        }
        return result
    }
}

/**
 * 구글 계정 로그아웃: 공통 생활비를 혼자 쓰기로 되돌립니다.
 */
class SignOutGoogleUseCase(
    private val settingsRepository: SettingsRepository,
    private val spaceRepository: LedgerSpaceRepository,
    private val sharedSyncRepository: SharedSyncRepository
) {
    suspend operator fun invoke() {
        sharedSyncRepository.signOutGoogleAccount()
        settingsRepository.clearGoogleLogin()
        spaceRepository.updateShareConfig(
            spaceId = AppDatabase.SPACE_ID_SHARED,
            memberNamesCsv = "나",
            inviteCode = null,
            isShared = false
        )
    }
}

/** 공유 장부 멤버 목록 (방장 / 함께 쓰는 중 / 수락 대기) */
class LoadSharedMembersUseCase(private val sharedSyncRepository: SharedSyncRepository) {
    suspend operator fun invoke(): Result<SharedRoomInfo> = sharedSyncRepository.loadRoomMembers()
}

/** 아직 수락하지 않은 초대 취소 */
class CancelInvitationUseCase(private val sharedSyncRepository: SharedSyncRepository) {
    suspend operator fun invoke(partnerEmail: String): Result<SharedRoomInfo> =
        sharedSyncRepository.cancelInvitation(partnerEmail)
}

/** 함께 쓰던 멤버 내보내기 (방장만) */
class RemoveSharedMemberUseCase(private val sharedSyncRepository: SharedSyncRepository) {
    suspend operator fun invoke(memberEmail: String): Result<SharedRoomInfo> =
        sharedSyncRepository.removeMember(memberEmail)
}

/** 공유 장부에서 나가기 (방장이 아닌 멤버). 내 휴대폰 기록은 유지 */
class LeaveSharedSpaceUseCase(private val sharedSyncRepository: SharedSyncRepository) {
    suspend operator fun invoke(): Result<Unit> = sharedSyncRepository.leaveRoom()
}

/** 방장 넘기기 (방장 → 함께 쓰는 중인 멤버) */
class TransferOwnershipUseCase(private val sharedSyncRepository: SharedSyncRepository) {
    suspend operator fun invoke(newOwnerEmail: String): Result<SharedRoomInfo> =
        sharedSyncRepository.transferOwnership(newOwnerEmail)
}

/**
 * 회원 탈퇴
 * - 클라우드 정리와 로그인 계정 삭제가 끝난 뒤에만 이 휴대폰을 로그아웃·혼자 쓰기 상태로 돌림
 *   (중간에 실패하면 계속 로그인된 상태라 다시 시도할 수 있음)
 * - [wipeLocalData]이면 이 휴대폰의 가계부 기록도 모두 지움 (앱이 종료되고, 다시 열면 처음 상태)
 */
class DeleteAccountUseCase(
    private val settingsRepository: SettingsRepository,
    private val spaceRepository: LedgerSpaceRepository,
    private val sharedSyncRepository: SharedSyncRepository,
    private val localDataResetter: LocalDataResetter
) {
    suspend operator fun invoke(uiHost: Any?, wipeLocalData: Boolean): Result<Unit> {
        val result = sharedSyncRepository.deleteAccount(uiHost)
        result.onSuccess {
            settingsRepository.clearGoogleLogin()
            spaceRepository.updateShareConfig(
                spaceId = AppDatabase.SPACE_ID_SHARED,
                memberNamesCsv = "나",
                inviteCode = null,
                isShared = false
            )
            if (wipeLocalData) localDataResetter.wipeAllLocalData()
        }
        return result
    }
}

/**
 * 장부에 표시할 내 이름 바꾸기 (기본값은 구글 프로필 이름)
 * - 이 휴대폰 설정에 먼저 저장하고, 로그인 중이면 내 프로필과 함께 쓰는 장부 멤버 명단에도 반영
 */
class UpdateDisplayNameUseCase(
    private val settingsRepository: SettingsRepository,
    private val sharedSyncRepository: SharedSyncRepository
) {
    suspend operator fun invoke(name: String): Result<Unit> {
        val clean = name.trim()
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("이름을 입력해 주세요."))
        if (clean.length > MAX_LENGTH) return Result.failure(IllegalArgumentException("이름은 ${MAX_LENGTH}자까지 쓸 수 있어요."))
        settingsRepository.setUserRealName(clean)
        return sharedSyncRepository.updateDisplayName(clean)
    }

    companion object {
        const val MAX_LENGTH = 20
    }
}
