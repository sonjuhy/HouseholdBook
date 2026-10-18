package com.budget.householdbook.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.ui.theme.IosSegmentedThumbDark
import com.budget.householdbook.ui.theme.IosSystemGray
import com.budget.householdbook.ui.theme.LocalIsDarkTheme

/**
 * 1. Apple iOS UISegmentedControl 스타일의 슬라이딩 캡슐 피커
 * - Light 모드에서는 #E5E5EA 트랙 위에 #FFFFFF 썸이 올라오고,
 * - Dark 모드에서는 #2C2C2E 트랙 위에 #3A3A3C 썸이 올라와 카드(#1C1C1E) 내부에서도 선명하게 구분됩니다.
 */
@Composable
fun <T> IosSegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val thumbColor = if (isDark) IosSegmentedThumbDark else MaterialTheme.colorScheme.surface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val isSelected = item == selectedItem
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (isSelected) {
                            Modifier
                                .shadow(elevation = if (isDark) 0.dp else 1.dp, shape = RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .background(thumbColor)
                        } else {
                            Modifier.clip(RoundedCornerShape(8.dp))
                        }
                    )
                    .clickable { onItemSelected(item) }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = labelProvider(item),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else IosSystemGray,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 2. Apple iOS Inset Grouped List 카드 (UITableView.Style.insetGrouped)
 */
@Composable
fun IosInsetGroupedCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

/**
 * 3. Apple iOS 0.5dp Hairline Separator
 */
@Composable
fun IosHairlineDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    )
}

/**
 * 4. Apple iOS 섹션 헤더
 */
@Composable
fun IosSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = IosSystemGray,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (actionLabel != null && onActionClick != null) {
            Text(
                text = actionLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onActionClick() },
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * 5. Apple iOS Tinted Action Button
 */
@Composable
fun IosTintedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tintColor: Color = MaterialTheme.colorScheme.primary,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = tintColor.copy(alpha = 0.12f),
            contentColor = tintColor
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 6. Apple iOS Primary Filled Button
 */
@Composable
fun IosFilledButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 7. Apple iOS Capsule Pill Chip
 */
@Composable
fun IosPillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary
) {
    val bgColor = if (selected) {
        activeColor.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }
    val textColor = if (selected) activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 8. [방안 1] 단일 카테고리 선택 바 + 2단 iOS 바텀시트 피커 (`IosCategorySelectorField`)
 * - 수십 개의 칩을 화면에 늘어놓는 대신, 평소에는 `[🍔 식비 › 🛵 배달/외식   변경 ›]` 1줄로 깔끔하게 보여주고
 *   터치 시 2열(좌측 상위 카테고리 / 우측 세부 카테고리) 바텀시트를 띄워 원터치로 선택합니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosCategorySelectorField(
    spaceCategories: List<CategoryEntity>,
    selectedCategoryId: Long,
    selectedSubCategoryId: Long?,
    onCategorySelected: (categoryId: Long, subCategoryId: Long?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "카테고리"
) {
    var showSheet by remember { mutableStateOf(false) }

    val parentCategories = remember(spaceCategories) {
        spaceCategories.filter { it.parentId == null }
    }
    val currentParent = parentCategories.firstOrNull { it.id == selectedCategoryId }
        ?: parentCategories.firstOrNull()
    val currentSub = spaceCategories.firstOrNull {
        it.id == selectedSubCategoryId && it.parentId == currentParent?.id
    }

    val displayPath = buildString {
        if (currentParent != null) {
            append("${currentParent.emoji} ${currentParent.name}")
            if (currentSub != null) {
                append("  ›  ${currentSub.emoji} ${currentSub.name}")
            } else {
                append("  ›  세부 전체")
            }
        } else {
            append("분류 선택")
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable { showSheet = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = IosSystemGray,
            maxLines = 1,
            softWrap = false
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = displayPath,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "변경",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    if (showSheet && parentCategories.isNotEmpty()) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var activeParentId by remember(currentParent) {
            mutableLongStateOf(currentParent?.id ?: parentCategories.first().id)
        }
        val activeSubs = remember(spaceCategories, activeParentId) {
            spaceCategories.filter { it.parentId == activeParentId }
        }

        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 320.dp, max = 460.dp)
                    .padding(bottom = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "카테고리 및 세부 분류 선택",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "좌측에서 대분류를 고른 뒤 우측 세부 항목을 터치하세요",
                            fontSize = 12.sp,
                            color = IosSystemGray
                        )
                    }
                    Text(
                        text = "닫기",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { showSheet = false }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                IosHairlineDivider()

                // 2단 분할 피커 (좌측 42%: 상위 카테고리 / 우측 58%: 세부 카테고리)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // 좌측: 상위 카테고리 목록
                    LazyColumn(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(parentCategories, key = { it.id }) { parent ->
                            val isSelectedParent = parent.id == activeParentId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelectedParent) {
                                            MaterialTheme.colorScheme.surface
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .clickable { activeParentId = parent.id }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${parent.emoji} ${parent.name}",
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelectedParent) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelectedParent) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // 우측: 선택된 상위 카테고리의 세부 카테고리 체크 리스트
                    LazyColumn(
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        if (activeSubs.isEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            onCategorySelected(activeParentId, null)
                                            showSheet = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "기본 선택 (세부 없음)",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else {
                            items(activeSubs, key = { it.id }) { sub ->
                                val isChecked = activeParentId == selectedCategoryId && sub.id == selectedSubCategoryId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isChecked) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                            } else {
                                                Color.Transparent
                                            }
                                        )
                                        .clickable {
                                            onCategorySelected(activeParentId, sub.id)
                                            showSheet = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 13.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${sub.emoji} ${sub.name}",
                                        fontSize = 14.sp,
                                        fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isChecked) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isChecked) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "선택됨",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                IosHairlineDivider()
                            }

                            // 세부 미지정(상위만 선택) 옵션
                            item {
                                val isNoneChecked = activeParentId == selectedCategoryId && selectedSubCategoryId == null
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onCategorySelected(activeParentId, null)
                                            showSheet = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 13.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "기타 (세부 분류 미지정)",
                                        fontSize = 13.sp,
                                        color = IosSystemGray
                                    )
                                    if (isNoneChecked) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "선택됨",
                                            tint = MaterialTheme.colorScheme.primary,
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
    }
}
