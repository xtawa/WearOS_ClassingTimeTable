package com.classing.wear.timetable

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.classing.wear.timetable.core.AppContainer
import com.classing.wear.timetable.core.DefaultAppContainer
import com.classing.wear.timetable.domain.model.KeepAliveLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ClassingTimetableApplication : Application(), Configuration.Provider {
    lateinit var appContainer: AppContainer
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultAppContainer(this)
        appScope.launch {
            // Preferences can change from the settings UI, from a phone-pushed settings snapshot or
            // from the official cloud. Re-apply the background policy whenever the relevant fields
            // change so a remote "reminders off" / keep-alive change takes effect without a relaunch.
            appContainer.settingsRepository.observePreferences()
                .map { BackgroundPolicy(it.autoSync, it.remindersEnabled, it.keepAliveLevel) }
                .distinctUntilChanged()
                .collect { policy ->
                    try {
                        appContainer.autoSyncController.setEnabled(policy.autoSync)
                        appContainer.reminderWorkController.setPolicy(
                            enabled = policy.remindersEnabled,
                            level = policy.keepAliveLevel,
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Log.w(TAG, "Failed to apply background policy", error)
                    }
                }
        }
    }

    private data class BackgroundPolicy(
        val autoSync: Boolean,
        val remindersEnabled: Boolean,
        val keepAliveLevel: KeepAliveLevel,
    )

    private companion object {
        const val TAG = "ClassingApp"
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
