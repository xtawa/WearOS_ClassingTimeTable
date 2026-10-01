package com.classing.wear.timetable

import android.app.Application
import android.util.Log
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
            // A failure here (missing GMS, blocked background work on OEM builds, unreadable
            // preferences) must never take the whole process down before the UI is shown.
            runCatching {
                val preferences = appContainer.settingsRepository.observePreferences().first()
                appContainer.autoSyncController.setEnabled(preferences.autoSync)
                appContainer.reminderWorkController.setPolicy(
                    enabled = preferences.remindersEnabled,
                    level = preferences.keepAliveLevel,
                )
            }.onFailure { error ->
                Log.e(TAG, "Startup bootstrap failed; continuing with defaults", error)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    private companion object {
        const val TAG = "ClassingTimetable"
    }
}
