package com.classing.wear.timetable.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.classing.wear.timetable.ClassingTimetableApplication
import com.classing.wear.timetable.worker.ReminderCheckWorker
import com.classing.wear.timetable.worker.WearReminderAlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val data = Data.Builder()
            .putString(ReminderCheckWorker.KEY_DIRECT_LESSON_ID, intent.getStringExtra(EXTRA_LESSON_ID).orEmpty())
            .putString(ReminderCheckWorker.KEY_DIRECT_LESSON_TITLE, intent.getStringExtra(EXTRA_LESSON_TITLE).orEmpty())
            .putString(ReminderCheckWorker.KEY_DIRECT_LESSON_LOCATION, intent.getStringExtra(EXTRA_LESSON_LOCATION))
            .putInt(ReminderCheckWorker.KEY_DIRECT_START_MINUTE, intent.getIntExtra(EXTRA_START_MINUTE, -1))
            .putString(ReminderCheckWorker.KEY_DIRECT_REMINDER_KEY, intent.getStringExtra(EXTRA_REMINDER_KEY).orEmpty())
            .build()

        val request = OneTimeWorkRequestBuilder<ReminderCheckWorker>()
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueue(request)

        val app = context.applicationContext as? ClassingTimetableApplication
        if (app == null) {
            pendingResult.finish()
            return
        }
        receiverScope.launch {
            try {
                val pref = app.appContainer.settingsRepository.observePreferences().firstOrNull()
                if (pref != null) {
                    WearReminderAlarmScheduler.refresh(
                        context = context,
                        enabled = pref.remindersEnabled,
                        level = pref.keepAliveLevel,
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        const val EXTRA_LESSON_ID = "extra_lesson_id"
        const val EXTRA_LESSON_TITLE = "extra_lesson_title"
        const val EXTRA_LESSON_LOCATION = "extra_lesson_location"
        const val EXTRA_START_MINUTE = "extra_start_minute"
        const val EXTRA_REMINDER_KEY = "extra_reminder_key"
    }
}
