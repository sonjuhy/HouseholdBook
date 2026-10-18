package com.budget.householdbook.ui.queue

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.householdbook.core.common.CurrencyAndDateFormatter
import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.core.designsystem.IosCategorySelectorField
import com.budget.householdbook.core.designsystem.IosFilledButton
import com.budget.householdbook.core.designsystem.IosHairlineDivider
import com.budget.householdbook.core.designsystem.IosInsetGroupedCard
import com.budget.householdbook.core.designsystem.IosPillChip
import com.budget.householdbook.core.designsystem.IosSectionHeader
import com.budget.householdbook.core.designsystem.IosSegmentedControl
import com.budget.householdbook.core.designsystem.IosTintedButton
import com.budget.householdbook.data.ingestion.notification.FinancialNotificationListenerService
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.data.local.entity.PendingTransactionEntity
import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.domain.model.QueueKind
import com.budget.householdbook.domain.model.SplitApprovalItem
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.ui.HouseholdUiState
import com.budget.householdbook.ui.theme.ExpenseCoral
import com.budget.householdbook.ui.theme.IncomeBlue
import com.budget.householdbook.ui.theme.IosSystemGray
import com.budget.householdbook.ui.theme.MealSaverAmber
import com.budget.householdbook.ui.theme.TransferSlate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PendingQueueScreen(
    uiState: HouseholdUiState,
    onApproveSingle: (
        pendingId: Long,
        targetSpaceId: String,
        transactionType: TransactionType,
        amount: Long,
        merchantOrTitle: String,
        memo: String,
        categoryId: Long,
        subCategoryId: Long?
    ) -> Unit,
    onApproveSplit: (pendingId: Long, merchantOrTitle: String, splits: List<SplitApprovalItem>) -> Unit,
    onApplyMatchedCancel: (pendingId: Long) -> Unit,
    onLoadCancelCandidates: (pendingId: Long, onLoaded: (List<TransactionEntity>) -> Unit) -> Unit,
    onLinkCancelTarget: (pendingId: Long, transactionId: Long) -> Unit,
    onDismissItem: (pendingId: Long, ignoreKeyword: String?) -> Unit,
    onApproveAll: () -> Unit,
    onSimulateNotification: (Int) -> Unit,
    onRunKbQuickInquiryBeta: () -> Unit
) {
    val context = LocalContext.current
    val hasNotiPermission = remember {
        FinancialNotificationListenerService.isNotificationAccessGranted(context)
    }

    var editingItem by remember { mutableStateOf<PendingTransactionEntity?>(null) }
    var splittingItem by remember { mutableStateOf<PendingTransactionEntity?>(null) }
    var dismissingItem by remember { mutableStateOf<PendingTransactionEntity?>(null) }
    var linkingCancelItem by remember { mutableStateOf<PendingTransactionEntity?>(null) }
    var showSimulatorMenu by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(2.dp)) }

        // 상단 iOS Inset Grouped 컨트롤 패널
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IosSectionHeader(title = "자동 불러오기 및 확인 대기 내역")

                IosInsetGroupedCard(contentPadding = PaddingValues(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp)
                        ) {
                            Text(
                                text = "확인 대기 ${uiState.pendingQueue.size}건",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "알림으로 들어온 내역을 장부에 넣기 전에 먼저 확인합니다.",
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = IosSystemGray
                            )
                        }
                        if (uiState.pendingQueue.isNotEmpty()) {
                            IosFilledButton(
                                text = "일괄 승인",
                                onClick = onApproveAll,
                                icon = Icons.Default.DoneAll
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    IosHairlineDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IosTintedButton(
                            text = "예시 알림 받아보기",
                            onClick = { showSimulatorMenu = true },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Notifications
                        )
                        IosTintedButton(
                            text = "계좌 내역 불러오기",
                            onClick = onRunKbQuickInquiryBeta,
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Sync
                        )
                    }

                    if (!hasNotiPermission) {
                        Spacer(modifier = Modifier.height(8.dp))
                        IosTintedButton(
                            text = "⚙️ 결제 알림 자동 인식 권한 켜기",
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            tintColor = MealSaverAmber
                        )
                    }
                }
            }
        }

        if (uiState.pendingQueue.isEmpty()) {
            item {
                IosInsetGroupedCard(contentPadding = PaddingValues(28.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "✅ 확인할 대기 내역이 모두 정리되었습니다",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "상단 '예시 알림 받아보기'나 '계좌 내역 불러오기' 버튼으로\n새 내역을 불러올 수 있습니다.",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = IosSystemGray
                        )
                    }
                }
            }
        } else {
            item {
                IosSectionHeader(title = "확인 대기 목록 (${uiState.pendingQueue.size})")
            }

            items(uiState.pendingQueue, key = { it.id }) { item ->
                PendingQueueItemCard(
                    item = item,
                    uiState = uiState,
                    onQuickApprove = { spaceId, type, catId, subId ->
                        onApproveSingle(
                            item.id,
                            spaceId,
                            type,
                            item.parsedAmount,
                            item.parsedMerchant,
                            "",
                            catId,
                            subId
                        )
                    },
                    onOpenEdit = { editingItem = item },
                    onOpenSplit = { splittingItem = item },
                    onApplyMatchedCancel = { onApplyMatchedCancel(item.id) },
                    onOpenCancelLink = { linkingCancelItem = item },
                    onOpenDismiss = { dismissingItem = item }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(104.dp)) }
    }

    // 1. 예시 결제 알림 받아보기 다이얼로그
    if (showSimulatorMenu) {
        AlertDialog(
            onDismissRequest = { showSimulatorMenu = false },
            title = { Text("예시 결제 알림 받아보기") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "실제 은행·카드 알림이나 문자가 도착했을 때\n어떻게 자동으로 정리되는지 미리 체험해 볼 수 있습니다.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = IosSystemGray
                    )
                    IosTintedButton(
                        text = "① [KB Pay] 배달의민족 23,500원 결제 알림",
                        onClick = {
                            onSimulateNotification(0)
                            showSimulatorMenu = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    IosTintedButton(
                        text = "② [토스] 스타벅스 5,800원 결제 (내 용돈 추천)",
                        onClick = {
                            onSimulateNotification(1)
                            showSimulatorMenu = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    IosTintedButton(
                        text = "③ [내 계좌 이체] 토스 출금 ↔ KB 입금 20만원",
                        onClick = {
                            onSimulateNotification(2)
                            showSimulatorMenu = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    IosTintedButton(
                        text = "④ [결제 취소 자동 연결] 이마트 트레이더스 145,000원 취소",
                        onClick = {
                            onSimulateNotification(3)
                            showSimulatorMenu = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSimulatorMenu = false }) {
                    Text("닫기", maxLines = 1, softWrap = false)
                }
            }
        )
    }

    // 2. 단건 상세 수정 후 저장 다이얼로그 (방안 1: 1줄 카테고리 선택 바 + 2-Pane 바텀시트 적용)
    editingItem?.let { item ->
        var amountText by remember(item) { mutableStateOf(item.parsedAmount.toString()) }
        var merchantText by remember(item) { mutableStateOf(item.parsedMerchant) }
        var memoText by remember(item) { mutableStateOf("") }
        var selectedSpaceId by remember(item) { mutableStateOf(item.suggestedSpaceId) }
        var selectedType by remember(item) { mutableStateOf(item.parsedType) }

        val spaceCats = uiState.allCategories.filter { it.spaceId == selectedSpaceId }
        val parentCats = spaceCats.filter { it.parentId == null }
        var selectedCatId by remember(selectedSpaceId) {
            mutableLongStateOf(item.suggestedCategoryId ?: parentCats.firstOrNull()?.id ?: 1L)
        }
        val subCats = spaceCats.filter { it.parentId == selectedCatId }
        var selectedSubId by remember(selectedCatId) {
            mutableStateOf(item.suggestedSubCategoryId ?: subCats.firstOrNull()?.id)
        }

        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("대기 내역 수정 후 저장") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "원본: ${item.rawText}",
                        fontSize = 12.sp,
                        color = IosSystemGray
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.spaces.forEach { space ->
                            IosPillChip(
                                label = space.name,
                                selected = selectedSpaceId == space.id,
                                onClick = { selectedSpaceId = space.id }
                            )
                        }
                    }
                    IosSegmentedControl(
                        items = TransactionType.entries,
                        selectedItem = selectedType,
                        onItemSelected = { selectedType = it },
                        labelProvider = { it.displayName }
                    )
                    OutlinedTextField(
                        value = merchantText,
                        onValueChange = { merchantText = it },
                        label = { Text("사용처 / 거래 제목") },
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
                        val finalAmount = amountText.toLongOrNull() ?: item.parsedAmount
                        onApproveSingle(
                            item.id,
                            selectedSpaceId,
                            selectedType,
                            finalAmount,
                            merchantText,
                            memoText,
                            selectedCatId,
                            selectedSubId
                        )
                        editingItem = null
                    }
                ) {
                    Text("수정 후 저장", maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) {
                    Text("취소", maxLines = 1, softWrap = false)
                }
            }
        )
    }

    // 3. 분할 등록(Split Transaction: 공통 생활비 + 개인 용돈 쪼개기) 모달
    splittingItem?.let { item ->
        val total = item.parsedAmount
        val defaultSharedAmount = (total * 7) / 10
        var sharedAmountText by remember(item) { mutableStateOf(defaultSharedAmount.toString()) }
        val sharedAmount = (sharedAmountText.toLongOrNull() ?: 0L).coerceIn(0L, total)
        val personalAmount = (total - sharedAmount).coerceAtLeast(0L)

        val sharedSpaceCats = uiState.allCategories.filter { it.spaceId == AppDatabase.SPACE_ID_SHARED }
        val personalSpaceCats = uiState.allCategories.filter { it.spaceId == AppDatabase.SPACE_ID_PERSONAL }

        val defaultSharedParentId = sharedSpaceCats.firstOrNull { it.parentId == null }?.id ?: 1L
        val defaultPersonalParentId = personalSpaceCats.firstOrNull { it.parentId == null }?.id ?: 1L

        var sharedCatId by remember(item) { mutableLongStateOf(defaultSharedParentId) }
        var sharedSubId by remember(item) {
            mutableStateOf(sharedSpaceCats.firstOrNull { it.parentId == defaultSharedParentId }?.id)
        }
        var personalCatId by remember(item) { mutableLongStateOf(defaultPersonalParentId) }
        var personalSubId by remember(item) {
            mutableStateOf(personalSpaceCats.firstOrNull { it.parentId == defaultPersonalParentId }?.id)
        }

        AlertDialog(
            onDismissRequest = { splittingItem = null },
            title = { Text("✂️ 공통 생활비 + 개인 용돈 분할 등록") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${item.parsedMerchant} 총 결제 금액: ${CurrencyAndDateFormatter.formatWon(total)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "마트 결제 등 1건의 알림을 공통 생활비와 개인 용돈으로 나누어 저장합니다.",
                        fontSize = 12.sp,
                        color = IosSystemGray
                    )
                    OutlinedTextField(
                        value = sharedAmountText,
                        onValueChange = { sharedAmountText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("① 공통 생활비 금액 (원)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    IosCategorySelectorField(
                        spaceCategories = sharedSpaceCats,
                        selectedCategoryId = sharedCatId,
                        selectedSubCategoryId = sharedSubId,
                        onCategorySelected = { catId, subId ->
                            sharedCatId = catId
                            sharedSubId = subId
                        },
                        label = "공통 분류"
                    )

                    IosHairlineDivider()

                    Text(
                        text = "② 내 개인 용돈 자동 배분액: ${CurrencyAndDateFormatter.formatWon(personalAmount)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IosCategorySelectorField(
                        spaceCategories = personalSpaceCats,
                        selectedCategoryId = personalCatId,
                        selectedSubCategoryId = personalSubId,
                        onCategorySelected = { catId, subId ->
                            personalCatId = catId
                            personalSubId = subId
                        },
                        label = "용돈 분류"
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val splits = buildList {
                            if (sharedAmount > 0L) {
                                add(
                                    SplitApprovalItem(
                                        spaceId = AppDatabase.SPACE_ID_SHARED,
                                        categoryId = sharedCatId,
                                        subCategoryId = sharedSubId,
                                        amount = sharedAmount,
                                        memo = "공통 생활비 분할분"
                                    )
                                )
                            }
                            if (personalAmount > 0L) {
                                add(
                                    SplitApprovalItem(
                                        spaceId = AppDatabase.SPACE_ID_PERSONAL,
                                        categoryId = personalCatId,
                                        subCategoryId = personalSubId,
                                        amount = personalAmount,
                                        memo = "내 용돈 분할분"
                                    )
                                )
                            }
                        }
                        onApproveSplit(item.id, item.parsedMerchant, splits)
                        splittingItem = null
                    }
                ) {
                    Text("분할 저장 완료", maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(onClick = { splittingItem = null }) {
                    Text("취소", maxLines = 1, softWrap = false)
                }
            }
        )
    }

    // 4. 원본을 못 찾은 결제 취소 알림 → 사용자가 원본 결제를 직접 골라 연결
    linkingCancelItem?.let { item ->
        CancelTargetPickerDialog(
            item = item,
            uiState = uiState,
            onLoadCandidates = { onLoaded -> onLoadCancelCandidates(item.id, onLoaded) },
            onConfirm = { txId ->
                onLinkCancelTarget(item.id, txId)
                linkingCancelItem = null
            },
            onDismiss = { linkingCancelItem = null }
        )
    }

    // 5. 삭제/무시 및 자동 건너뛰기 단어 설정 다이얼로그
    dismissingItem?.let { item ->
        var blockFutureKeyword by remember(item) { mutableStateOf(false) }
        var keywordText by remember(item) { mutableStateOf(item.parsedMerchant) }

        AlertDialog(
            onDismissRequest = { dismissingItem = null },
            title = { Text("대기 내역 삭제") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("이 알림 내역을 장부에 넣지 않고 삭제할까요?")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = blockFutureKeyword,
                            onCheckedChange = { blockFutureKeyword = it }
                        )
                        Text(
                            text = "다음부터 이 단어가 들어간 알림은 자동으로 건너뛰기",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (blockFutureKeyword) {
                        OutlinedTextField(
                            value = keywordText,
                            onValueChange = { keywordText = it },
                            label = { Text("자동으로 건너뛸 단어") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDismissItem(
                            item.id,
                            if (blockFutureKeyword) keywordText else null
                        )
                        dismissingItem = null
                    }
                ) {
                    Text("삭제", maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(onClick = { dismissingItem = null }) {
                    Text("취소", maxLines = 1, softWrap = false)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingQueueItemCard(
    item: PendingTransactionEntity,
    uiState: HouseholdUiState,
    onQuickApprove: (spaceId: String, type: TransactionType, categoryId: Long, subCategoryId: Long?) -> Unit,
    onOpenEdit: () -> Unit,
    onOpenSplit: () -> Unit,
    onApplyMatchedCancel: () -> Unit,
    onOpenCancelLink: () -> Unit,
    onOpenDismiss: () -> Unit
) {
    var selectedSpaceId by remember(item.id, item.suggestedSpaceId) {
        mutableStateOf(item.suggestedSpaceId)
    }
    var selectedType by remember(item.id, item.parsedType) {
        mutableStateOf(item.parsedType)
    }

    val spaceCats: List<CategoryEntity> = uiState.allCategories.filter { it.spaceId == selectedSpaceId }
    val parentCats = spaceCats.filter { it.parentId == null }
    var selectedCatId by remember(item.id, selectedSpaceId) {
        mutableLongStateOf(item.suggestedCategoryId ?: parentCats.firstOrNull()?.id ?: 1L)
    }
    val subCats = spaceCats.filter { it.parentId == selectedCatId }
    var selectedSubId by remember(item.id, selectedCatId) {
        mutableStateOf(item.suggestedSubCategoryId ?: subCats.firstOrNull()?.id)
    }

    val badgeColor = when (item.queueKind) {
        QueueKind.CANCEL_MATCHED, QueueKind.CANCEL_UNMATCHED -> ExpenseCoral
        QueueKind.TRANSFER_PAIR -> TransferSlate
        QueueKind.NORMAL -> MaterialTheme.colorScheme.primary
    }

    IosInsetGroupedCard(contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 상단 출처 캡슐 배지 및 발생 시각
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor.copy(alpha = 0.13f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${item.sourceAppName} · ${item.queueKind.badgeLabel}",
                        fontSize = 11.sp,
                        color = badgeColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = CurrencyAndDateFormatter.formatDateTime(item.occurredAt),
                    fontSize = 12.sp,
                    color = IosSystemGray,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // 가맹점명 및 파싱 금액
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = item.parsedMerchant,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.rawText,
                        fontSize = 12.sp,
                        color = IosSystemGray
                    )
                }
                Text(
                    text = when {
                        item.queueKind == QueueKind.CANCEL_MATCHED -> "취소 ${CurrencyAndDateFormatter.formatWon(item.parsedAmount)}"
                        selectedType == TransactionType.INCOME -> "+${CurrencyAndDateFormatter.formatWon(item.parsedAmount)}"
                        selectedType == TransactionType.TRANSFER -> "이체 ${CurrencyAndDateFormatter.formatWon(item.parsedAmount)}"
                        else -> "-${CurrencyAndDateFormatter.formatWon(item.parsedAmount)}"
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (selectedType) {
                        TransactionType.INCOME -> IncomeBlue
                        TransactionType.TRANSFER -> TransferSlate
                        TransactionType.EXPENSE -> ExpenseCoral
                    },
                    maxLines = 1,
                    softWrap = false
                )
            }

            // 기존 결제 취소 연결인 경우 전용 안내 UI 표시
            if (item.queueKind == QueueKind.CANCEL_MATCHED && item.matchedTransactionId != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MealSaverAmber.copy(alpha = 0.13f))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔄 이미 장부에 기록된 같은 금액의 결제 내역과 연결되었습니다.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        IosFilledButton(
                            text = "취소 반영",
                            onClick = onApplyMatchedCancel,
                            containerColor = MealSaverAmber
                        )
                    }
                }
            } else {
                // 원본을 자동으로 찾지 못한 취소 알림: 원본 결제 직접 선택 안내
                if (item.queueKind == QueueKind.CANCEL_UNMATCHED) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ExpenseCoral.copy(alpha = 0.10f))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "❓ 어떤 결제가 취소된 건지 찾지 못했어요.\n원본 결제를 고르면 예산에 바로 반영됩니다.",
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            IosFilledButton(
                                text = "원본 결제 선택",
                                onClick = onOpenCancelLink,
                                containerColor = ExpenseCoral
                            )
                        }
                    }
                }

                // 1) 공간 선택 & 이체 제외 1줄 컨트롤
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    uiState.spaces.forEach { space ->
                        IosPillChip(
                            label = space.name,
                            selected = selectedSpaceId == space.id,
                            onClick = { selectedSpaceId = space.id }
                        )
                    }
                    IosPillChip(
                        label = "이체/집계제외",
                        selected = selectedType == TransactionType.TRANSFER,
                        onClick = {
                            selectedType = if (selectedType == TransactionType.TRANSFER) {
                                TransactionType.EXPENSE
                            } else {
                                TransactionType.TRANSFER
                            }
                        },
                        activeColor = TransferSlate
                    )
                }

                // 2) 방안 1: 1줄 브레드크럼 카테고리 셀렉터 바 (클릭 시 2-Pane iOS 바텀시트 오픈)
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

            IosHairlineDivider()

            // 하단 iOS Tinted & Filled 액션 버튼 바 (테두리 없는 깔끔한 캡슐 버튼 4종)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IosTintedButton(
                    text = "삭제",
                    onClick = onOpenDismiss,
                    modifier = Modifier.weight(1f),
                    tintColor = ExpenseCoral,
                    icon = Icons.Default.DeleteOutline
                )
                IosTintedButton(
                    text = "분할",
                    onClick = onOpenSplit,
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.CallSplit
                )
                IosTintedButton(
                    text = "수정",
                    onClick = onOpenEdit,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Edit
                )
                IosFilledButton(
                    text = "저장",
                    onClick = {
                        onQuickApprove(selectedSpaceId, selectedType, selectedCatId, selectedSubId)
                    },
                    modifier = Modifier.weight(1.1f),
                    icon = Icons.Default.Check
                )
            }
        }
    }
}

/**
 * 원본 결제 직접 선택 다이얼로그
 * - 같은 금액 결제를 먼저, 그다음 금액이 더 큰 결제(부분 취소)를 최신순으로 보여줍니다.
 */
@Composable
private fun CancelTargetPickerDialog(
    item: PendingTransactionEntity,
    uiState: HouseholdUiState,
    onLoadCandidates: (onLoaded: (List<TransactionEntity>) -> Unit) -> Unit,
    onConfirm: (transactionId: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var candidates by remember(item.id) { mutableStateOf<List<TransactionEntity>?>(null) }
    var selectedTxId by remember(item.id) { mutableStateOf<Long?>(null) }
    LaunchedEffect(item.id) {
        onLoadCandidates { loaded -> candidates = loaded }
    }
    val spaceNames = uiState.spaces.associate { it.id to it.name }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("취소된 원본 결제 선택") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${item.parsedMerchant} · 환불 ${CurrencyAndDateFormatter.formatWon(item.parsedAmount)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "금액이 같으면 원본 결제가 취소 처리되고,\n원본이 더 크면 환불 금액만큼 차감(부분 취소)됩니다.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = IosSystemGray
                )
                IosHairlineDivider()
                val list = candidates
                when {
                    list == null -> Text("불러오는 중...", fontSize = 13.sp, color = IosSystemGray)
                    list.isEmpty() -> Text(
                        text = "최근 60일 안에 연결할 수 있는 결제가 없습니다.\n대기 목록에서 직접 수정하거나 삭제해주세요.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = IosSystemGray
                    )
                    else -> LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(list, key = { it.id }) { tx ->
                            val isSelected = selectedTxId == tx.id
                            val isSameAmount = tx.amount == item.parsedAmount
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .clickable { selectedTxId = tx.id }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = tx.merchantOrTitle,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${CurrencyAndDateFormatter.formatDateTime(tx.transactionDate)} · ${spaceNames[tx.spaceId].orEmpty()}",
                                        fontSize = 11.sp,
                                        color = IosSystemGray,
                                        maxLines = 1
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = CurrencyAndDateFormatter.formatWon(tx.amount),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        text = if (isSameAmount) "전체 취소" else "부분 취소",
                                        fontSize = 11.sp,
                                        color = if (isSameAmount) ExpenseCoral else MealSaverAmber,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "선택됨",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedTxId?.let(onConfirm) },
                enabled = selectedTxId != null
            ) {
                Text("취소 반영", maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기", maxLines = 1, softWrap = false)
            }
        }
    )
}
