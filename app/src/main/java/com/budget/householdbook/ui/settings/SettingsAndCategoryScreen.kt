package com.budget.householdbook.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.budget.householdbook.core.common.BillingCycleCalculator
import com.budget.householdbook.core.common.CurrencyAndDateFormatter
import com.budget.householdbook.core.designsystem.BillingStartDayField
import com.budget.householdbook.core.designsystem.IosFilledButton
import com.budget.householdbook.core.designsystem.IosHairlineDivider
import com.budget.householdbook.core.designsystem.IosInsetGroupedCard
import com.budget.householdbook.core.designsystem.IosPillChip
import com.budget.householdbook.core.designsystem.IosSectionHeader
import com.budget.householdbook.core.designsystem.IosSegmentedControl
import com.budget.householdbook.core.designsystem.IosTintedButton
import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.data.sync.RemoteSyncManager
import com.budget.householdbook.data.work.BackgroundWorkScheduler
import com.budget.householdbook.domain.model.ThemeMode
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.domain.model.UserTier
import com.budget.householdbook.ui.HouseholdUiState
import com.budget.householdbook.ui.theme.ExpenseCoral
import com.budget.householdbook.ui.theme.IncomeBlue
import com.budget.householdbook.ui.theme.IosSystemGray

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsAndCategoryScreen(
    uiState: HouseholdUiState,
    onAddCategory: (
        spaceId: String,
        parentId: Long?,
        transactionType: TransactionType,
        name: String,
        emoji: String,
        colorHex: String
    ) -> Unit,
    onUpdateCategory: (CategoryEntity) -> Unit,
    onDeleteCategory: (Long) -> Unit,
    onUpdateBudgetAndStartDay: (Long, Int) -> Unit,
    onUpdateUserTier: (UserTier) -> Unit,
    onUpdateThemeMode: (ThemeMode) -> Unit,
    onUpdateSharedMembers: (String, Boolean, String?) -> Unit,
    onSignInGoogle: (manualEmail: String?, manualName: String?) -> Unit,
    onSignOutGoogle: () -> Unit,
    onInviteByGoogleEmail: (partnerEmail: String, partnerName: String, inviteCode: String) -> Unit,
    onSaveKbCredentials: (String, String, String, String) -> Unit,
    onUpdateKbAutoInquiry: (enabled: Boolean, intervalHours: Int) -> Unit,
    onRemoveIgnoredKeyword: (Long) -> Unit
) {
    val context = LocalContext.current
    // 초대번호·멤버는 현재 선택된 공간과 무관하게 항상 '공통 생활비' 공간 기준
    val sharedSpace = uiState.spaces.firstOrNull { it.id == AppDatabase.SPACE_ID_SHARED }

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasSmsPermission = granted }

    var addingParentForSpace by remember { mutableStateOf(false) }
    var addingSubForParent by remember { mutableStateOf<CategoryEntity?>(null) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }

    var showGoogleLoginDialog by remember { mutableStateOf(false) }
    var showKbSetupDialog by remember { mutableStateOf(false) }
    var showMemberShareDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(2.dp)) }

        // 0-A. 구글 계정 로그인 및 초대 연결 카드
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IosSectionHeader(title = "내 구글 계정 및 초대 연결")

                IosInsetGroupedCard(contentPadding = PaddingValues(16.dp)) {
                    if (uiState.settings.isLoggedIn && uiState.settings.userEmail.isNotBlank()) {
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
                                    text = "🟢 ${uiState.settings.userRealName} (${uiState.settings.userEmail})",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "현재 플랜: ${uiState.settings.userTier.displayName}\n${uiState.syncStatusMessage}",
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = IosSystemGray
                                )
                            }
                            IosTintedButton(
                                text = "로그아웃",
                                onClick = onSignOutGoogle,
                                tintColor = ExpenseCoral
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        IosHairlineDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IosFilledButton(
                                text = "구글 계정으로 파트너 초대",
                                onClick = { showMemberShareDialog = true },
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.PersonAdd
                            )
                            IosTintedButton(
                                text = "초대장 공유",
                                onClick = {
                                    val code = sharedSpace?.inviteCode
                                    if (code.isNullOrBlank()) {
                                        // 아직 초대번호가 없으면 초대 다이얼로그에서 먼저 발급
                                        showMemberShareDialog = true
                                        return@IosTintedButton
                                    }
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "[우리집 가계부 초대]\n'${uiState.settings.userRealName}'님이 함께 쓰는 생활비 장부에 초대했습니다!\n초대받은 구글 계정으로 앱에 로그인하면 자동으로 연결됩니다. (초대번호: $code)"
                                        )
                                    }
                                    runCatching {
                                        context.startActivity(Intent.createChooser(shareIntent, "초대장 보내기"))
                                    }
                                },
                                icon = Icons.Default.Share
                            )
                        }
                    } else {
                        Text(
                            text = "구글 계정으로 로그인하고 시작하기",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "처음 로그인하면 내 계정이 기본(혼자 쓰기)인지 프리미엄(함께 쓰기)인지 확인하여 자동으로 적용합니다.\n가족·짝꿍이 내 구글 이메일로 보낸 초대가 있다면 즉시 함께 쓰는 장부로 연결됩니다.",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = IosSystemGray
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        IosFilledButton(
                            text = "구글 계정으로 로그인",
                            onClick = { showGoogleLoginDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Default.AccountCircle
                        )
                    }
                }
            }
        }

        // 0-B. 화면 스타일 (밝은 화면 / 어두운 화면)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IosSectionHeader(title = "화면 스타일")

                IosInsetGroupedCard(contentPadding = PaddingValues(16.dp)) {
                    IosSegmentedControl(
                        items = ThemeMode.entries,
                        selectedItem = uiState.settings.themeMode,
                        onItemSelected = { onUpdateThemeMode(it) },
                        labelProvider = { "${it.emoji} ${it.displayName}" }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "밤에도 눈이 편안한 어두운 화면과 깔끔한 밝은 화면 중 원하는 스타일을 선택하세요.\n상단 버튼으로도 언제든 바꿀 수 있습니다.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = IosSystemGray
                    )
                }
            }
        }

        // 1. 방안 3 적용: 상위 카테고리 + 세부 분류 관리 섹션
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IosSectionHeader(
                    title = "${uiState.currentSpace.name} 카테고리 및 세부 분류",
                    actionLabel = "+ 대분류 추가",
                    onActionClick = { addingParentForSpace = true }
                )

                IosInsetGroupedCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    uiState.currentSpaceCategoryGroups.forEachIndexed { index, group ->
                        if (index > 0) {
                            IosHairlineDivider()
                        }
                        var isExpanded by remember(group.parent.id) { mutableStateOf(true) }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 상위 카테고리 메인 행
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
                                    IosTintedButton(
                                        text = "+ 세부",
                                        onClick = { addingSubForParent = group.parent }
                                    )
                                    IconButton(
                                        onClick = { editingCategory = group.parent },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "수정",
                                            tint = IosSystemGray,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteCategory(group.parent.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "삭제",
                                            tint = ExpenseCoral,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }

                            // 방안 3: 들여쓰기된 세로 리스트로 세부 카테고리 표시
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
                                                IconButton(
                                                    onClick = { editingCategory = sub },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "세부 수정",
                                                        tint = IosSystemGray,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { onDeleteCategory(sub.id) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteOutline,
                                                        contentDescription = "세부 삭제",
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
                }
            }
        }

        // 2. 이용 플랜 및 생활비 함께 쓰기
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IosSectionHeader(title = "이용 플랜 및 생활비 함께 쓰기")

                IosInsetGroupedCard(contentPadding = PaddingValues(16.dp)) {
                    IosSegmentedControl(
                        items = UserTier.entries,
                        selectedItem = uiState.settings.userTier,
                        onItemSelected = { onUpdateUserTier(it) },
                        labelProvider = { it.displayName }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    IosHairlineDivider()
                    Spacer(modifier = Modifier.height(12.dp))

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
                                text = "함께 쓰는 멤버: ${uiState.currentSpace.memberNamesCsv}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (uiState.settings.userTier == UserTier.FREE) {
                                    "기본 플랜: 내 휴대폰에만 안전하게 보관되며 다른 사람과 공유되지 않습니다."
                                } else {
                                    "프리미엄: 가족·연인과 실시간으로 함께 쓰며 인터넷이 없어도 기록됩니다.\n초대번호: ${sharedSpace?.inviteCode ?: "아직 없음"}"
                                },
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = IosSystemGray
                            )
                        }
                        IosTintedButton(
                            text = "멤버 관리",
                            onClick = { showMemberShareDialog = true },
                            icon = if (uiState.settings.userTier == UserTier.FREE) Icons.Default.Lock else null
                        )
                    }
                }
            }
        }

        // 3. 결제 알림 및 KB국민은행 빠른조회 설정
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IosSectionHeader(title = "결제 알림 및 은행 내역 자동 불러오기")

                IosInsetGroupedCard(contentPadding = PaddingValues(16.dp)) {
                    Text(
                        text = "은행·카드사 앱 알림과 문자를 자동으로 읽어옵니다.\n놓친 내역은 KB국민은행 빠른조회로 한 번에 불러올 수 있습니다.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = IosSystemGray
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IosTintedButton(
                            text = if (uiState.kbCredentials.isConfigured) {
                                "KB 빠른조회 계좌 수정"
                            } else {
                                "KB 빠른조회 계좌 등록"
                            },
                            onClick = { showKbSetupDialog = true },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Security
                        )

                        IosTintedButton(
                            text = "알림 읽기 권한 설정",
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 문자(SMS) 수집은 사용자가 직접 켤 때만 권한을 요청 (거부해도 앱 알림 수집은 정상 동작)
                    Spacer(modifier = Modifier.height(8.dp))
                    IosTintedButton(
                        text = if (hasSmsPermission) "문자 자동 수집 켜짐" else "문자 자동 수집 켜기",
                        onClick = {
                            if (!hasSmsPermission) {
                                smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // [Beta] KB 빠른조회 백그라운드 자동 불러오기 (계좌 등록 후 사용 가능)
                    Spacer(modifier = Modifier.height(12.dp))
                    IosHairlineDivider()
                    Spacer(modifier = Modifier.height(10.dp))
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
                                text = "KB 계좌 내역 자동 불러오기 [Beta]",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (uiState.kbCredentials.isConfigured) {
                                    "켜두면 인터넷이 연결되어 있을 때 정해진 간격으로\n새 내역을 대기내역에 넣어둡니다."
                                } else {
                                    "먼저 'KB 빠른조회 계좌 등록'을 해주세요."
                                },
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = IosSystemGray
                            )
                        }
                        Switch(
                            checked = uiState.settings.kbAutoInquiryEnabled,
                            onCheckedChange = { enabled ->
                                onUpdateKbAutoInquiry(enabled, uiState.settings.kbAutoInquiryIntervalHours)
                            },
                            enabled = uiState.kbCredentials.isConfigured
                        )
                    }
                    if (uiState.settings.kbAutoInquiryEnabled && uiState.kbCredentials.isConfigured) {
                        Spacer(modifier = Modifier.height(8.dp))
                        IosSegmentedControl(
                            items = BackgroundWorkScheduler.KB_INTERVAL_OPTIONS,
                            selectedItem = uiState.settings.kbAutoInquiryIntervalHours,
                            onItemSelected = { hours -> onUpdateKbAutoInquiry(true, hours) },
                            labelProvider = { hours -> "${hours}시간마다" },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (uiState.ignoredKeywords.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        IosHairlineDivider()
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "자동으로 건너뛸 단어",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uiState.ignoredKeywords.forEach { item ->
                                IosPillChip(
                                    label = "${item.keyword} ✕",
                                    selected = true,
                                    onClick = { onRemoveIgnoredKeyword(item.id) },
                                    activeColor = ExpenseCoral
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. 공간별 전체 목표 예산 및 월 정산 기준일 설정
        item {
            var targetBudgetText by remember(uiState.currentSpace) {
                mutableStateOf(uiState.currentSpace.targetBudgetAmount.toString())
            }
            var startDay by remember(uiState.currentSpace) {
                mutableIntStateOf(uiState.currentSpace.billingStartDay)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IosSectionHeader(title = "${uiState.currentSpace.name} 전체 목표 금액 & 월 기준일")

                IosInsetGroupedCard(contentPadding = PaddingValues(16.dp)) {
                    Text(
                        text = "현재 목표: ${CurrencyAndDateFormatter.formatWon(uiState.currentSpace.targetBudgetAmount)} · 시작일: 매월 ${BillingCycleCalculator.startDayLabel(uiState.currentSpace.billingStartDay)}",
                        fontSize = 12.sp,
                        color = IosSystemGray
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = targetBudgetText,
                        onValueChange = { targetBudgetText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("전체 목표 금액(원)", maxLines = 1) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    BillingStartDayField(
                        startDay = startDay,
                        onStartDayChange = { startDay = it }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    IosFilledButton(
                        text = "목표 및 시작일 저장",
                        onClick = {
                            val target = targetBudgetText.toLongOrNull() ?: uiState.currentSpace.targetBudgetAmount
                            onUpdateBudgetAndStartDay(target, startDay)
                        },
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(104.dp)) }
    }

    // 상위 카테고리 추가 다이얼로그
    if (addingParentForSpace) {
        var name by remember { mutableStateOf("") }
        var emoji by remember { mutableStateOf("🛍️") }
        var type by remember { mutableStateOf(TransactionType.EXPENSE) }

        AlertDialog(
            onDismissRequest = { addingParentForSpace = false },
            title = { Text("${uiState.currentSpace.name} 대분류 추가") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            onAddCategory(uiState.currentSpace.id, null, type, name, emoji, "#007AFF")
                            addingParentForSpace = false
                        }
                    }
                ) {
                    Text("추가", maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(onClick = { addingParentForSpace = false }) {
                    Text("취소", maxLines = 1, softWrap = false)
                }
            }
        )
    }

    // 세부 카테고리 추가 다이얼로그
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
                            onAddCategory(
                                parent.spaceId,
                                parent.id,
                                parent.transactionType,
                                subName,
                                subEmoji,
                                parent.colorHex
                            )
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

    // 카테고리 이름/아이콘 수정 및 삭제 다이얼로그
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

    // KB국민은행 빠른조회 계좌 설정 다이얼로그
    if (showKbSetupDialog) {
        val creds = uiState.kbCredentials
        var accText by remember(creds) { mutableStateOf(creds.accountNumber.ifBlank { "110-123-456789" }) }
        var birthText by remember(creds) { mutableStateOf(creds.birthOrBizNumber.ifBlank { "950101" }) }
        var pwText by remember(creds) { mutableStateOf(creds.quickPassword4Digit.ifBlank { "1234" }) }
        var realNameText by remember(uiState.settings) { mutableStateOf(uiState.settings.userRealName) }

        AlertDialog(
            onDismissRequest = { showKbSetupDialog = false },
            title = { Text("KB국민은행 빠른조회 계좌 설정") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "입력하신 계좌 정보는 다른 곳으로 전송되지 않고 내 휴대폰 안에만 안전하게 잠금 보관됩니다.\n최근 입출금 내역을 불러올 때만 사용됩니다.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = IosSystemGray
                    )
                    OutlinedTextField(
                        value = accText,
                        onValueChange = { accText = it },
                        label = { Text("KB국민은행 계좌번호") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = birthText,
                        onValueChange = { birthText = it.filter { ch -> ch.isDigit() }.take(10) },
                        label = { Text("생년월일(6자리) 또는 사업자번호") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pwText,
                        onValueChange = { pwText = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("빠른조회 비밀번호 (4자리)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = realNameText,
                        onValueChange = { realNameText = it },
                        label = { Text("본인 이름 (내 계좌끼리 이체한 내역 자동 구분용)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveKbCredentials(accText, birthText, pwText, realNameText)
                        showKbSetupDialog = false
                    }
                ) {
                    Text("안전하게 저장", maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(onClick = { showKbSetupDialog = false }) {
                    Text("취소", maxLines = 1, softWrap = false)
                }
            }
        )
    }

    // 구글 계정으로 로그인 다이얼로그 (로그인 시 자동으로 기본/프리미엄 플랜 및 초대 여부 확인)
    if (showGoogleLoginDialog) {
        var googleEmailText by remember(uiState.settings.userEmail) {
            mutableStateOf(uiState.settings.userEmail)
        }
        var displayNameText by remember(uiState.settings.userRealName) {
            mutableStateOf(
                uiState.settings.userRealName.ifBlank {
                    uiState.currentSpace.memberNamesCsv.split(",").firstOrNull()?.trim().orEmpty()
                }
            )
        }

        AlertDialog(
            onDismissRequest = { showGoogleLoginDialog = false },
            title = { Text("구글 계정으로 로그인") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "구글 계정으로 로그인하면 내 계정 상태를 먼저 확인하여\n혼자 쓰는 기본 플랜인지, 함께 쓰는 프리미엄 플랜인지 자동으로 맞춰줍니다.\n장부 보호를 위해 항상 구글 인증 창에서 본인 확인을 거칩니다.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = IosSystemGray
                    )
                    OutlinedTextField(
                        value = googleEmailText,
                        onValueChange = { googleEmailText = it },
                        label = { Text("구글 이메일 주소 (예: myname@gmail.com)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = displayNameText,
                        onValueChange = { displayNameText = it },
                        label = { Text("장부에 표시할 내 이름 (예: 민수)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    IosTintedButton(
                        text = "구글 공식 인증 창으로 바로 로그인",
                        onClick = {
                            onSignInGoogle("", displayNameText.trim())
                            showGoogleLoginDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSignInGoogle(googleEmailText.trim(), displayNameText.trim())
                        showGoogleLoginDialog = false
                    }
                ) {
                    Text("로그인 및 플랜 확인", maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoogleLoginDialog = false }) {
                    Text("취소", maxLines = 1, softWrap = false)
                }
            }
        )
    }

    // 공통 생활비 멤버 관리 및 구글 계정 초대 다이얼로그
    if (showMemberShareDialog) {
        var membersText by remember(sharedSpace) {
            mutableStateOf(sharedSpace?.memberNamesCsv ?: "나")
        }
        val inviteCodeText by remember(sharedSpace) {
            mutableStateOf(
                sharedSpace?.inviteCode?.takeIf { RemoteSyncManager.isValidInviteCode(it) }
                    ?: RemoteSyncManager.generateInviteCode()
            )
        }
        var partnerEmailText by remember { mutableStateOf("") }
        var partnerNameText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showMemberShareDialog = false },
            title = { Text("함께 쓰는 멤버 및 구글 계정 초대") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "상대방의 구글 이메일로 초대장을 보내면,\n상대방이 로그인할 때 자동으로 프리미엄 함께 쓰기 장부로 연결됩니다.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = IosSystemGray
                    )
                    OutlinedTextField(
                        value = partnerEmailText,
                        onValueChange = { partnerEmailText = it },
                        label = { Text("초대할 분의 구글 이메일 (예: partner@gmail.com)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = partnerNameText,
                        onValueChange = { partnerNameText = it },
                        label = { Text("초대할 분의 호칭/이름 (예: 짝꿍)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    IosFilledButton(
                        text = "구글 계정으로 초대 보내기",
                        onClick = {
                            onInviteByGoogleEmail(
                                partnerEmailText.trim(),
                                partnerNameText.trim(),
                                inviteCodeText.trim()
                            )
                            showMemberShareDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    IosHairlineDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "멤버 이름 설정 (초대번호는 자동 발급)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = membersText,
                        onValueChange = { membersText = it },
                        label = { Text("함께 쓰는 멤버 이름 (예: 나, 짝꿍)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inviteCodeText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("공유 초대번호 (자동 발급)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (uiState.settings.userTier == UserTier.FREE) {
                            onUpdateUserTier(UserTier.PRO)
                        }
                        onUpdateSharedMembers(membersText, true, inviteCodeText)
                        showMemberShareDialog = false
                    }
                ) {
                    Text("멤버·초대번호 저장", maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMemberShareDialog = false }) {
                    Text("닫기", maxLines = 1, softWrap = false)
                }
            }
        )
    }
}

