package com.budget.householdbook.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.householdbook.core.designsystem.IosHairlineDivider
import com.budget.householdbook.domain.model.FinancialInstitutions
import com.budget.householdbook.domain.model.NotificationSources
import com.budget.householdbook.ui.theme.IosSystemGray

/**
 * 앱별 알림 제외 다이얼로그
 * - 지금까지 금융 알림을 보낸 앱(과 문자)을 보여주고, 끈 출처의 알림은 대기내역에 넣지 않음
 * - 같은 결제가 카드 앱·은행 앱·문자에서 모두 올 때 한쪽만 남기는 용도
 */
@Composable
fun NotificationSourceExclusionDialog(
    seenSources: Map<String, String>,
    excludedIds: Set<String>,
    autoFilterDuplicates: Boolean,
    onConfirm: (excludedIds: Set<String>, autoFilterDuplicates: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var excluded by remember(excludedIds) { mutableStateOf(excludedIds) }
    var autoFilter by remember(autoFilterDuplicates) { mutableStateOf(autoFilterDuplicates) }
    // 문자를 맨 위에, 나머지는 앱 이름 순
    val sources = seenSources.entries
        .sortedWith(compareBy<Map.Entry<String, String>> { it.key != NotificationSources.SMS_SOURCE_ID }.thenBy { it.value })

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("앱별 알림 제외") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SwitchRow(
                    title = "같은 결제 알림 자동으로 거르기",
                    subtitle = "다른 앱에서 3분 안에 같은 금액·가맹점 알림이 또 오면 처음 들어온 것만 남깁니다.",
                    checked = autoFilter,
                    onCheckedChange = { autoFilter = it }
                )
                IosHairlineDivider()
                Text(
                    text = "끈 앱의 알림은 확인 대기 내역에 넣지 않습니다.",
                    fontSize = 12.sp,
                    color = IosSystemGray
                )
                if (sources.isEmpty()) {
                    Text(
                        text = "아직 금융 알림을 읽은 앱이 없습니다.\n알림이 한 번 들어오면 여기에 표시돼요.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = IosSystemGray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                        items(sources, key = { it.key }) { (id, label) ->
                            SwitchRow(
                                title = label,
                                subtitle = sourceSubtitle(id),
                                checked = id !in excluded,
                                onCheckedChange = { reading ->
                                    excluded = if (reading) excluded - id else excluded + id
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(excluded, autoFilter) }) {
                Text("저장", maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", maxLines = 1, softWrap = false)
            }
        }
    )
}

private fun sourceSubtitle(sourceId: String): String = when (sourceId) {
    NotificationSources.SMS_SOURCE_ID -> "모든 금융 문자"
    else -> FinancialInstitutions.byPackage(sourceId)?.displayName ?: "그 외 금융 앱"
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        ) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(text = subtitle, fontSize = 11.sp, lineHeight = 15.sp, color = IosSystemGray)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 설정 화면 요약 문구 */
fun sourceExclusionSummary(excludedIds: Set<String>, autoFilterDuplicates: Boolean): String {
    val excludedText = if (excludedIds.isEmpty()) "제외한 앱 없음" else "${excludedIds.size}개 제외"
    val dupText = if (autoFilterDuplicates) "중복 알림 자동 거르기 켜짐" else "중복 알림 거르기 꺼짐"
    return "$excludedText · $dupText"
}
