package com.classing.wear.timetable

import android.app.Application
import androidx.work.Configuration
import com.classing.wear.timetable.core.AppContainer
import com.classing.wear.timetable.core.DefaultAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ClassingTimetableApplication : Application(), Configuration.Provider {
    lateinit var appContainer: AppContainer
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultAppContainer(this)
        appScope.launch {
            val preferences = appContainer.settingsRepository.observePreferences().first()
            appContainer.autoSyncController.setEnabled(preferences.autoSync)
            appContainer.reminderWorkController.setEnabled(preferences.remindersEnabled)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
