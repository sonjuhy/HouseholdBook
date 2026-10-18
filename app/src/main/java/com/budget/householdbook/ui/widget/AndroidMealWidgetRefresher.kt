package com.budget.householdbook.ui.widget

import android.content.Context
import com.budget.householdbook.domain.repository.MealWidgetRefresher

/** 집밥 절약 홈 화면 위젯([MealSaverWidgetProvider]) 갱신 브로드캐스트 */
class AndroidMealWidgetRefresher(private val context: Context) : MealWidgetRefresher {
    override fun refresh() = MealSaverWidgetProvider.requestRefresh(context)
}
