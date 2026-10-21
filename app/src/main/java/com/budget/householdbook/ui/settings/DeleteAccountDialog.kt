package com.budget.householdbook.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.householdbook.ui.AccountDeletionState
import com.budget.householdbook.ui.theme.ExpenseCoral
import com.budget.householdbook.ui.theme.IosSystemGray

/**
 * 회원 탈퇴 확인 다이얼로그
 * - 무엇이 지워지고 무엇이 남는지 먼저 보여주고
 * - '이 휴대폰의 기록도 지우기'를 고를 수 있음 (기본은 남김)
 * - 탈퇴 버튼을 누르면 본인 확인용 구글 계정 선택 창이 뜸
 */
@Composable
fun DeleteAccountDialog(
    email: String,
    state: AccountDeletionState,
    onConfirm: (wipeLocalData: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var wipeLocalData by remember { mutableStateOf(false) }
    val inProgress = state == AccountDeletionState.InProgress
    // 한국어 구절 단위 줄바꿈 (단어 중간에서 끊기지 않도록)
    val body = MaterialTheme.typography.bodyMedium

    AlertDialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
        title = { Text("회원 탈퇴") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "$email 계정으로 탈퇴합니다.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "• 구글 로그인 정보, 플랜, 초대번호가 지워져요.\n" +
                        "• 내가 보내거나 받은 초대장이 지워져요.\n" +
                        "• 함께 쓰는 장부의 멤버라면 장부에서 나가요. 내가 남긴 공통 생활비 기록은 다른 멤버의 장부에 남아요.\n" +
                        "• 혼자 남은 방장이라면 공유 장부와 공유 기록이 모두 지워져요.\n" +
                        "• 함께 쓰는 멤버가 있는 방장은 먼저 방장을 넘겨야 해요.",
                    style = body,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ExpenseCoral.copy(alpha = 0.08f))
                        .clickable(enabled = !inProgress) { wipeLocalData = !wipeLocalData }
                        .padding(end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = wipeLocalData,
                        onCheckedChange = { wipeLocalData = it },
                        enabled = !inProgress
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("이 휴대폰의 기록도 모두 지우기", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (wipeLocalData) {
                                "장부·설정이 모두 지워지고 앱이 닫혀요. 되돌릴 수 없어요."
                            } else {
                                "지우지 않으면 로그인 없이 지금 기록을 계속 쓸 수 있어요."
                            },
                            style = body,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = IosSystemGray
                        )
                    }
                }
                Text(
                    text = "본인 확인을 위해 구글 계정을 한 번 더 선택해요.",
                    fontSize = 11.sp,
                    color = IosSystemGray
                )
                if (state is AccountDeletionState.Failed) {
                    Text(
                        text = state.message,
                        style = body,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = ExpenseCoral
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(wipeLocalData) },
                enabled = !inProgress,
                colors = ButtonDefaults.buttonColors(containerColor = ExpenseCoral)
            ) {
                if (inProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text("탈퇴하기", maxLines = 1, softWrap = false)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !inProgress) {
                Text("취소", maxLines = 1, softWrap = false)
            }
        }
    )
}
