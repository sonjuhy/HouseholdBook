package com.budget.householdbook.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.householdbook.domain.usecase.UpdateDisplayNameUseCase
import com.budget.householdbook.ui.theme.ExpenseCoral
import com.budget.householdbook.ui.theme.IosSystemGray

/**
 * 로그인한 계정 한 줄: 이름 첫 글자 동그라미 + 이름 / 이메일 / 상태 + 이름 변경·로그아웃
 */
@Composable
fun AccountProfileRow(
    name: String,
    email: String,
    status: String,
    onEditName: () -> Unit,
    onSignOut: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = email,
                fontSize = 12.sp,
                color = IosSystemGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = IosSystemGray
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            TextButton(onClick = onEditName) {
                Text("이름 변경", fontSize = 13.sp, maxLines = 1, softWrap = false)
            }
            TextButton(onClick = onSignOut) {
                Text("로그아웃", fontSize = 13.sp, color = ExpenseCoral, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** 장부에 표시할 내 이름 바꾸기 (함께 쓰는 멤버에게 이 이름으로 보임) */
@Composable
fun EditDisplayNameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    val clean = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이름 변경") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "함께 쓰는 가족에게 이 이름으로 보여요.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = IosSystemGray
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(UpdateDisplayNameUseCase.MAX_LENGTH) },
                    label = { Text("이름") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(clean) }, enabled = clean.isNotBlank() && clean != currentName) {
                Text("저장", maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소", maxLines = 1, softWrap = false) }
        }
    )
}

/**
 * 함께 쓸 사람 초대: 상대의 구글 이메일 + 장부에 보일 이름(선택)
 * 상대가 그 구글 계정으로 로그인하면 자동으로 같은 장부에 연결됨
 */
@Composable
fun InvitePartnerDialog(
    onInvite: (email: String, name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val cleanEmail = email.trim().lowercase()
    val looksLikeEmail = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(cleanEmail)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("함께 쓸 사람 초대") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "상대방의 구글 계정 이메일을 입력하세요. 상대방이 그 계정으로 로그인하면 바로 같은 장부를 함께 써요.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = IosSystemGray
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    label = { Text("구글 계정 이메일") },
                    placeholder = { Text("name@gmail.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(UpdateDisplayNameUseCase.MAX_LENGTH) },
                    label = { Text("부를 이름 (선택)") },
                    placeholder = { Text("예: 엄마, 민수") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onInvite(cleanEmail, name.trim()) }, enabled = looksLikeEmail) {
                Text("초대 보내기", maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소", maxLines = 1, softWrap = false) }
        }
    )
}
