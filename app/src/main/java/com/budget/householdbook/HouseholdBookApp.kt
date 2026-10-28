package com.budget.householdbook

import android.app.Application
import com.budget.householdbook.core.notification.BudgetAlertMonitor
import com.budget.householdbook.core.notification.PendingAlertCleaner
import com.budget.householdbook.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class HouseholdBookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            androidContext(this@HouseholdBookApp)
            modules(appModules)
        }.koin
        // 앱 프로세스가 살아 있는 동안: 예산 80%·100% 알림 감시, 처리된 대기 항목의 알림 정리
        koin.get<BudgetAlertMonitor>().start()
        koin.get<PendingAlertCleaner>().start()
    }
}
