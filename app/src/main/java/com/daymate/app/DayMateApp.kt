package com.daymate.app

import android.app.Application
import com.daymate.app.core.AppContainer

class DayMateApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
