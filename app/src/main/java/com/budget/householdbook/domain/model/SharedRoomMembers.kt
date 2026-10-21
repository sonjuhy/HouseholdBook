package com.budget.householdbook.domain.model

/**
 * 공유 장부(공통 생활비) 멤버 상태
 * - OWNER: 방을 만든 사람 (내보낼 수 없음)
 * - JOINED: 초대를 수락해 함께 쓰는 중
 * - INVITED: 초대했지만 아직 로그인·수락 전
 */
enum class MemberStatus(val label: String) {
    OWNER("방장"),
    JOINED("함께 쓰는 중"),
    INVITED("수락 대기")
}

data class SharedRoomMember(
    val email: String,
    val name: String,
    val status: MemberStatus,
    val isMe: Boolean
)

data class SharedRoomInfo(
    val inviteCode: String,
    val members: List<SharedRoomMember>,
    val amIOwner: Boolean
) {
    /** 내가 이 멤버에게 할 수 있는 관리 동작 */
    fun canCancelInvitation(member: SharedRoomMember) = member.status == MemberStatus.INVITED && !member.isMe
    fun canRemove(member: SharedRoomMember) = amIOwner && member.status == MemberStatus.JOINED && !member.isMe
    val canLeave: Boolean get() = !amIOwner

    /** 방장은 이미 함께 쓰고 있는 다른 멤버에게만 넘길 수 있음 (수락 대기 중인 사람은 불가) */
    fun canTransferOwnership(member: SharedRoomMember) = amIOwner && member.status == MemberStatus.JOINED && !member.isMe
    val ownershipCandidates: List<SharedRoomMember> get() = members.filter { canTransferOwnership(it) }

    /** 함께 쓰는 멤버가 남아 있는 방장은 탈퇴 전에 방장을 넘겨야 함 (공유 장부가 통째로 사라지지 않도록) */
    val mustTransferBeforeLeaving: Boolean get() = ownershipCandidates.isNotEmpty()

    /** 장부에 표시할 멤버 이름 목록 (참여한 사람만): "나, 짝꿍" */
    fun joinedNamesCsv(): String =
        members.filter { it.status != MemberStatus.INVITED }.joinToString(", ") { it.name }
}

/**
 * Firestore 방 문서(`shared_spaces/{inviteCode}`) 값으로 멤버 목록을 만드는 순수 로직
 */
object SharedRoomMembers {

    /**
     * @param joinedEmails 예전 방 문서에는 없을 수 있음(null) → 이때는 명단의 모든 사람을 참여 중으로 봄
     */
    fun build(
        inviteCode: String,
        ownerEmail: String,
        memberEmails: List<String>,
        joinedEmails: List<String>?,
        memberNames: Map<String, String>,
        myEmail: String
    ): SharedRoomInfo {
        val owner = ownerEmail.trim().lowercase()
        val me = myEmail.trim().lowercase()
        val joined = joinedEmails?.map { it.trim().lowercase() }?.toSet()
        val members = (listOf(owner) + memberEmails.map { it.trim().lowercase() })
            .distinct()
            .map { email ->
                val status = when {
                    email == owner -> MemberStatus.OWNER
                    joined == null || email in joined -> MemberStatus.JOINED
                    else -> MemberStatus.INVITED
                }
                SharedRoomMember(
                    email = email,
                    name = memberNames[email]?.takeIf { it.isNotBlank() } ?: email.substringBefore("@"),
                    status = status,
                    isMe = email == me
                )
            }
            // 방장 → 참여 중 → 수락 대기 순
            .sortedBy { it.status.ordinal }
        return SharedRoomInfo(inviteCode = inviteCode, members = members, amIOwner = owner == me)
    }
}
