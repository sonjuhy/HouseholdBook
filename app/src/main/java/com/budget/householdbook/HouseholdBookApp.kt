package com.budget.householdbook

import android.app.Application
import com.budget.householdbook.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class HouseholdBookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@HouseholdBookApp)
            modules(appModules)
        }
    }
}
