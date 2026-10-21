package com.budget.householdbook.ui.mealsaver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.budget.householdbook.core.designsystem.IosTintedButton
import com.budget.householdbook.domain.usecase.RecordCustomMealSavingUseCase
import com.budget.householdbook.ui.theme.ExpenseCoral
import com.budget.householdbook.ui.theme.IosSystemGray
import com.budget.householdbook.ui.theme.MealSaverAmber

/** 금액을 빠르게 더하는 버튼 */
private val QuickAddAmounts = listOf(1_000L, 5_000L, 10_000L)

/**
 * 아낀 금액 직접 입력 다이얼로그
 * - 아침·점심·저녁·간식 단가로 맞지 않을 때 (도시락, 텀블러 커피, 장 봐서 해 먹은 파티 등)
 * - 금액(필수) + 무엇을 아꼈는지(선택)
 */
@Composable
fun CustomMealSavingDialog(
    initialLabel: String = "",
    onConfirm: (amount: Long, label: String) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var label by remember { mutableStateOf(initialLabel) }
    val amount = amountText.toLongOrNull() ?: 0L
    val tooLarge = amount > RecordCustomMealSavingUseCase.MAX_AMOUNT
    val canSave = amount > 0 && !tooLarge

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("아낀 금액 직접 입력") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "정해진 끼니 금액과 다를 때, 이번에 아낀 배달·외식비를 직접 적어요.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = IosSystemGray
                )
                OutlinedTextField(
                    value = amountText,
                    // 숫자만, 앞자리 0 없이 (최대 8자리)
                    onValueChange = { input ->
                        amountText = input.filter { it.isDigit() }.trimStart('0').take(8)
                    },
                    label = { Text("아낀 금액 (원)") },
                    placeholder = { Text("예: 8500") },
                    singleLine = true,
                    isError = tooLarge,
                    supportingText = {
                        when {
                            tooLarge -> Text(
                                "한 번에 ${RecordCustomMealSavingUseCase.MAX_AMOUNT / 10_000}만 원까지 입력할 수 있어요.",
                                color = ExpenseCoral
                            )
                            amount > 0 -> Text(CurrencyAndDateFormatter.formatWon(amount), fontWeight = FontWeight.SemiBold)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QuickAddAmounts.forEach { add ->
                        IosTintedButton(
                            text = "+${add / 1_000}천",
                            onClick = { amountText = (amount + add).toString() },
                            tintColor = MealSaverAmber,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(40) },
                    label = { Text("무엇을 아꼈나요? (선택)") },
                    placeholder = { Text("예: 회사에 도시락 싸 감") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(amount, label.trim()) },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = MealSaverAmber)
            ) {
                Text("기록하기", maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소", maxLines = 1, softWrap = false) }
        }
    )
}
