package com.classing.mobile.timetable.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val UNIQUE_REMINDER_WORK = "mobile_lesson_reminder_periodic"

    fun sync(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(UNIQUE_REMINDER_WORK)
            return
        }

        val request = PeriodicWorkRequestBuilder<LessonReminderWorker>(15, TimeUnit.MINUTES)
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()

        manager.enqueueUniquePeriodicWork(
            UNIQUE_REMINDER_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
