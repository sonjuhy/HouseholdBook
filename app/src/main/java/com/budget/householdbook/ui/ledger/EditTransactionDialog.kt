package com.budget.householdbook.ui.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.householdbook.core.common.CurrencyAndDateFormatter
import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.core.designsystem.IosCategorySelectorField
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.ui.theme.IosSystemGray
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 수정 다이얼로그에서 저장할 값 */
data class TransactionEdit(
    val merchantOrTitle: String,
    val amount: Long,
    val transactionDate: Long,
    val categoryId: Long,
    val subCategoryId: Long?,
    val memo: String
)

private val DateLabelFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREA)

/**
 * 등록된 거래 수정: 이름·금액·날짜·분류·메모
 * - 날짜를 바꾸면 원래 시각(시·분)은 그대로 두고 날짜만 바꿈
 * - 오늘 이후 날짜는 고를 수 없음
 */
@Composable
fun EditTransactionDialog(
    tx: TransactionEntity,
    spaceCategories: List<CategoryEntity>,
    onSave: (TransactionEdit) -> Unit,
    onDismiss: () -> Unit,
    zoneId: ZoneId = ZoneId.systemDefault()
) {
    var title by remember { mutableStateOf(tx.merchantOrTitle) }
    var amountText by remember { mutableStateOf(tx.amount.toString()) }
    var memo by remember { mutableStateOf(tx.memo) }
    var dateMillis by remember { mutableLongStateOf(tx.transactionDate) }
    var categoryId by remember { mutableLongStateOf(tx.categoryId) }
    var subCategoryId by remember { mutableStateOf(tx.subCategoryId) }
    var showDatePicker by remember { mutableStateOf(false) }

    val amount = amountText.toLongOrNull() ?: 0L
    val canSave = title.isNotBlank() && amount > 0
    val localDate = Instant.ofEpochMilli(dateMillis).atZone(zoneId).toLocalDate()

    if (showDatePicker) {
        TransactionDatePickerDialog(
            initialDate = localDate,
            onPick = { picked ->
                // 시각은 그대로, 날짜만 바꿈
                val time = Instant.ofEpochMilli(dateMillis).atZone(zoneId).toLocalTime()
                dateMillis = picked.atTime(time).atZone(zoneId).toInstant().toEpochMilli()
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("내역 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    // 작성자는 여러 명이 쓰는 공통 생활비에서만 의미 있음
                    text = if (tx.spaceId == AppDatabase.SPACE_ID_SHARED) {
                        "${tx.type.displayName} · ${tx.authorName}님이 기록"
                    } else {
                        tx.type.displayName
                    },
                    fontSize = 12.sp,
                    color = IosSystemGray
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(40) },
                    label = { Text("이름 (가게·내역)") },
                    singleLine = true,
                    isError = title.isBlank(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() }.trimStart('0').take(10) },
                    label = { Text("금액 (원)") },
                    singleLine = true,
                    isError = amount <= 0,
                    supportingText = {
                        if (amount > 0) Text(CurrencyAndDateFormatter.formatWon(amount), fontWeight = FontWeight.SemiBold)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                // 날짜: 누르면 달력
                OutlinedTextField(
                    value = localDate.format(DateLabelFormatter),
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("날짜") },
                    trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                )
                IosCategorySelectorField(
                    spaceCategories = spaceCategories,
                    selectedCategoryId = categoryId,
                    selectedSubCategoryId = subCategoryId,
                    onCategorySelected = { catId, subId ->
                        categoryId = catId
                        subCategoryId = subId
                    },
                    label = "분류"
                )
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it.take(100) },
                    label = { Text("메모 (선택)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        TransactionEdit(
                            merchantOrTitle = title.trim(),
                            amount = amount,
                            transactionDate = dateMillis,
                            categoryId = categoryId,
                            subCategoryId = subCategoryId,
                            memo = memo.trim()
                        )
                    )
                },
                enabled = canSave
            ) {
                Text("저장", maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소", maxLines = 1, softWrap = false) }
        }
    )
}

/** 거래 날짜 고르기 (한국어 달력, 오늘까지만) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDatePickerDialog(
    initialDate: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    // DatePicker는 UTC 자정 기준 밀리초를 씀
    val todayUtcEnd = LocalDate.now().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis < todayUtcEnd
            override fun isSelectableYear(year: Int) = year <= LocalDate.now().year
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let {
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    } ?: onDismiss()
                }
            ) { Text("확인", maxLines = 1, softWrap = false) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소", maxLines = 1, softWrap = false) }
        }
    ) {
        // 달력의 월·요일 표기는 휴대폰 언어를 따름
        DatePicker(
                state = state,
                title = { Text("날짜 선택", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
                headline = {
                    val picked = state.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    } ?: initialDate
                    Text(
                        text = picked.format(DateLabelFormatter),
                        fontSize = 22.sp,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp)
                    )
                },
                showModeToggle = false
            )
    }
}
