package com.budget.householdbook.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.budget.householdbook.core.common.BillingCycleCalculator

/**
 * 월 정산 시작 기준일 입력 필드
 * - [날짜 지정 | 말일] 세그먼트로 방식을 고르고, 날짜 지정이면 1~28일을 입력합니다.
 * - 말일 선택 시 [BillingCycleCalculator.LAST_DAY_OF_MONTH](99)를 전달합니다.
 */
@Composable
fun BillingStartDayField(
    startDay: Int,
    onStartDayChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isLastDay = startDay == BillingCycleCalculator.LAST_DAY_OF_MONTH
    // 말일 ↔ 날짜 지정 전환 시 이전에 입력한 날짜를 되살리기 위해 따로 보관
    var dayText by remember {
        mutableStateOf(if (isLastDay) "25" else startDay.coerceIn(1, 28).toString())
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IosSegmentedControl(
            items = listOf(false, true),
            selectedItem = isLastDay,
            onItemSelected = { lastDaySelected ->
                if (lastDaySelected) {
                    onStartDayChange(BillingCycleCalculator.LAST_DAY_OF_MONTH)
                } else {
                    onStartDayChange((dayText.toIntOrNull() ?: 1).coerceIn(1, 28))
                }
            },
            labelProvider = { if (it) "매월 말일" else "날짜 지정" },
            modifier = Modifier.fillMaxWidth()
        )
        if (!isLastDay) {
            OutlinedTextField(
                value = dayText,
                onValueChange = { input ->
                    dayText = input.filter { ch -> ch.isDigit() }.take(2)
                    dayText.toIntOrNull()?.let { onStartDayChange(it.coerceIn(1, 28)) }
                },
                label = { Text("월 정산 시작일 (1 ~ 28일)", maxLines = 1) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
