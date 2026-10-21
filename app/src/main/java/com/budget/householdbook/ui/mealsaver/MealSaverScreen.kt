package com.budget.householdbook.ui.mealsaver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.householdbook.core.common.CurrencyAndDateFormatter
import com.budget.householdbook.core.designsystem.IosHairlineDivider
import com.budget.householdbook.core.designsystem.IosInsetGroupedCard
import com.budget.householdbook.core.designsystem.IosSectionHeader
import com.budget.householdbook.core.designsystem.IosTintedButton
import com.budget.householdbook.domain.model.MealPriceConfig
import com.budget.householdbook.domain.model.MealType
import com.budget.householdbook.ui.HouseholdUiState
import com.budget.householdbook.ui.theme.ExpenseCoral
import com.budget.householdbook.ui.theme.IosSystemGray
import com.budget.householdbook.ui.theme.MealSaverAmber

@Composable
fun MealSaverScreen(
    uiState: HouseholdUiState,
    onRecordMeal: (MealType, String) -> Unit,
    onRecordCustomMeal: (amount: Long, label: String) -> Unit,
    onDeleteMealLog: (Long) -> Unit,
    onUpdateMealPrices: (MealPriceConfig) -> Unit
) {
    var showPriceConfigDialog by remember { mutableStateOf(false) }
    var memoInput by remember { mutableStateOf("") }
    var showCustomDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(2.dp)) }

        // 1. 집밥 절약 누적 현황 카드
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IosSectionHeader(
                    title = "이번 달 집밥 절약 현황",
                    actionLabel = "끼니별 금액 편집",
                    onActionClick = { showPriceConfigDialog = true }
                )

                IosInsetGroupedCard(contentPadding = PaddingValues(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = "${uiState.billingPeriod.periodLabel} (${uiState.currentSpace.name})",
                                fontSize = 12.sp,
                                color = IosSystemGray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "총 ${uiState.totalMealCount}끼 · ${
                                    CurrencyAndDateFormatter.formatWon(uiState.totalMealSavedAmount)
                                }",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = MealSaverAmber,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "집에서 직접 챙겨 먹어 아낀 배달·외식비 금액입니다",
                                fontSize = 12.sp,
                                color = IosSystemGray
                            )
                        }
                        IosTintedButton(
                            text = "금액 설정",
                            onClick = { showPriceConfigDialog = true },
                            tintColor = MealSaverAmber,
                            icon = Icons.Default.Edit
                        )
                    }
                }
            }
        }

        // 2. 원터치 끼니별(+1) 카운트 버튼 영역
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IosSectionHeader(title = "오늘 직접 해먹은 끼니 +1 기록")

                IosInsetGroupedCard(contentPadding = PaddingValues(16.dp)) {
                    OutlinedTextField(
                        value = memoInput,
                        onValueChange = { memoInput = it },
                        label = { Text("메뉴 메모 (선택, 예: 김치볶음밥)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    MealType.quickTypes.chunked(2).forEach { rowTypes ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowTypes.forEach { mealType ->
                                val unitPrice = uiState.settings.mealPriceConfig.priceFor(mealType)
                                val countForType = uiState.mealLogs
                                    .filter { it.mealType == mealType }
                                    .sumOf { it.count }

                                IosTintedButton(
                                    text = "${mealType.emoji} ${mealType.displayName} +1 (${unitPrice / 1000}천원·${countForType}회)",
                                    onClick = {
                                        onRecordMeal(mealType, memoInput)
                                        memoInput = ""
                                    },
                                    modifier = Modifier.weight(1f),
                                    tintColor = MealSaverAmber
                                )
                            }
                        }
                    }

                    // 정해진 끼니 금액과 다를 때: 금액 직접 입력 (위 메모가 있으면 그대로 가져감)
                    IosTintedButton(
                        text = "${MealType.CUSTOM.emoji} 아낀 금액 직접 입력",
                        onClick = { showCustomDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        tintColor = MealSaverAmber
                    )
                }
            }
        }

        // 3. 이번 주기 집밥 카운트 기록 리스트
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IosSectionHeader(title = "최근 기록 내역 (${uiState.mealLogs.size})")

                if (uiState.mealLogs.isEmpty()) {
                    IosInsetGroupedCard(contentPadding = PaddingValues(24.dp)) {
                        Text(
                            text = "아직 기록된 집밥 내역이 없습니다. 위 끼니 버튼을 눌러 첫 절약 금액을 기록해보세요!",
                            fontSize = 13.sp,
                            color = IosSystemGray
                        )
                    }
                } else {
                    IosInsetGroupedCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        uiState.mealLogs.forEachIndexed { index, log ->
                            if (index > 0) {
                                IosHairlineDivider()
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = if (log.mealType == MealType.CUSTOM) {
                                            "${log.mealType.emoji} ${log.mealType.displayName}"
                                        } else {
                                            "${log.mealType.emoji} ${log.mealType.displayName} (${log.count}회)"
                                        },
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${log.memo} · ${CurrencyAndDateFormatter.formatDateTime(log.occurredAt)}",
                                        fontSize = 12.sp,
                                        color = IosSystemGray
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "+${CurrencyAndDateFormatter.formatWon(log.savedAmount)}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MealSaverAmber,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    IconButton(
                                        onClick = { onDeleteMealLog(log.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "기록 삭제",
                                            tint = ExpenseCoral,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(104.dp)) }
    }

    if (showCustomDialog) {
        CustomMealSavingDialog(
            initialLabel = memoInput,
            onConfirm = { amount, label ->
                onRecordCustomMeal(amount, label)
                memoInput = ""
                showCustomDialog = false
            },
            onDismiss = { showCustomDialog = false }
        )
    }

    // 끼니별 단일 단가 설정 다이얼로그
    if (showPriceConfigDialog) {
        val cfg = uiState.settings.mealPriceConfig
        var breakfastText by remember(cfg) { mutableStateOf(cfg.breakfastPrice.toString()) }
        var lunchText by remember(cfg) { mutableStateOf(cfg.lunchPrice.toString()) }
        var dinnerText by remember(cfg) { mutableStateOf(cfg.dinnerPrice.toString()) }
        var snackText by remember(cfg) { mutableStateOf(cfg.snackPrice.toString()) }

        AlertDialog(
            onDismissRequest = { showPriceConfigDialog = false },
            title = { Text("끼니별 1회 절약 단가 설정") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "끼니를 직접 해결했을 때 회당 얼마(배달비·외식비)를 아낀 것으로 기록할지 단일 금액을 설정합니다.",
                        fontSize = 12.sp,
                        color = IosSystemGray
                    )
                    OutlinedTextField(
                        value = breakfastText,
                        onValueChange = { breakfastText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("🌅 아침 집밥 1회 금액 (원)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = lunchText,
                        onValueChange = { lunchText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("☀️ 점심 집밥/도시락 1회 금액 (원)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dinnerText,
                        onValueChange = { dinnerText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("🌙 저녁 집밥(배달 대체) 1회 금액 (원)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = snackText,
                        onValueChange = { snackText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("☕ 간식·홈카페 1회 금액 (원)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateMealPrices(
                            MealPriceConfig(
                                breakfastPrice = breakfastText.toLongOrNull() ?: cfg.breakfastPrice,
                                lunchPrice = lunchText.toLongOrNull() ?: cfg.lunchPrice,
                                dinnerPrice = dinnerText.toLongOrNull() ?: cfg.dinnerPrice,
                                snackPrice = snackText.toLongOrNull() ?: cfg.snackPrice
                            )
                        )
                        showPriceConfigDialog = false
                    }
                ) {
                    Text("저장", maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPriceConfigDialog = false }) {
                    Text("취소", maxLines = 1, softWrap = false)
                }
            }
        )
    }
}
