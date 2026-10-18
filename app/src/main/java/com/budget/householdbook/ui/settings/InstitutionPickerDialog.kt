package com.budget.householdbook.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.householdbook.core.designsystem.IosHairlineDivider
import com.budget.householdbook.core.designsystem.IosTintedButton
import com.budget.householdbook.domain.model.FinancialInstitutions
import com.budget.householdbook.domain.model.InstitutionGroup
import com.budget.householdbook.ui.theme.IosSystemGray

/**
 * 알림·문자를 읽어올 금융기관 선택 다이얼로그
 * - 시중·특수·인터넷·지방은행, 상호금융, 카드·간편결제를 묶음별로 보여주고 체크한 곳만 대기내역으로 가져옴
 */
@Composable
fun InstitutionPickerDialog(
    enabledIds: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember(enabledIds) { mutableStateOf(enabledIds) }
    val allIds = FinancialInstitutions.DEFAULT_ENABLED_IDS
    val grouped = FinancialInstitutions.ALL.groupBy { it.group }

    fun toggle(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("알림을 읽어올 금융사") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "체크한 은행·카드사 앱 알림과 문자만\n확인 대기 내역으로 가져옵니다.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = IosSystemGray
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IosTintedButton(text = "전체 선택", onClick = { selected = allIds }, modifier = Modifier.weight(1f))
                    IosTintedButton(text = "전체 해제", onClick = { selected = emptySet() }, modifier = Modifier.weight(1f))
                }
                IosHairlineDivider()
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    InstitutionGroup.entries.forEach { group ->
                        val institutions = grouped[group].orEmpty()
                        if (institutions.isEmpty()) return@forEach
                        item(key = "header-${group.name}") {
                            Text(
                                text = group.displayName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IosSystemGray,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                            )
                        }
                        items(institutions, key = { it.id }) { institution ->
                            InstitutionRow(
                                name = institution.displayName,
                                checked = institution.id in selected,
                                onToggle = { toggle(institution.id) }
                            )
                        }
                    }
                    item(key = "header-other") {
                        Text(
                            text = "기타",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IosSystemGray,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                        )
                    }
                    item(key = FinancialInstitutions.OTHER_ID) {
                        InstitutionRow(
                            name = FinancialInstitutions.OTHER_DISPLAY_NAME,
                            checked = FinancialInstitutions.OTHER_ID in selected,
                            onToggle = { toggle(FinancialInstitutions.OTHER_ID) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selected) }) {
                Text("${selected.size}곳 저장", maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", maxLines = 1, softWrap = false)
            }
        }
    )
}

@Composable
private fun InstitutionRow(name: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(text = name, fontSize = 14.sp, maxLines = 1)
    }
}

/** 설정 화면 요약 문구: "은행 23곳 · 카드 3곳 선택" */
fun institutionSelectionSummary(enabledIds: Set<String>): String {
    val selected = FinancialInstitutions.ALL.filter { it.id in enabledIds }
    val cards = selected.count { it.group == InstitutionGroup.CARD_PAY }
    val banks = selected.size - cards
    val other = if (FinancialInstitutions.OTHER_ID in enabledIds) " · 그 외 포함" else ""
    return if (selected.isEmpty() && other.isEmpty()) "선택한 금융사 없음 (알림을 읽지 않음)" else "은행 ${banks}곳 · 카드 ${cards}곳$other"
}
