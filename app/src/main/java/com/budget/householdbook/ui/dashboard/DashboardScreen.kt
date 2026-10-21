package com.budget.householdbook.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.budget.householdbook.ui.mealsaver.CustomMealSavingDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.householdbook.core.common.BillingCycleCalculator
import com.budget.householdbook.core.common.CurrencyAndDateFormatter
import com.budget.householdbook.core.designsystem.BillingStartDayField
import com.budget.householdbook.core.designsystem.IosHairlineDivider
import com.budget.householdbook.core.designsystem.IosInsetGroupedCard
import com.budget.householdbook.core.designsystem.IosPillChip
import com.budget.householdbook.core.designsystem.IosSectionHeader
import com.budget.householdbook.core.designsystem.IosTintedButton
import com.budget.householdbook.domain.model.MealType
import com.budget.householdbook.domain.model.SpaceType
import com.budget.householdbook.ui.HouseholdUiState
import com.budget.householdbook.ui.theme.ExpenseCoral
import com.budget.householdbook.ui.theme.IncomeBlue
import com.budget.householdbook.ui.theme.IosSystemGray
import com.budget.householdbook.ui.theme.MealSaverAmber

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    uiState: HouseholdUiState,
    onNavigateToQueue: () -> Unit,
    onNavigateToMealSaver: () -> Unit,
    onChangeMonthOffset: (Int) -> Unit,
    onRecordQuickMeal: (MealType) -> Unit,
    onRecordCustomMeal: (amount: Long, label: String) -> Unit,
    onUpdateBudgetAndDay: (Long, Int) -> Unit
) {
    var showBudgetEditDialog by remember { mutableStateOf(false) }
    var showCustomMealDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(2.dp)) }

        // 1. iOS 알림 배너 스타일 대기 큐 안내 카드
        if (uiState.pendingQueue.isNotEmpty()) {
            item {
                IosInsetGroupedCard(
                    modifier = Modifier.clickable { onNavigateToQueue() },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = "확인 대기 내역 ${uiState.pendingQueue.size}건",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "알림을 확인하고 수정·분할·저장하세요",
                                fontSize = 12.sp,
                                color = IosSystemGray
                            )
                        }
                        IosTintedButton(
                            text = "확인하기",
                            onClick = onNavigateToQueue
                        )
                    }
                }
            }
        }

        // 2. 목표 예산 카드
        item {
            val isOverBudget = uiState.remainingBudgetAmount < 0L
            val progress = uiState.budgetUsageRatio.coerceIn(0f, 1f)
            val progressColor = when {
                isOverBudget || uiState.budgetUsageRatio >= 0.9f -> ExpenseCoral
                uiState.budgetUsageRatio >= 0.75f -> MealSaverAmber
                else -> MaterialTheme.colorScheme.primary
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IosSectionHeader(
                    title = "월간 목표 예산 현황",
                    actionLabel = "목표·기준일 편집",
                    onActionClick = { showBudgetEditDialog = true }
                )

                IosInsetGroupedCard(contentPadding = PaddingValues(18.dp)) {
                    // 상단 정산 주기 이동 바
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onChangeMonthOffset(-1) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "이전 주기",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = uiState.billingPeriod.periodLabel,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "정산 기준일 매월 ${BillingCycleCalculator.startDayLabel(uiState.currentSpace.billingStartDay)} · ${uiState.billingPeriod.remainingDays}일 남음",
                                fontSize = 12.sp,
                                color = IosSystemGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onChangeMonthOffset(1) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "다음 주기",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    IosHairlineDivider()
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (isOverBudget) "목표 초과 금액" else "남은 예산",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = IosSystemGray
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = if (isOverBudget) {
                            "-${CurrencyAndDateFormatter.formatWon(kotlin.math.abs(uiState.remainingBudgetAmount))}"
                        } else {
                            CurrencyAndDateFormatter.formatWon(uiState.remainingBudgetAmount)
                        },
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOverBudget) ExpenseCoral else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 예산 요약 행
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "현재 지출 (${(uiState.budgetUsageRatio * 100).toInt()}%)",
                            fontSize = 13.sp,
                            color = IosSystemGray
                        )
                        Text(
                            text = CurrencyAndDateFormatter.formatWon(uiState.totalSpentAmount),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ExpenseCoral,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "목표 예산",
                            fontSize = 13.sp,
                            color = IosSystemGray
                        )
                        Text(
                            text = CurrencyAndDateFormatter.formatWon(uiState.currentSpace.targetBudgetAmount),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "하루 권장 지출",
                            fontSize = 13.sp,
                            color = IosSystemGray
                        )
                        Text(
                            text = if (isOverBudget) {
                                "예산 초과"
                            } else {
                                CurrencyAndDateFormatter.formatWon(uiState.recommendedDailyBudget)
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    if (uiState.currentSpace.type == SpaceType.SHARED) {
                        Spacer(modifier = Modifier.height(10.dp))
                        IosHairlineDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "👥 함께 쓰는 멤버: ${uiState.currentSpace.memberNamesCsv} · ${uiState.syncStatusMessage}",
                            fontSize = 11.sp,
                            color = IosSystemGray
                        )
                    }
                }
            }
        }

        // 3. 집밥 절약 빠른 카운트 카드
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IosSectionHeader(
                    title = "집밥 절약 기록",
                    actionLabel = "전체 기록 보기",
                    onActionClick = onNavigateToMealSaver
                )

                IosInsetGroupedCard(contentPadding = PaddingValues(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "이번 주기 총 ${uiState.totalMealCount}끼 직접 해결",
                                fontSize = 13.sp,
                                color = IosSystemGray
                            )
                            Text(
                                text = "${CurrencyAndDateFormatter.formatWon(uiState.totalMealSavedAmount)} 아꼈어요",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MealSaverAmber
                            )
                        }
                        Text(
                            text = "🍚",
                            fontSize = 28.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    MealType.quickTypes.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { mealType ->
                                val price = uiState.settings.mealPriceConfig.priceFor(mealType)
                                IosTintedButton(
                                    text = "${mealType.emoji} ${mealType.displayName} +1 (${price / 1000}천원)",
                                    onClick = { onRecordQuickMeal(mealType) },
                                    modifier = Modifier.weight(1f),
                                    tintColor = MealSaverAmber
                                )
                            }
                        }
                    }
                    IosTintedButton(
                        text = "${MealType.CUSTOM.emoji} 아낀 금액 직접 입력",
                        onClick = { showCustomMealDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        tintColor = MealSaverAmber
                    )
                }
            }
        }

        // 4. 방안 3 적용: Apple Inset Grouped Table 스타일 카테고리 & 들여쓰기(Sub-Row) 세부 카테고리 지출 요약
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IosSectionHeader(title = "카테고리 및 세부 카테고리별 지출")

                IosInsetGroupedCard(contentPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp)) {
                    if (uiState.categorySpendSummaries.isEmpty()) {
                        Text(
                            text = "현재 주기에 확정된 지출 내역이 없습니다.",
                            modifier = Modifier.padding(vertical = 14.dp),
                            fontSize = 13.sp,
                            color = IosSystemGray
                        )
                    } else {
                        uiState.categorySpendSummaries.forEachIndexed { index, summary ->
                            if (index > 0) {
                                IosHairlineDivider()
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            ) {
                                // 상위 카테고리 헤더 행
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${summary.parentCategory.emoji} ${summary.parentCategory.name}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${CurrencyAndDateFormatter.formatWon(summary.totalSpent)} (${summary.percentageOfTotal.toInt()}%)",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ExpenseCoral,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                // 방안 3: 칩 구름 대신 들여쓰기된 깔끔한 Sub-Row 테이블 정렬
                                if (summary.subBreakdown.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        summary.subBreakdown.forEachIndexed { subIdx, (subName, subAmount) ->
                                            if (subIdx > 0) {
                                                IosHairlineDivider()
                                            }
                                            val subPercent = if (summary.totalSpent > 0L) {
                                                ((subAmount.toDouble() / summary.totalSpent.toDouble()) * 100.0).toInt()
                                            } else {
                                                0
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "· $subName",
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(end = 8.dp),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${CurrencyAndDateFormatter.formatWon(subAmount)} ($subPercent%)",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = IosSystemGray,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
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

    if (showBudgetEditDialog) {
        var budgetText by remember(uiState.currentSpace) {
            mutableStateOf(uiState.currentSpace.targetBudgetAmount.toString())
        }
        var startDay by remember(uiState.currentSpace) {
            mutableIntStateOf(uiState.currentSpace.billingStartDay)
        }

        AlertDialog(
            onDismissRequest = { showBudgetEditDialog = false },
            title = { Text("${uiState.currentSpace.name} 목표 예산 & 기준일") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = budgetText,
                        onValueChange = { budgetText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("전체 목표 예산 (원)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    BillingStartDayField(
                        startDay = startDay,
                        onStartDayChange = { startDay = it }
                    )
                    Text(
                        text = "예: 기준일을 25일로 설정하면 전월 25일 ~ 당월 24일, 말일로 설정하면 전월 말일 ~ 당월 말일 전날 주기로 사용 금액과 남은 금액이 자동 계산됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IosSystemGray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = budgetText.toLongOrNull() ?: uiState.currentSpace.targetBudgetAmount
                        onUpdateBudgetAndDay(target, startDay)
                        showBudgetEditDialog = false
                    }
                ) {
                    Text("저장", maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetEditDialog = false }) {
                    Text("취소", maxLines = 1, softWrap = false)
                }
            }
        )
    }

    if (showCustomMealDialog) {
        CustomMealSavingDialog(
            onConfirm = { amount, label ->
                onRecordCustomMeal(amount, label)
                showCustomMealDialog = false
            },
            onDismiss = { showCustomMealDialog = false }
        )
    }
}
