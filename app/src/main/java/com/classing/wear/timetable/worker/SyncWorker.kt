package com.classing.wear.timetable.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.classing.shared.sync.CloudSyncContracts
import com.classing.wear.timetable.ClassingTimetableApplication
import com.classing.wear.timetable.sync.WearSyncModeStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ClassingTimetableApplication
        val independentMode = WearSyncModeStore.isIndependentModeEnabled(applicationContext)
        val syncResult = if (independentMode) {
            app.appContainer.wearOfficialCloudSyncCoordinator.sync(CloudSyncContracts.TRIGGER_FOREGROUND_TICK)
                .fold(
                    onSuccess = { Result.success() },
                    onFailure = { Result.retry() },
                )
        } else {
            val result = app.appContainer.mobileSyncRequester.requestSyncFromPhone()
            if (result.getOrDefault(0) > 0 || result.isSuccess) {
                Result.success()
            } else {
                val message = result.exceptionOrNull()?.message.orEmpty()
                if (message.contains("No connected phone", ignoreCase = true)) {
                    Result.success()
                } else {
                    Result.retry()
                }
            }
        }

        val reminderResult = refreshRemindersFromSettings(app)
        return when {
            syncResult == Result.retry() -> Result.retry()
            reminderResult.isFailure -> Result.retry()
            else -> Result.success()
        }
    }

    private suspend fun refreshRemindersFromSettings(app: ClassingTimetableApplication): kotlin.Result<Unit> {
        return runCatching {
            val pref = app.appContainer.settingsRepository.observePreferences().first()
            WearReminderAlarmScheduler.refresh(
                context = applicationContext,
                enabled = pref.remindersEnabled,
                level = pref.keepAliveLevel,
            )
        }
    }

    companion object {
        internal const val UNIQUE_WORK_NAME = "schedule_sync_worker"
        internal const val REMINDER_REBUILD_WORK_NAME = "reminder_rebuild_worker"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
