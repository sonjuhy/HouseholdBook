package com.budget.householdbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.budget.householdbook.core.designsystem.IosHairlineDivider
import com.budget.householdbook.core.designsystem.IosSegmentedControl
import com.budget.householdbook.domain.model.SpaceType
import com.budget.householdbook.domain.model.ThemeMode
import com.budget.householdbook.ui.HouseholdUiState
import com.budget.householdbook.ui.MainViewModel
import com.budget.householdbook.ui.navigation.HouseholdNavHost
import com.budget.householdbook.ui.navigation.IosFloatingCapsuleTabBar
import com.budget.householdbook.ui.navigation.TopLevelDestination
import com.budget.householdbook.ui.navigation.navigateToTopLevel
import com.budget.householdbook.ui.onboarding.OnboardingScreen
import com.budget.householdbook.ui.theme.HouseholdBookTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.android.ext.android.inject
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.budget.householdbook.data.ingestion.notification.FinancialNotificationListenerService
import com.budget.householdbook.domain.usecase.CheckCollectionStatusUseCase

class MainActivity : ComponentActivity() {

    private val checkCollectionStatus: CheckCollectionStatusUseCase by inject()

    /** 알림을 눌러 들어왔을 때 열 탭 (처리하면 비움) */
    private val requestedTab = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) requestedTab.value = intent?.getStringExtra(EXTRA_OPEN_TAB)
        setContent {
            val viewModel: MainViewModel = koinViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
            val undoSnackbar by viewModel.undoSnackbar.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }

            val systemDark = isSystemInDarkTheme()
            val isDarkTheme = when (uiState.settings.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !isDarkTheme
                    controller.isAppearanceLightNavigationBars = !isDarkTheme
                }
            }

            LaunchedEffect(snackbarMessage) {
                val msg = snackbarMessage ?: return@LaunchedEffect
                snackbarHostState.showSnackbar(msg)
                viewModel.clearSnackbar()
            }

            // 취소처럼 되돌릴 수 있는 동작: 스낵바의 '되돌리기'를 누르면 원래대로
            LaunchedEffect(undoSnackbar) {
                val undo = undoSnackbar ?: return@LaunchedEffect
                val result = snackbarHostState.showSnackbar(
                    message = undo.message,
                    actionLabel = undo.actionLabel,
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) undo.onUndo()
                // 표시가 끝난 뒤에 비움 (먼저 비우면 이 효과가 바로 취소되어 스낵바가 사라짐)
                viewModel.clearUndoSnackbar()
            }

            HouseholdBookTheme(
                spaceType = uiState.currentSpace.type,
                darkTheme = isDarkTheme
            ) {
                when {
                    // 저장된 설정을 읽기 전에는 빈 배경만 (다시 실행한 사용자에게 튜토리얼이 깜빡이지 않도록)
                    !uiState.isLoaded -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                    // 처음 실행: 튜토리얼
                    !uiState.settings.hasSeenOnboarding -> OnboardingScreen(
                        onFinish = { withSampleData -> viewModel.completeOnboarding(withSampleData) }
                    )
                    else -> HouseholdBookRootScreen(
                        uiState = uiState,
                        isDarkTheme = isDarkTheme,
                        viewModel = viewModel,
                        snackbarHostState = snackbarHostState,
                        requestedTab = requestedTab.value,
                        onRequestedTabHandled = { requestedTab.value = null }
                    )
                }
            }
        }
    }

    /** 앱이 열려 있을 때 알림을 누른 경우 (singleTop) */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_OPEN_TAB)?.let { requestedTab.value = it }
    }

    override fun onResume() {
        super.onResume()
        // 설정에서 결제 알림 읽기를 다시 켜고 돌아온 경우 '자동 기록 멈춤' 알림을 지우고 사용 이력을 기록
        val accessEnabled = FinancialNotificationListenerService.isNotificationAccessGranted(this)
        lifecycleScope.launch { checkCollectionStatus(notificationAccessEnabled = accessEnabled, notify = false) }
    }

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
        const val TAB_HOME = "home"
        const val TAB_QUEUE = "queue"
        const val TAB_LEDGER = "ledger"
        const val TAB_SETTINGS = "settings"
    }
}

@Composable
private fun HouseholdBookRootScreen(
    uiState: HouseholdUiState,
    isDarkTheme: Boolean,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    requestedTab: String?,
    onRequestedTabHandled: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = TopLevelDestination.from(backStackEntry?.destination)

    // 로그인해 둔 상태에서 받은 함께 쓰기 초대는 앱을 열거나 돌아올 때 자동으로 수락 (다시 로그인하지 않아도 됨)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.checkPendingInvitation() }

    // 알림을 눌러 들어온 경우 해당 탭으로 (새 결제 → 대기내역, 예산 → 홈 등)
    LaunchedEffect(requestedTab, backStackEntry != null) {
        val tab = requestedTab ?: return@LaunchedEffect
        if (backStackEntry == null) return@LaunchedEffect
        val destination = when (tab) {
            MainActivity.TAB_QUEUE -> TopLevelDestination.QUEUE
            MainActivity.TAB_LEDGER -> TopLevelDestination.LEDGER
            MainActivity.TAB_SETTINGS -> TopLevelDestination.SETTINGS
            else -> TopLevelDestination.DASHBOARD
        }
        navController.navigateToTopLevel(destination)
        onRequestedTabHandled()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            IosNavigationHeader(
                uiState = uiState,
                isDarkTheme = isDarkTheme,
                currentTabTitle = currentDestination.headerTitle,
                // 대기내역은 모든 공간의 항목을 함께 보여주고 저장 공간은 카드에서 고르므로 상단 전환을 숨김
                showSpaceSwitcher = currentDestination != TopLevelDestination.QUEUE,
                onSelectSpace = { viewModel.selectSpace(it) },
                onToggleDarkMode = { viewModel.toggleDarkMode(isDarkTheme) }
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 84.dp)
            )
        }
    ) { innerPadding ->
        // 플로팅 캡슐 바 뒤로 스크롤 컨텐츠가 잘리지 않고 자연스럽게 비치도록
        // 상단(topBar) 패딩만 적용하고, 하단 플로팅 캡슐 바는 Box 하단 오버레이로 띄웁니다.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            HouseholdNavHost(
                navController = navController,
                uiState = uiState,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )

            // 화면 하단 위에 투명하게 떠 있는 플로팅 캡슐 독 (뒤쪽 스크롤 영역이 잘리지 않고 그대로 투과됨)
            IosFloatingCapsuleTabBar(
                selectedDestination = currentDestination,
                pendingCount = uiState.pendingQueue.size,
                onSelectDestination = { navController.navigateToTopLevel(it) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/**
 * Apple iOS UINavigationBar + UISegmentedControl 스타일 상단 바
 */
@Composable
private fun IosNavigationHeader(
    uiState: HouseholdUiState,
    isDarkTheme: Boolean,
    currentTabTitle: String,
    showSpaceSwitcher: Boolean,
    onSelectSpace: (String) -> Unit,
    onToggleDarkMode: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentTabTitle,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 원터치 라이트/다크 모드 빠른 전환 캡슐 버튼
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onToggleDarkMode() }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "화면 밝기 전환",
                            tint = if (isDarkTheme) Color(0xFFFFD60A) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isDarkTheme) "어둡게" else "밝게",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = uiState.settings.userTier.displayName,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Apple iOS UISegmentedControl 슬라이딩 공간 피커
                if (showSpaceSwitcher && uiState.spaces.isNotEmpty()) {
                    IosSegmentedControl(
                        items = uiState.spaces,
                        selectedItem = uiState.currentSpace,
                        onItemSelected = { onSelectSpace(it.id) },
                        labelProvider = { space ->
                            val emoji = if (space.type == SpaceType.SHARED) "👥" else "👤"
                            "$emoji ${space.name}"
                        }
                    )
                }
            }
            IosHairlineDivider()
        }
    }
}
