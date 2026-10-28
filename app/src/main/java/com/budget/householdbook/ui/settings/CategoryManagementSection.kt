package com.budget.householdbook.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.householdbook.core.designsystem.IosHairlineDivider
import com.budget.householdbook.core.designsystem.IosInsetGroupedCard
import com.budget.householdbook.core.designsystem.IosSectionHeader
import com.budget.householdbook.core.designsystem.IosSegmentedControl
import com.budget.householdbook.core.designsystem.IosTintedButton
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.domain.model.CategoryGroup
import com.budget.householdbook.domain.model.SpaceType
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.ui.shortLabel
import com.budget.householdbook.ui.theme.ExpenseCoral
import com.budget.householdbook.ui.theme.IosSystemGray

/** 공용·개인 탭 아래 설명: 지금 고른 장부의 분류만 바뀐다는 것을 알려줌 */
internal fun categorySpaceCaption(space: LedgerSpaceEntity, spaces: List<LedgerSpaceEntity>): String {
    val others = spaces.filter { it.id != space.id }
    val otherText = if (others.isEmpty()) "" else " 여기서 바꿔도 ${others.joinToString { it.shortLabel() }} 분류는 그대로예요."
    return if (space.type == SpaceType.SHARED) {
        "${space.name} 장부에서 쓰는 분류예요.$otherText"
    } else {
        "${space.name} 장부에서만 쓰는 분류예요.$otherText"
    }
}

/**
 * 설정 > 분류 관리: 공용·개인 분류를 탭으로 나눠 따로 추가·수정·삭제
 * - 처음에는 지금 보고 있는 장부의 분류를 보여주고, 탭으로 다른 장부 분류로 바꿈 (상단 장부 전환과 무관)
 */
@Composable
fun CategoryManagementSection(
    spaces: List<LedgerSpaceEntity>,
    initialSpaceId: String,
    categoryGroupsBySpace: Map<String, List<CategoryGroup>>,
    onAddCategory: (
        spaceId: String,
        parentId: Long?,
        transactionType: TransactionType,
        name: String,
        emoji: String,
        colorHex: String
    ) -> Unit,
    onUpdateCategory: (CategoryEntity) -> Unit,
    onDeleteCategory: (Long) -> Unit
) {
    var selectedSpaceId by rememberSaveable { mutableStateOf(initialSpaceId) }
    val selectedSpace = spaces.firstOrNull { it.id == selectedSpaceId } ?: spaces.firstOrNull() ?: return
    val groups = categoryGroupsBySpace[selectedSpace.id].orEmpty()

    var addingParent by remember { mutableStateOf(false) }
    var addingSubForParent by remember { mutableStateOf<CategoryEntity?>(null) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        IosSectionHeader(
            title = "분류 관리",
            actionLabel = "+ 대분류 추가",
            onActionClick = { addingParent = true }
        )

        IosInsetGroupedCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
            Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                if (spaces.size > 1) {
                    IosSegmentedControl(
                        // 공용을 항상 왼쪽에
                        items = spaces.sortedBy { it.type != SpaceType.SHARED },
                        selectedItem = selectedSpace,
                        onItemSelected = { selectedSpaceId = it.id },
                        labelProvider = { "${it.shortLabel()} 분류" }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = categorySpaceCaption(selectedSpace, spaces),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = IosSystemGray
                )
            }

            if (groups.isEmpty()) {
                IosHairlineDivider()
                Text(
                    text = "아직 분류가 없어요. 오른쪽 위 '+ 대분류 추가'로 만들어 보세요.",
                    fontSize = 13.sp,
                    color = IosSystemGray,
                    modifier = Modifier.padding(vertical = 14.dp)
                )
            }

            groups.forEach { group ->
                IosHairlineDivider()
                CategoryGroupRow(
                    group = group,
                    onAddSub = { addingSubForParent = group.parent },
                    onEdit = { editingCategory = it },
                    onDelete = onDeleteCategory
                )
            }
        }
    }

    // 대분류 추가: 지금 고른 장부(공용/개인)에만
    if (addingParent) {
        var name by remember { mutableStateOf("") }
        var emoji by remember { mutableStateOf("🛍️") }
        var type by remember { mutableStateOf(TransactionType.EXPENSE) }

        AlertDialog(
            onDismissRequest = { addingParent = false },
            title = { Text("${selectedSpace.shortLabel()} 대분류 추가") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${selectedSpace.name} 장부에만 추가돼요.",
                        fontSize = 12.sp,
                        color = IosSystemGray
                    )
                    IosSegmentedControl(
                        items = listOf(TransactionType.EXPENSE, TransactionType.INCOME),
                        selectedItem = type,
                        onItemSelected = { type = it },
                        labelProvider = { it.displayName }
                    )
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it },
                        label = { Text("아이콘 모양 (예: 🍔, 🏠, 🎮)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("대분류 이름") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onAddCategory(selectedSpace.id, null, type, name, emoji, "#007AFF")
                            addingParent = false
                        }
                    }
                ) {
                    Text("추가", maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(onClick = { addingParent = false }) {
                    Text("취소", maxLines = 1, softWrap = false)
                }
            }
        )
    }

    // 세부 분류 추가: 대분류와 같은 장부에
    addingSubForParent?.let { parent ->
        var subName by remember { mutableStateOf("") }
        var subEmoji by remember { mutableStateOf("🏷️") }

        AlertDialog(
            onDismissRequest = { addingSubForParent = null },
            title = { Text("'${parent.name}'의 세부 분류 추가") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = subEmoji,
                        onValueChange = { subEmoji = it },
                        label = { Text("아이콘 모양 (예: 🛵, 🥬, ☕)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = subName,
                        onValueChange = { subName = it },
                        label = { Text("세부 분류 이름") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (subName.isNotBlank()) {
                            onAddCategory(parent.spaceId, parent.id, parent.transactionType, subName, subEmoji, parent.colorHex)
                            addingSubForParent = null
                        }
                    }
                ) {
                    Text("추가", maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(onClick = { addingSubForParent = null }) {
                    Text("취소", maxLines = 1, softWrap = false)
                }
            }
        )
    }

    // 이름/아이콘 수정 및 삭제
    editingCategory?.let { cat ->
        var editName by remember(cat) { mutableStateOf(cat.name) }
        var editEmoji by remember(cat) { mutableStateOf(cat.emoji) }

        AlertDialog(
            onDismissRequest = { editingCategory = null },
            title = { Text("분류 수정 / 삭제") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editEmoji,
                        onValueChange = { editEmoji = it },
                        label = { Text("아이콘 모양") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("분류 이름") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    IosTintedButton(
                        text = "이 분류 삭제하기",
                        onClick = {
                            onDeleteCategory(cat.id)
                            editingCategory = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        tintColor = ExpenseCoral,
                        icon = Icons.Default.DeleteOutline
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            onUpdateCategory(cat.copy(name = editName.trim(), emoji = editEmoji.trim()))
                            editingCategory = null
                        }
                    }
                ) {
                    Text("저장", maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCategory = null }) {
                    Text("취소", maxLines = 1, softWrap = false)
                }
            }
        )
    }
}

/** 대분류 한 줄 + 들여쓴 세부 분류 목록 */
@Composable
private fun CategoryGroupRow(
    group: CategoryGroup,
    onAddSub: () -> Unit,
    onEdit: (CategoryEntity) -> Unit,
    onDelete: (Long) -> Unit
) {
    var isExpanded by remember(group.parent.id) { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${group.parent.emoji} ${group.parent.name}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${group.parent.transactionType.displayName} · 세부 ${group.subCategories.size}개",
                    fontSize = 12.sp,
                    color = IosSystemGray,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (group.subCategories.isNotEmpty()) {
                    TextButton(
                        onClick = { isExpanded = !isExpanded },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = if (isExpanded) "접기" else "펼치기",
                            fontSize = 12.sp,
                            color = IosSystemGray,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                IosTintedButton(text = "+ 세부", onClick = onAddSub)
                IconButton(onClick = { onEdit(group.parent) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "${group.parent.name} 수정",
                        tint = IosSystemGray,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(onClick = { onDelete(group.parent.id) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "${group.parent.name} 삭제",
                        tint = ExpenseCoral,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        if (group.subCategories.isEmpty()) {
            Text(
                text = "등록된 세부 분류가 없습니다.",
                fontSize = 12.sp,
                color = IosSystemGray,
                modifier = Modifier.padding(start = 8.dp)
            )
        } else if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                group.subCategories.forEachIndexed { subIdx, sub ->
                    if (subIdx > 0) {
                        IosHairlineDivider()
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "↳ ${sub.emoji} ${sub.name}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onEdit(sub) }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "${sub.name} 수정",
                                    tint = IosSystemGray,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            IconButton(onClick = { onDelete(sub.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "${sub.name} 삭제",
                                    tint = ExpenseCoral,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
