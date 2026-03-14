package com.classing.wear.timetable.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.classing.wear.timetable.ClassingTimetableApplication
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ClassingTimetableApplication
        val result = app.appContainer.mobileSyncRequester.requestSyncFromPhone()
        if (result.isSuccess) return Result.success()

        val message = result.exceptionOrNull()?.message.orEmpty()
        return if (message.contains("No connected phone", ignoreCase = true)) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "schedule_sync_worker"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
