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
import androidx.compose.material3.SnackbarHostState
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
import com.budget.householdbook.ui.theme.HouseholdBookTheme
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = koinViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
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

            HouseholdBookTheme(
                spaceType = uiState.currentSpace.type,
                darkTheme = isDarkTheme
            ) {
                HouseholdBookRootScreen(
                    uiState = uiState,
                    isDarkTheme = isDarkTheme,
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState
                )
            }
        }
    }
}

@Composable
private fun HouseholdBookRootScreen(
    uiState: HouseholdUiState,
    isDarkTheme: Boolean,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = TopLevelDestination.from(backStackEntry?.destination)

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
