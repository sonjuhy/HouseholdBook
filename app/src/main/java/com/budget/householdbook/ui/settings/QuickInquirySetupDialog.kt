package com.budget.householdbook.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.householdbook.domain.model.FinancialInstitution
import com.budget.householdbook.domain.model.FinancialInstitutions
import com.budget.householdbook.domain.model.InstitutionGroup
import com.budget.householdbook.domain.model.QuickInquiryCredentials
import com.budget.householdbook.domain.model.QuickInquiryStatus
import com.budget.householdbook.ui.theme.ExpenseCoral
import com.budget.householdbook.ui.theme.IncomeBlue
import com.budget.householdbook.ui.theme.IosSystemGray
import com.budget.householdbook.ui.theme.MealSaverAmber

/** 계좌 빠른조회 대상이 될 수 있는 은행 (카드·간편결제 제외) */
private val BANKS: List<FinancialInstitution> =
    FinancialInstitutions.ALL.filter { it.group != InstitutionGroup.CARD_PAY }

private fun QuickInquiryStatus.label(): String = when (this) {
    QuickInquiryStatus.SUPPORTED_BETA -> "지원 [Beta]"
    QuickInquiryStatus.NOT_YET_SUPPORTED -> "준비 중"
    QuickInquiryStatus.NOT_OFFERED -> "서비스 없음"
}

/**
 * 은행 계좌 빠른조회 설정 다이얼로그
 * - 은행을 고르고, 앱에서 지원하는 은행이면 조회 정보를 입력해 저장
 * - 아직 지원하지 않는 은행은 은행 서비스 이름과 미리 등록하는 위치, 대신 쓸 수 있는 방법(알림 읽기)을 안내
 */
@Composable
fun QuickInquirySetupDialog(
    credentials: QuickInquiryCredentials,
    userRealName: String,
    onSave: (institutionId: String, accountNo: String, birthOrBiz: String, pw4: String, realName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var institutionId by remember(credentials) { mutableStateOf(credentials.institutionId) }
    var showBankList by remember { mutableStateOf(false) }
    var accText by remember(credentials) { mutableStateOf(credentials.accountNumber) }
    var birthText by remember(credentials) { mutableStateOf(credentials.birthOrBizNumber) }
    var pwText by remember(credentials) { mutableStateOf(credentials.quickPassword4Digit) }
    var realNameText by remember(userRealName) { mutableStateOf(userRealName) }

    val institution = FinancialInstitutions.byId(institutionId) ?: BANKS.first()
    val info = institution.quickInquiry
    val isSupported = info.status == QuickInquiryStatus.SUPPORTED_BETA
    val canSave = isSupported && accText.isNotBlank() && birthText.length >= 6

    if (showBankList) {
        BankListDialog(
            selectedId = institution.id,
            onSelect = {
                institutionId = it
                showBankList = false
            },
            onDismiss = { showBankList = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("은행 계좌 빠른조회 설정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 은행 선택 1줄 요약 바
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { showBankList = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("은행", fontSize = 13.sp, color = IosSystemGray, modifier = Modifier.padding(end = 12.dp))
                    Text(
                        text = institution.displayName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Text("변경 ›", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
                }

                if (isSupported) {
                    Text(
                        text = "입력한 계좌 정보는 다른 곳으로 전송되지 않고 내 휴대폰 안에만 잠금 보관됩니다.\n" +
                            "${info.serviceName ?: "빠른조회"} 서비스는 먼저 은행에 등록해야 합니다: ${info.registerPath.orEmpty()}",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = IosSystemGray
                    )
                    OutlinedTextField(
                        value = accText,
                        onValueChange = { accText = it },
                        label = { Text("${institution.displayName} 계좌번호") },
                        placeholder = { Text("예: 123456-78-901234") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = birthText,
                        onValueChange = { birthText = it.filter { ch -> ch.isDigit() }.take(10) },
                        label = { Text("생년월일(6자리) 또는 사업자번호") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pwText,
                        onValueChange = { pwText = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("빠른조회 비밀번호 (4자리)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = realNameText,
                        onValueChange = { realNameText = it },
                        label = { Text("본인 이름 (내 계좌끼리 이체한 내역 자동 구분용)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    UnsupportedBankNotice(institution)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(institution.id, accText, birthText, pwText, realNameText) },
                enabled = canSave
            ) {
                Text("안전하게 저장", maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기", maxLines = 1, softWrap = false)
            }
        }
    )
}

@Composable
private fun UnsupportedBankNotice(institution: FinancialInstitution) {
    val info = institution.quickInquiry
    val message = when (info.status) {
        QuickInquiryStatus.NOT_YET_SUPPORTED ->
            "${institution.displayName}은(는) '${info.serviceName}'(으)로 로그인 없이 거래내역을 조회할 수 있지만, 앱 연동은 아직 준비 중이에요.\n\n" +
                "미리 등록하는 곳: ${info.registerPath}\n\n" +
                "지금은 '알림을 읽어올 금융사'에서 ${institution.displayName}을(를) 켜 두면 앱 알림·문자로 내역을 가져올 수 있어요."
        else ->
            "${institution.displayName}은(는) 로그인 없이 거래내역을 조회하는 서비스를 제공하지 않아요.\n\n" +
                "'알림을 읽어올 금융사'에서 ${institution.displayName}을(를) 켜 두면 앱 알림으로 내역을 가져올 수 있어요."
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MealSaverAmber.copy(alpha = 0.12f))
            .padding(12.dp)
    ) {
        Text(text = message, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun BankListDialog(
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("은행 선택") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                InstitutionGroup.entries.filter { it != InstitutionGroup.CARD_PAY }.forEach { group ->
                    val banks = BANKS.filter { it.group == group }
                    if (banks.isEmpty()) return@forEach
                    item(key = "header-${group.name}") {
                        Text(
                            text = group.displayName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IosSystemGray,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                        )
                    }
                    items(banks, key = { it.id }) { bank ->
                        val status = bank.quickInquiry.status
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (bank.id == selectedId) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                                )
                                .clickable { onSelect(bank.id) }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(bank.displayName, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(
                                text = status.label(),
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                color = when (status) {
                                    QuickInquiryStatus.SUPPORTED_BETA -> IncomeBlue
                                    QuickInquiryStatus.NOT_YET_SUPPORTED -> MealSaverAmber
                                    QuickInquiryStatus.NOT_OFFERED -> ExpenseCoral
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기", maxLines = 1, softWrap = false) }
        }
    )
}
