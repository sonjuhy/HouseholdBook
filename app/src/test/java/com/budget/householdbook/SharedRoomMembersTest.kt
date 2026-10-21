package com.budget.householdbook

import com.budget.householdbook.domain.model.MemberStatus
import com.budget.householdbook.domain.model.SharedRoomMembers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedRoomMembersTest {

    private val owner = "owner@gmail.com"
    private val partner = "partner@gmail.com"
    private val invited = "invited@gmail.com"

    private fun room(myEmail: String, joined: List<String>? = listOf(owner, partner)) = SharedRoomMembers.build(
        inviteCode = "HB-ABCD-EFGH",
        ownerEmail = owner,
        memberEmails = listOf(owner, partner, invited),
        joinedEmails = joined,
        memberNames = mapOf(owner to "나", partner to "짝꿍"),
        myEmail = myEmail
    )

    @Test
    fun `방장·참여·수락 대기 순으로 상태를 정한다`() {
        val info = room(owner)
        assertEquals(listOf(MemberStatus.OWNER, MemberStatus.JOINED, MemberStatus.INVITED), info.members.map { it.status })
        assertEquals(listOf("나", "짝꿍", "invited"), info.members.map { it.name })
        assertEquals("나, 짝꿍", info.joinedNamesCsv())
    }

    @Test
    fun `방장은 수락 대기 초대를 취소하고 참여 멤버를 내보낼 수 있지만 나갈 수는 없다`() {
        val info = room(owner)
        val (me, p, i) = info.members
        assertTrue(info.amIOwner)
        assertFalse(info.canLeave)
        assertTrue(info.canRemove(p))
        assertFalse(info.canRemove(me))
        assertFalse(info.canRemove(i))
        assertTrue(info.canCancelInvitation(i))
        assertFalse(info.canCancelInvitation(p))
    }

    @Test
    fun `방장이 아닌 멤버는 초대 취소와 나가기만 할 수 있다`() {
        val info = room(partner)
        val (o, me, i) = info.members
        assertTrue(me.isMe)
        assertTrue(info.canLeave)
        assertFalse(info.canRemove(o))
        assertTrue(info.canCancelInvitation(i))
    }

    @Test
    fun `참여 기록이 없는 예전 방은 모두 참여 중으로 본다`() {
        val info = room(owner, joined = null)
        assertEquals(listOf(MemberStatus.OWNER, MemberStatus.JOINED, MemberStatus.JOINED), info.members.map { it.status })
    }

    @Test
    fun `이메일 대소문자와 공백을 정리해 비교한다`() {
        val info = SharedRoomMembers.build(
            inviteCode = "HB-ABCD-EFGH",
            ownerEmail = " Owner@Gmail.com ",
            memberEmails = listOf("owner@gmail.com", "Partner@Gmail.com"),
            joinedEmails = listOf("OWNER@gmail.com", "partner@gmail.com"),
            memberNames = emptyMap(),
            myEmail = "PARTNER@gmail.com"
        )
        assertEquals(2, info.members.size)
        assertTrue(info.members.last().isMe)
        assertEquals(MemberStatus.JOINED, info.members.last().status)
    }
}
