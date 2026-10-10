package com.unitracker.app

import android.app.Application
import com.unitracker.app.alarm.NotificationHelper
import com.unitracker.app.sync.SyncScheduler

class UniTrackerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannels(this)
        SyncScheduler.schedulePeriodic(this)
    }
}
