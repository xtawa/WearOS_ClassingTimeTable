package com.classing.wear.timetable.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.classing.wear.timetable.ClassingTimetableApplication
import com.classing.wear.timetable.worker.WearReminderAlarmScheduler
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

class ReminderRebuildReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                val rebuild = OneTimeWorkRequestBuilder<com.classing.wear.timetable.worker.SyncWorker>().build()
                WorkManager.getInstance(context).enqueue(rebuild)
                val app = context.applicationContext as? ClassingTimetableApplication ?: return
                val pref = runCatching {
                    runBlocking { app.appContainer.settingsRepository.observePreferences().first() }
                }.getOrNull() ?: return
                WearReminderAlarmScheduler.refresh(
                    context = context,
                    enabled = pref.remindersEnabled,
                    level = pref.keepAliveLevel,
                )
            }
        }
    }
}
