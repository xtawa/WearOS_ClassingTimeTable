package com.xtawa.classingtime.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class LessonReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val data = Data.Builder()
            .putString(LessonReminderWorker.KEY_DIRECT_LESSON_ID, intent.getStringExtra(EXTRA_LESSON_ID).orEmpty())
            .putString(LessonReminderWorker.KEY_DIRECT_LESSON_TITLE, intent.getStringExtra(EXTRA_LESSON_TITLE).orEmpty())
            .putString(LessonReminderWorker.KEY_DIRECT_LESSON_LOCATION, intent.getStringExtra(EXTRA_LESSON_LOCATION))
            .putInt(LessonReminderWorker.KEY_DIRECT_START_MINUTE, intent.getIntExtra(EXTRA_START_MINUTE, -1))
            .putString(LessonReminderWorker.KEY_DIRECT_REMINDER_KEY, intent.getStringExtra(EXTRA_REMINDER_KEY).orEmpty())
            .build()

        val request = OneTimeWorkRequestBuilder<LessonReminderWorker>()
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    companion object {
        const val EXTRA_LESSON_ID = "extra_lesson_id"
        const val EXTRA_LESSON_TITLE = "extra_lesson_title"
        const val EXTRA_LESSON_LOCATION = "extra_lesson_location"
        const val EXTRA_START_MINUTE = "extra_start_minute"
        const val EXTRA_REMINDER_KEY = "extra_reminder_key"
    }
}
