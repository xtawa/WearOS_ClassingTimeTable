package com.classing.wear.timetable.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class ReminderRebuildReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                val rebuild = OneTimeWorkRequestBuilder<com.classing.wear.timetable.worker.SyncWorker>().build()
                WorkManager.getInstance(context).enqueue(rebuild)
            }
        }
    }
}
