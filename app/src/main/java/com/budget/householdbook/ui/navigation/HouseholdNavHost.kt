package com.budget.householdbook.ui.navigation

import android.app.Activity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.budget.householdbook.ui.HouseholdUiState
import com.budget.householdbook.ui.MainViewModel
import com.budget.householdbook.ui.dashboard.DashboardScreen
import com.budget.householdbook.ui.ledger.LedgerScreen
import com.budget.householdbook.ui.mealsaver.MealSaverScreen
import com.budget.householdbook.ui.queue.PendingQueueScreen
import com.budget.householdbook.ui.settings.SettingsAndCategoryScreen

/**
 * 5개 최상위 화면 그래프. 탭 전환은 즉시 바뀌도록(기존 탭 전환과 동일) 화면 전환 애니메이션을 끕니다.
 */
@Composable
fun HouseholdNavHost(
    navController: NavHostController,
    uiState: HouseholdUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = DashboardRoute,
        modifier = modifier,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable<DashboardRoute> {
            DashboardScreen(
                uiState = uiState,
                onNavigateToQueue = { navController.navigateToTopLevel(TopLevelDestination.QUEUE) },
                onNavigateToMealSaver = { navController.navigateToTopLevel(TopLevelDestination.MEAL_SAVER) },
                onChangeMonthOffset = { viewModel.changeMonthOffset(it) },
                onRecordQuickMeal = { viewModel.recordMeal(it) },
                onUpdateBudgetAndDay = { target, day ->
                    viewModel.updateSpaceBudgetAndStartDay(uiState.currentSpace.id, target, day)
                }
            )
        }

        composable<PendingQueueRoute> {
            PendingQueueScreen(
                uiState = uiState,
                onApproveSingle = { id, spaceId, type, amt, merchant, memo, catId, subId ->
                    viewModel.approvePendingItem(id, spaceId, type, amt, merchant, memo, catId, subId)
                },
                onApproveSplit = { id, merchant, splits ->
                    viewModel.approveSplitPendingItem(id, merchant, splits)
                },
                onApplyMatchedCancel = { viewModel.applyMatchedCancel(it) },
                onLoadCancelCandidates = { id, onLoaded -> viewModel.loadManualCancelCandidates(id, onLoaded) },
                onLinkCancelTarget = { id, txId -> viewModel.linkCancelToTransaction(id, txId) },
                onDismissItem = { id, keyword -> viewModel.dismissPendingItem(id, keyword) },
                onApproveAll = { viewModel.approveAllPending() },
                onSimulateNotification = { viewModel.simulateFinancialNotification(it) },
                onRunKbQuickInquiryBeta = { viewModel.runKbQuickInquiryBeta() }
            )
        }

        composable<LedgerRoute> {
            LedgerScreen(
                uiState = uiState,
                onChangeMonthOffset = { viewModel.changeMonthOffset(it) },
                onAddManualTransaction = { spaceId, type, amt, catId, subId, title, memo ->
                    viewModel.addManualTransaction(spaceId, type, amt, catId, subId, title, memo)
                },
                onCancelTransaction = { viewModel.cancelTransaction(it) },
                onDeleteTransaction = { viewModel.deleteTransaction(it) }
            )
        }

        composable<MealSaverRoute> {
            MealSaverScreen(
                uiState = uiState,
                onRecordMeal = { mealType, memo -> viewModel.recordMeal(mealType, memo) },
                onDeleteMealLog = { viewModel.deleteMealLog(it) },
                onUpdateMealPrices = { viewModel.updateMealPriceConfig(it) }
            )
        }

        composable<SettingsRoute> {
            val context = LocalContext.current
            SettingsAndCategoryScreen(
                uiState = uiState,
                onAddCategory = { spaceId, parentId, type, name, emoji, color ->
                    viewModel.addCategory(spaceId, parentId, type, name, emoji, color)
                },
                onUpdateCategory = { viewModel.updateCategory(it) },
                onDeleteCategory = { viewModel.deleteCategory(it) },
                onUpdateBudgetAndStartDay = { target, day ->
                    viewModel.updateSpaceBudgetAndStartDay(uiState.currentSpace.id, target, day)
                },
                onUpdateUserTier = { viewModel.updateUserTier(it) },
                onUpdateThemeMode = { viewModel.updateThemeMode(it) },
                onUpdateSharedMembers = { members, isShared, inviteCode ->
                    viewModel.updateSharedMembers(members, isShared, inviteCode)
                },
                onSignInGoogle = { googleEmail, displayName ->
                    viewModel.signInWithGoogleAccount(
                        activity = context as? Activity,
                        manualEmail = googleEmail,
                        manualName = displayName
                    )
                },
                onSignOutGoogle = { viewModel.signOutGoogleAccount() },
                onInviteByGoogleEmail = { partnerEmail, partnerName, inviteCode ->
                    viewModel.invitePartnerByGoogleEmail(partnerEmail, partnerName, inviteCode)
                },
                onUpdateKbAutoInquiry = { enabled, hours -> viewModel.updateKbAutoInquiry(enabled, hours) },
                onSaveKbCredentials = { acc, birth, pw, realName ->
                    viewModel.saveKbQuickCredentials(acc, birth, pw, realName)
                },
                onRemoveIgnoredKeyword = { viewModel.removeIgnoredKeyword(it) }
            )
        }
    }
}
