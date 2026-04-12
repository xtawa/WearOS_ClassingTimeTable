package com.xtawa.classingtime.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.xtawa.classingtime.data.MobilePrefsStore
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val UNIQUE_REMINDER_WORK = "mobile_lesson_reminder_periodic"
    private const val REMINDER_ALARM_REQUEST_CODE = 22031

    fun sync(
        context: Context,
        enabled: Boolean,
        keepAliveLevel: KeepAliveLevel,
        reminderMinutes: Int,
    ) {
        val manager = WorkManager.getInstance(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!enabled) {
            manager.cancelUniqueWork(UNIQUE_REMINDER_WORK)
            cancelAlarm(context, alarmManager)
            return
        }

        val request = PeriodicWorkRequestBuilder<LessonReminderWorker>(15, TimeUnit.MINUTES).build()
        manager.enqueueUniquePeriodicWork(
            UNIQUE_REMINDER_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )

        if (keepAliveLevel == KeepAliveLevel.ECO) {
            cancelAlarm(context, alarmManager)
            return
        }
        refreshNextAlarm(context, keepAliveLevel, reminderMinutes)
    }

    fun refreshNextAlarm(
        context: Context,
        keepAliveLevel: KeepAliveLevel,
        reminderMinutes: Int,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (keepAliveLevel == KeepAliveLevel.ECO) {
            cancelAlarm(context, alarmManager)
            return
        }

        val lessons = MobilePrefsStore.loadLessons(context)
        val next = ReminderRuntime.findNextAlarm(
            lessons = lessons,
            now = LocalDateTime.now(),
            leadMinutes = reminderMinutes.coerceIn(5, 60),
        )
        if (next == null) {
            cancelAlarm(context, alarmManager)
            return
        }

        val alarmIntent = Intent(context, LessonReminderAlarmReceiver::class.java).apply {
            putExtra(LessonReminderAlarmReceiver.EXTRA_LESSON_ID, next.lessonId)
            putExtra(LessonReminderAlarmReceiver.EXTRA_LESSON_TITLE, next.title)
            putExtra(LessonReminderAlarmReceiver.EXTRA_LESSON_LOCATION, next.location)
            putExtra(LessonReminderAlarmReceiver.EXTRA_START_MINUTE, next.startMinute)
            putExtra(LessonReminderAlarmReceiver.EXTRA_REMINDER_KEY, next.reminderKey)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REMINDER_ALARM_REQUEST_CODE,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val canExact = keepAliveLevel == KeepAliveLevel.AGGRESSIVE && canUseExactAlarm(alarmManager)
        when {
            canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.triggerAtMillis, pendingIntent)
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.triggerAtMillis, pendingIntent)
            }

            else -> {
                alarmManager.set(AlarmManager.RTC_WAKEUP, next.triggerAtMillis, pendingIntent)
            }
        }
    }

    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAlarm(context, alarmManager)
    }

    private fun canUseExactAlarm(alarmManager: AlarmManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun cancelAlarm(context: Context, alarmManager: AlarmManager) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REMINDER_ALARM_REQUEST_CODE,
            Intent(context, LessonReminderAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}
