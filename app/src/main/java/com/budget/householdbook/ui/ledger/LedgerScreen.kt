package com.budget.householdbook.ui.ledger

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.householdbook.core.common.CurrencyAndDateFormatter
import com.budget.householdbook.core.designsystem.IosCategorySelectorField
import com.budget.householdbook.core.designsystem.IosFilledButton
import com.budget.householdbook.core.designsystem.IosHairlineDivider
import com.budget.householdbook.core.designsystem.IosInsetGroupedCard
import com.budget.householdbook.core.designsystem.IosSectionHeader
import com.budget.householdbook.core.designsystem.IosSegmentedControl
import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.ui.HouseholdUiState
import com.budget.householdbook.ui.theme.ExpenseCoral
import com.budget.householdbook.ui.theme.IncomeBlue
import com.budget.householdbook.ui.theme.IosSystemGray
import com.budget.householdbook.ui.theme.TransferSlate

@Composable
fun LedgerScreen(
    uiState: HouseholdUiState,
    onChangeMonthOffset: (Int) -> Unit,
    onAddManualTransaction: (
        spaceId: String,
        type: TransactionType,
        amount: Long,
        categoryId: Long,
        subCategoryId: Long?,
        merchantOrTitle: String,
        memo: String
    ) -> Unit,
    onCancelTransaction: (Long) -> Unit,
    onDeleteTransaction: (Long) -> Unit
) {
    var selectedFilter by remember { mutableStateOf<TransactionType?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val filterOptions = listOf<TransactionType?>(
        null,
        TransactionType.EXPENSE,
        TransactionType.INCOME,
        TransactionType.TRANSFER
    )

    val filteredTransactions = remember(uiState.transactions, selectedFilter) {
        if (selectedFilter == null) {
            uiState.transactions
        } else {
            uiState.transactions.filter { it.type == selectedFilter }
        }
    }
    val catMap = remember(uiState.allCategories) {
        uiState.allCategories.associateBy { it.id }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(2.dp)) }

        // 정산 주기 네비게이터 및 iOS Segmented 필터
        item {
            IosInsetGroupedCard(contentPadding = PaddingValues(14.dp)) {
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
                            text = "${uiState.currentSpace.name} (${filteredTransactions.size}건)",
                            fontSize = 12.sp,
                            color = IosSystemGray
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

                IosSegmentedControl(
                    items = filterOptions,
                    selectedItem = selectedFilter,
                    onItemSelected = { selectedFilter = it },
                    labelProvider = { type -> type?.displayName ?: "전체" }
                )
            }
        }

        // 섹션 헤더 + 우측 [+ 직접 추가] iOS 액션
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IosSectionHeader(
                    title = "확정 거래 내역",
                    modifier = Modifier.weight(1f)
                )
                IosFilledButton(
                    text = "직접 입력",
                    onClick = { showAddDialog = true },
                    icon = Icons.Default.Add
                )
            }
        }

        // Apple iOS UITableView.Style.insetGrouped 단일 그룹 카드 내 헤어라인 구분 리스트
        item {
            if (filteredTransactions.isEmpty()) {
                IosInsetGroupedCard(contentPadding = PaddingValues(28.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "표시할 거래 내역이 없습니다.",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "우측 상단 '직접 입력' 버튼이나 대기내역 탭에서 내역을 추가해보세요.",
                            fontSize = 12.sp,
                            color = IosSystemGray
                        )
                    }
                }
            } else {
                IosInsetGroupedCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    filteredTransactions.forEachIndexed { index, tx ->
                        if (index > 0) {
                            IosHairlineDivider()
                        }
                        val parentCat = catMap[tx.categoryId]
                        val subCat = tx.subCategoryId?.let { catMap[it] }
                        IosTransactionRow(
                            tx = tx,
                            categoryLabel = buildString {
                                append(parentCat?.emoji ?: "🏷️")
                                append(" ")
                                append(parentCat?.name ?: "미분류")
                                if (subCat != null) {
                                    append(" › ")
                                    append(subCat.emoji)
                                    append(" ")
                                    append(subCat.name)
                                }
                            },
                            onCancel = { onCancelTransaction(tx.id) },
                            onDelete = { onDeleteTransaction(tx.id) }
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(104.dp)) }
    }

    if (showAddDialog) {
        val spaceCats = uiState.allCategories.filter { it.spaceId == uiState.currentSpace.id }
        val parentCats = spaceCats.filter { it.parentId == null }
        var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
        var titleText by remember { mutableStateOf("") }
        var amountText by remember { mutableStateOf("") }
        var memoText by remember { mutableStateOf("") }
        var selectedCatId by remember(parentCats) {
            mutableLongStateOf(parentCats.firstOrNull()?.id ?: 1L)
        }
        val subCats = spaceCats.filter { it.parentId == selectedCatId }
        var selectedSubId by remember(selectedCatId) {
            mutableStateOf(subCats.firstOrNull()?.id)
        }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("${uiState.currentSpace.name} 내역 직접 입력") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    IosSegmentedControl(
                        items = TransactionType.entries,
                        selectedItem = selectedType,
                        onItemSelected = { selectedType = it },
                        labelProvider = { it.displayName }
                    )
                    OutlinedTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        label = { Text("거래처 / 내역명") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("금액 (원)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = memoText,
                        onValueChange = { memoText = it },
                        label = { Text("메모 (선택)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    IosCategorySelectorField(
                        spaceCategories = spaceCats,
                        selectedCategoryId = selectedCatId,
                        selectedSubCategoryId = selectedSubId,
                        onCategorySelected = { catId, subId ->
                            selectedCatId = catId
                            selectedSubId = subId
                        },
                        label = "분류"
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountText.toLongOrNull() ?: 0L
                        if (amt > 0L) {
                            onAddManualTransaction(
                                uiState.currentSpace.id,
                                selectedType,
                                amt,
                                selectedCatId,
                                selectedSubId,
                                titleText.ifBlank { "현금/직접 입력" },
                                memoText
                            )
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("저장", maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("취소", maxLines = 1, softWrap = false)
                }
            }
        )
    }
}

@Composable
private fun IosTransactionRow(
    tx: TransactionEntity,
    categoryLabel: String,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val amountColor = when {
        tx.isCanceled -> IosSystemGray
        tx.type == TransactionType.INCOME -> IncomeBlue
        tx.type == TransactionType.TRANSFER -> TransferSlate
        else -> ExpenseCoral
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
                text = if (tx.isCanceled) "[취소됨] ${tx.merchantOrTitle}" else tx.merchantOrTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (tx.isCanceled) TextDecoration.LineThrough else TextDecoration.None
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$categoryLabel · ${tx.authorName} · ${CurrencyAndDateFormatter.formatDateTime(tx.transactionDate)}",
                fontSize = 12.sp,
                color = IosSystemGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (tx.memo.isNotBlank()) {
                Text(
                    text = tx.memo,
                    fontSize = 12.sp,
                    color = IosSystemGray
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = when (tx.type) {
                    TransactionType.INCOME -> "+${CurrencyAndDateFormatter.formatWon(tx.amount)}"
                    TransactionType.TRANSFER -> "이체 ${CurrencyAndDateFormatter.formatWon(tx.amount)}"
                    TransactionType.EXPENSE -> "-${CurrencyAndDateFormatter.formatWon(tx.amount)}"
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = amountColor,
                textDecoration = if (tx.isCanceled) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                softWrap = false
            )

            Row {
                if (!tx.isCanceled) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "결제 취소 처리",
                            tint = IosSystemGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "삭제",
                        tint = ExpenseCoral,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
