package com.classing.wear.timetable.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.classing.wear.timetable.worker.SyncWorker

class ReminderRebuildReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                val rebuild = OneTimeWorkRequestBuilder<SyncWorker>().build()
                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    SyncWorker.REMINDER_REBUILD_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    rebuild,
                )
            }
        }
    }
}
