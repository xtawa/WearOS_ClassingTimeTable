package com.classing.wear.timetable

import android.app.Application
import androidx.work.Configuration
import com.classing.wear.timetable.core.AppContainer
import com.classing.wear.timetable.core.DefaultAppContainer
import com.classing.wear.timetable.worker.SyncWorker

class ClassingTimetableApplication : Application(), Configuration.Provider {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultAppContainer(this)
        SyncWorker.enqueuePeriodic(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
