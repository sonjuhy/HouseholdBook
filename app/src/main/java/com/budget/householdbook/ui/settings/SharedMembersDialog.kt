package com.budget.householdbook.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.householdbook.core.designsystem.IosHairlineDivider
import com.budget.householdbook.core.designsystem.IosTintedButton
import com.budget.householdbook.domain.model.MemberStatus
import com.budget.householdbook.domain.model.SharedRoomInfo
import com.budget.householdbook.domain.model.SharedRoomMember
import com.budget.householdbook.ui.SharedMembersState
import com.budget.householdbook.ui.theme.ExpenseCoral
import com.budget.householdbook.ui.theme.IncomeBlue
import com.budget.householdbook.ui.theme.IosSystemGray
import com.budget.householdbook.ui.theme.MealSaverAmber

/** 확인이 필요한 멤버 관리 동작 */
private sealed interface PendingMemberAction {
    data class Remove(val member: SharedRoomMember) : PendingMemberAction
    data class Transfer(val member: SharedRoomMember) : PendingMemberAction
    data object Leave : PendingMemberAction
}

/**
 * 함께 쓰는 멤버 관리 다이얼로그
 * - 방장 / 함께 쓰는 중 / 수락 대기 상태를 보여주고
 * - 수락 대기: 초대 취소 (멤버 누구나), 함께 쓰는 중: 내보내기 (방장만), 나: 나가기 (방장이 아닐 때)
 * - 방장 넘기기: 방장 → 함께 쓰는 중인 멤버 (넘긴 뒤 나가기·탈퇴 가능)
 */
@Composable
fun SharedMembersDialog(
    state: SharedMembersState,
    onRetry: () -> Unit,
    onInviteNew: () -> Unit,
    onCancelInvitation: (email: String) -> Unit,
    onRemoveMember: (email: String) -> Unit,
    onTransferOwnership: (email: String, name: String) -> Unit,
    onLeave: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirming by remember { mutableStateOf<PendingMemberAction?>(null) }
    var choosingNewOwner by remember { mutableStateOf(false) }

    confirming?.let { action ->
        ConfirmMemberActionDialog(
            action = action,
            onConfirm = {
                when (action) {
                    is PendingMemberAction.Remove -> onRemoveMember(action.member.email)
                    is PendingMemberAction.Transfer -> onTransferOwnership(action.member.email, action.member.name)
                    PendingMemberAction.Leave -> onLeave()
                }
                confirming = null
            },
            onDismiss = { confirming = null }
        )
        return
    }

    val loadedInfo = (state as? SharedMembersState.Loaded)?.info
    if (choosingNewOwner && loadedInfo != null) {
        NewOwnerPickerDialog(
            candidates = loadedInfo.ownershipCandidates,
            onPick = {
                choosingNewOwner = false
                confirming = PendingMemberAction.Transfer(it)
            },
            onDismiss = { choosingNewOwner = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("함께 쓰는 멤버") },
        text = {
            when (state) {
                SharedMembersState.Idle, SharedMembersState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is SharedMembersState.Error -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = state.message, fontSize = 13.sp, lineHeight = 19.sp)
                    IosTintedButton(text = "다시 불러오기", onClick = onRetry, modifier = Modifier.fillMaxWidth())
                }

                is SharedMembersState.Loaded -> MemberList(
                    info = state.info,
                    onCancelInvitation = onCancelInvitation,
                    onAskRemove = { confirming = PendingMemberAction.Remove(it) },
                    onAskLeave = { confirming = PendingMemberAction.Leave },
                    onAskTransfer = { choosingNewOwner = true },
                    onInviteNew = onInviteNew
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기", maxLines = 1, softWrap = false) }
        }
    )
}

@Composable
private fun MemberList(
    info: SharedRoomInfo,
    onCancelInvitation: (String) -> Unit,
    onAskRemove: (SharedRoomMember) -> Unit,
    onAskLeave: () -> Unit,
    onAskTransfer: () -> Unit,
    onInviteNew: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "초대번호 ${info.inviteCode}",
            fontSize = 12.sp,
            color = IosSystemGray
        )
        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
            items(info.members, key = { it.email }) { member ->
                MemberRow(
                    member = member,
                    action = when {
                        info.canCancelInvitation(member) -> "초대 취소" to { onCancelInvitation(member.email) }
                        info.canRemove(member) -> "내보내기" to { onAskRemove(member) }
                        else -> null
                    }
                )
                IosHairlineDivider()
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        IosTintedButton(text = "새로 초대하기", onClick = onInviteNew, modifier = Modifier.fillMaxWidth())
        if (info.ownershipCandidates.isNotEmpty()) {
            IosTintedButton(text = "방장 넘기기", onClick = onAskTransfer, modifier = Modifier.fillMaxWidth())
        }
        if (info.canLeave) {
            IosTintedButton(
                text = "이 공유 장부에서 나가기",
                onClick = onAskLeave,
                tintColor = ExpenseCoral,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                style = MaterialTheme.typography.bodyMedium,
                text = if (info.ownershipCandidates.isNotEmpty()) {
                    "방장은 나갈 수 없어요. 다른 멤버에게 방장을 넘기면 나가거나 회원 탈퇴를 할 수 있어요."
                } else {
                    "함께 쓰는 멤버가 없으면 플랜을 '기본 (혼자 쓰기)'로 바꿔 공유를 끝낼 수 있어요."
                },
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = IosSystemGray
            )
        }
    }
}

@Composable
private fun MemberRow(member: SharedRoomMember, action: Pair<String, () -> Unit>?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (member.isMe) "${member.name} (나)" else member.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.padding(start = 6.dp))
                StatusBadge(member.status)
            }
            Text(
                text = member.email,
                fontSize = 11.sp,
                color = IosSystemGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (action != null) {
            val (label, onClick) = action
            TextButton(onClick = onClick) {
                Text(label, fontSize = 13.sp, color = ExpenseCoral, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun StatusBadge(status: MemberStatus) {
    val color: Color = when (status) {
        MemberStatus.OWNER -> MaterialTheme.colorScheme.primary
        MemberStatus.JOINED -> IncomeBlue
        MemberStatus.INVITED -> MealSaverAmber
    }
    Text(
        text = status.label,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** 방장을 넘겨받을 멤버 고르기 (함께 쓰는 중인 멤버만) */
@Composable
private fun NewOwnerPickerDialog(
    candidates: List<SharedRoomMember>,
    onPick: (SharedRoomMember) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("누구에게 방장을 넘길까요?") },
        text = {
            Column {
                Text(
                    text = "함께 쓰는 중인 멤버에게만 넘길 수 있어요.",
                    fontSize = 12.sp,
                    color = IosSystemGray
                )
                Spacer(modifier = Modifier.height(6.dp))
                candidates.forEach { member ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(member) }
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = member.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = member.email,
                            fontSize = 11.sp,
                            color = IosSystemGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IosHairlineDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("취소", maxLines = 1, softWrap = false) }
        }
    )
}

@Composable
private fun ConfirmMemberActionDialog(
    action: PendingMemberAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val confirmColor = if (action is PendingMemberAction.Transfer) MaterialTheme.colorScheme.primary else ExpenseCoral
    val (title, body, confirmLabel) = when (action) {
        is PendingMemberAction.Remove -> Triple(
            "멤버 내보내기",
            "'${action.member.name}'(${action.member.email}) 님을 공유 장부에서 내보낼까요?\n" +
                "내보낸 멤버는 더 이상 공통 생활비를 보거나 기록할 수 없고, 그동안 남긴 기록은 장부에 그대로 남습니다.",
            "내보내기"
        )
        is PendingMemberAction.Transfer -> Triple(
            "방장 넘기기",
            "'${action.member.name}'(${action.member.email}) 님에게 방장을 넘길까요?\n" +
                "넘긴 뒤에는 멤버를 내보낼 수 없고, 새 방장만 다시 넘겨줄 수 있어요. " +
                "대신 이 공유 장부에서 나가거나 회원 탈퇴를 할 수 있어요.",
            "넘기기"
        )
        PendingMemberAction.Leave -> Triple(
            "공유 장부에서 나가기",
            "이 공유 장부에서 나갈까요?\n내 휴대폰의 공통 생활비 기록은 그대로 남고, 이후로는 혼자 쓰는 장부로 바뀝니다. " +
                "다시 함께 쓰려면 방장에게 초대를 받아야 합니다.",
            "나가기"
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        // 한국어 구절 단위 줄바꿈 (단어 중간에서 끊기지 않도록)
        text = { Text(body, style = MaterialTheme.typography.bodyMedium, fontSize = 13.sp, lineHeight = 19.sp) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = confirmColor)
            ) { Text(confirmLabel, maxLines = 1, softWrap = false) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소", maxLines = 1, softWrap = false) }
        }
    )
}
