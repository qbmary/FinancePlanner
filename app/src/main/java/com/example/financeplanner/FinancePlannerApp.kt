package com.example.financeplanner

import android.app.Application
import com.example.financeplanner.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/**
 * Точка инициализации Koin.
 *
 * Koin стартует один раз на процесс (а не в Activity.onCreate), поэтому
 * пересоздание Activity при повороте экрана не приводит к повторному
 * startKoin() и исключению KoinAppAlreadyStartedException.
 */
class FinancePlannerApp : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@FinancePlannerApp)
            modules(appModule)
        }
    }
}
