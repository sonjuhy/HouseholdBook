package com.budget.householdbook.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.householdbook.core.common.BillingCycleCalculator
import com.budget.householdbook.core.designsystem.IosFilledButton
import com.budget.householdbook.core.designsystem.IosHairlineDivider
import com.budget.householdbook.core.designsystem.IosInsetGroupedCard
import com.budget.householdbook.core.designsystem.IosPillChip
import com.budget.householdbook.core.designsystem.IosSectionHeader
import com.budget.householdbook.core.designsystem.IosSegmentedControl
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.domain.usecase.TransactionCsv
import com.budget.householdbook.ui.theme.IosSystemGray
import java.time.LocalDate

/** 내보낼 범위를 고르면 [fileName]을 제안해 파일 저장 창을 띄움 */
data class CsvExportRequest(
    val spaceIds: List<String>,
    val fromMonthOffset: Int,
    val toMonthOffset: Int,
    val fileName: String
)

/** 설정 > 내역 내보내기: 저장 창(ACTION_CREATE_DOCUMENT)에서 고른 파일에 CSV로 저장 */
@Composable
fun CsvExportCard(
    spaces: List<LedgerSpaceEntity>,
    currentSpaceId: String,
    onExport: (uri: String, spaceIds: List<String>, fromMonthOffset: Int, toMonthOffset: Int) -> Unit
) {
    var pending by remember { mutableStateOf<CsvExportRequest?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val request = pending
        pending = null
        // 저장 창에서 취소하면 uri가 null
        if (uri != null && request != null) {
            onExport(uri.toString(), request.spaceIds, request.fromMonthOffset, request.toMonthOffset)
        }
    }
    CsvExportContent(
        spaces = spaces,
        currentSpaceId = currentSpaceId,
        onRequestExport = { request ->
            pending = request
            launcher.launch(request.fileName)
        }
    )
}

private const val ALL_SPACES = "__all__"

/** 가장 오래 전으로 고를 수 있는 주기 (대시보드 이동 범위보다 넉넉하게 3년) */
internal const val CSV_EXPORT_MIN_OFFSET = -36

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CsvExportContent(
    spaces: List<LedgerSpaceEntity>,
    currentSpaceId: String,
    onRequestExport: (CsvExportRequest) -> Unit,
    today: LocalDate = LocalDate.now()
) {
    val spaceOptions = remember(spaces) {
        if (spaces.size > 1) spaces.map { it.id } + ALL_SPACES else spaces.map { it.id }
    }
    var selectedSpace by rememberSaveable(spaceOptions) {
        mutableStateOf(currentSpaceId.takeIf { it in spaceOptions } ?: spaceOptions.firstOrNull() ?: ALL_SPACES)
    }
    var fromOffset by rememberSaveable { mutableIntStateOf(0) }
    var toOffset by rememberSaveable { mutableIntStateOf(0) }

    val selectedSpaces = if (selectedSpace == ALL_SPACES) spaces else spaces.filter { it.id == selectedSpace }
    // 기간 표기는 고른 장부(전체면 첫 장부)의 월 기준일로
    val labelStartDay = selectedSpaces.firstOrNull()?.billingStartDay ?: 1
    fun periodLabel(offset: Int) =
        BillingCycleCalculator.calculatePeriod(baseDate = today, billingStartDay = labelStartDay, monthOffset = offset).periodLabel
    val monthCount = toOffset - fromOffset + 1
    val mixedStartDays = selectedSpaces.map { it.billingStartDay }.distinct().size > 1

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        IosSectionHeader(title = "내역 내보내기 (CSV)")
        IosInsetGroupedCard(contentPadding = PaddingValues(16.dp)) {
            Text(
                text = "고른 기간의 거래 내역을 엑셀에서 열 수 있는 CSV 파일로 저장합니다. 기간은 월 기준일에 맞춘 한 달 단위입니다.",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = IosSystemGray
            )

            if (spaceOptions.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                IosSegmentedControl(
                    items = spaceOptions,
                    selectedItem = selectedSpace,
                    onItemSelected = { selectedSpace = it },
                    labelProvider = { id -> if (id == ALL_SPACES) "전체" else spaces.firstOrNull { it.id == id }?.name ?: id },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple("이번 달", 0, 0),
                    Triple("지난 달", -1, -1),
                    Triple("최근 3개월", -2, 0),
                    Triple("최근 6개월", -5, 0)
                ).forEach { (label, from, to) ->
                    IosPillChip(
                        label = label,
                        selected = fromOffset == from && toOffset == to,
                        onClick = {
                            fromOffset = from
                            toOffset = to
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            IosHairlineDivider()
            Spacer(modifier = Modifier.height(4.dp))

            MonthStepperRow(
                title = "시작",
                label = periodLabel(fromOffset),
                canPrev = fromOffset > CSV_EXPORT_MIN_OFFSET,
                canNext = fromOffset < 0,
                onPrev = { fromOffset -= 1 },
                onNext = {
                    fromOffset += 1
                    if (toOffset < fromOffset) toOffset = fromOffset
                }
            )
            MonthStepperRow(
                title = "끝",
                label = periodLabel(toOffset),
                canPrev = toOffset > CSV_EXPORT_MIN_OFFSET,
                canNext = toOffset < 0,
                onPrev = {
                    toOffset -= 1
                    if (fromOffset > toOffset) fromOffset = toOffset
                },
                onNext = { toOffset += 1 }
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = buildString {
                    append("${monthCount}개월")
                    if (mixedStartDays) append(" · 장부마다 자기 월 기준일로 나눕니다")
                    append(" · 취소된 내역은 '취소' 열에 표시")
                },
                fontSize = 12.sp,
                color = IosSystemGray
            )

            Spacer(modifier = Modifier.height(12.dp))
            IosFilledButton(
                text = "CSV로 저장",
                onClick = {
                    val label = if (selectedSpace == ALL_SPACES) "전체" else selectedSpaces.firstOrNull()?.name.orEmpty()
                    onRequestExport(
                        CsvExportRequest(
                            spaceIds = selectedSpaces.map { it.id },
                            fromMonthOffset = fromOffset,
                            toMonthOffset = toOffset,
                            fileName = TransactionCsv.fileName(
                                spaceLabel = label,
                                fromMonth = TransactionCsv.monthOf(labelStartDay, fromOffset, today),
                                toMonth = TransactionCsv.monthOf(labelStartDay, toOffset, today)
                            )
                        )
                    )
                },
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun MonthStepperRow(
    title: String,
    label: String,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            color = IosSystemGray,
            modifier = Modifier.width(32.dp)
        )
        IconButton(onClick = onPrev, enabled = canPrev, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "$title 이전 달",
                tint = if (canPrev) MaterialTheme.colorScheme.primary else IosSystemGray
            )
        }
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onNext, enabled = canNext, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "$title 다음 달",
                tint = if (canNext) MaterialTheme.colorScheme.primary else IosSystemGray
            )
        }
    }
}
