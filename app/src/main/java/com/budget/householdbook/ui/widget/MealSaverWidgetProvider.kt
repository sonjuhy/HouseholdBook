package com.budget.householdbook.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.budget.householdbook.MainActivity
import com.budget.householdbook.R
import com.budget.householdbook.core.common.CurrencyAndDateFormatter
import com.budget.householdbook.domain.model.MealWidgetSummary
import com.budget.householdbook.domain.model.MealType
import com.budget.householdbook.domain.usecase.GetMealWidgetSummaryUseCase
import com.budget.householdbook.domain.usecase.RecordHomeCookedMealUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 집밥 절약 홈 화면 위젯
 * - 현재 선택된 장부 공간의 이번 정산 주기 집밥 횟수와 절약 금액 표시
 * - 아침/점심/저녁/간식 버튼으로 앱을 열지 않고 바로 +1 기록
 */
class MealSaverWidgetProvider : AppWidgetProvider(), KoinComponent {

    private val getMealWidgetSummary: GetMealWidgetSummaryUseCase by inject()
    private val recordHomeCookedMeal: RecordHomeCookedMealUseCase by inject()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                render(context, appWidgetManager, appWidgetIds, getMealWidgetSummary())
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RECORD_MEAL) {
            super.onReceive(context, intent)
            return
        }
        val mealType = intent.getStringExtra(EXTRA_MEAL_TYPE)
            ?.let { name -> runCatching { MealType.valueOf(name) }.getOrNull() }
            ?: return

        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                // 앱을 열지 않고 현재 선택된 장부 공간에 기록
                recordHomeCookedMeal(mealType)
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, MealSaverWidgetProvider::class.java))
                render(context, manager, ids, getMealWidgetSummary())
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun render(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        summary: MealWidgetSummary
    ) {
        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_meal_saver).apply {
                setTextViewText(R.id.widget_space_name, "🍚 집밥 절약 · ${summary.spaceName}")
                setTextViewText(
                    R.id.widget_saved_amount,
                    "${CurrencyAndDateFormatter.formatWon(summary.totalSavedAmount)} 아꼈어요"
                )
                setTextViewText(R.id.widget_meal_count, "이번 주기 ${summary.totalMealCount}끼 직접 해결 · ${summary.periodLabel}")

                setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
                MEAL_BUTTONS.forEach { (viewId, mealType) ->
                    setOnClickPendingIntent(viewId, recordMealIntent(context, mealType))
                }
            }
            manager.updateAppWidget(widgetId, views)
        }
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun recordMealIntent(context: Context, mealType: MealType): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            // 끼니마다 다른 requestCode를 써야 extra가 덮어써지지 않음
            REQUEST_CODE_BASE + mealType.ordinal,
            Intent(context, MealSaverWidgetProvider::class.java)
                .setAction(ACTION_RECORD_MEAL)
                .putExtra(EXTRA_MEAL_TYPE, mealType.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        private const val ACTION_RECORD_MEAL = "com.budget.householdbook.widget.RECORD_MEAL"
        private const val EXTRA_MEAL_TYPE = "meal_type"
        private const val REQUEST_CODE_BASE = 4100

        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val MEAL_BUTTONS = listOf(
            R.id.widget_btn_breakfast to MealType.BREAKFAST,
            R.id.widget_btn_lunch to MealType.LUNCH,
            R.id.widget_btn_dinner to MealType.DINNER,
            R.id.widget_btn_snack to MealType.SNACK
        )

        /**
         * 앱 안에서 집밥 기록·삭제, 단가·공간 변경 시 홈 화면 위젯을 즉시 갱신
         */
        fun requestRefresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MealSaverWidgetProvider::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, MealSaverWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }
    }
}
