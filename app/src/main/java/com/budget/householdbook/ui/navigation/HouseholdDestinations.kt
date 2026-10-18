package com.budget.householdbook.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import kotlinx.serialization.Serializable

/*
 * Type-Safe Navigation 라우트 (@Serializable data object)
 */

/** 홈 대시보드 */
@Serializable
data object DashboardRoute

/** 대기내역 검수 */
@Serializable
data object PendingQueueRoute

/** 기간별 장부 */
@Serializable
data object LedgerRoute

/** 집밥 절약 */
@Serializable
data object MealSaverRoute

/** 설정 */
@Serializable
data object SettingsRoute

/**
 * 플로팅 캡슐 탭 바에 표시되는 최상위 화면 (탭 순서 = 선언 순서)
 */
enum class TopLevelDestination(
    val route: Any,
    val tabLabel: String,
    val headerTitle: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD(DashboardRoute, "홈", "홈", Icons.Filled.Home, Icons.Outlined.Home),
    QUEUE(PendingQueueRoute, "대기내역", "대기내역", Icons.Filled.Inbox, Icons.Outlined.Inbox),
    LEDGER(LedgerRoute, "장부", "장부", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    MEAL_SAVER(MealSaverRoute, "집밥절약", "집밥 절약", Icons.Filled.Restaurant, Icons.Outlined.Restaurant),
    SETTINGS(SettingsRoute, "설정", "설정", Icons.Filled.Settings, Icons.Outlined.Settings);

    companion object {
        /** 현재 화면이 속한 최상위 탭 (없으면 홈) */
        fun from(destination: NavDestination?): TopLevelDestination =
            entries.firstOrNull { tab ->
                destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
            } ?: DASHBOARD
    }
}

/**
 * 탭 전환: 홈을 백스택 바닥에 두고, 탭별 화면 상태(스크롤 등)를 저장·복원하며 같은 탭은 중복으로 쌓지 않습니다.
 */
fun NavController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
