package com.classing.wear.timetable.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.classing.wear.timetable.domain.model.KeepAliveLevel
import com.classing.wear.timetable.reminder.ReminderAlarmReceiver
import java.time.LocalDateTime

object WearReminderAlarmScheduler {
    private const val ALARM_REQUEST_CODE = 13022

    /**
     * Re-arms the single "next reminder" alarm from the projected timetable stored in the Wear
     * database. Safe to call from any sync/apply path; it cancels the alarm when reminders are
     * disabled, in ECO mode, or when no future lesson exists in the lookahead window.
     */
    suspend fun refresh(context: Context, enabled: Boolean, level: KeepAliveLevel) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!enabled || level == KeepAliveLevel.ECO) {
            cancel(context, alarmManager)
            return
        }

        val now = LocalDateTime.now()
        // A failed database read must not cancel a valid alarm: keep whatever is currently armed.
        val occurrences = WearReminderSource.loadOrNull(context, now) ?: return
        val nextAlarm = ReminderCheckLogic.nextAlarm(occurrences, now) ?: run {
            cancel(context, alarmManager)
            return
        }

        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(ReminderAlarmReceiver.EXTRA_LESSON_ID, nextAlarm.lessonId)
            putExtra(ReminderAlarmReceiver.EXTRA_LESSON_TITLE, nextAlarm.title)
            putExtra(ReminderAlarmReceiver.EXTRA_LESSON_LOCATION, nextAlarm.location)
            putExtra(ReminderAlarmReceiver.EXTRA_START_MINUTE, nextAlarm.startMinute)
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_KEY, nextAlarm.reminderKey)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val canExact = level == KeepAliveLevel.AGGRESSIVE && canUseExactAlarm(alarmManager)

        try {
            when {
                canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarm.triggerAtMillis, pending)
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarm.triggerAtMillis, pending)
                }

                else -> {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, nextAlarm.triggerAtMillis, pending)
                }
            }
        } catch (_: SecurityException) {
            // SCHEDULE_EXACT_ALARM can be revoked by the user between the capability check and the
            // call; fall back to an inexact alarm instead of crashing the sync path (minSdk 30).
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarm.triggerAtMillis, pending)
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancel(context, alarmManager)
    }

    private fun cancel(context: Context, alarmManager: AlarmManager) {
        val pending = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            Intent(context, ReminderAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pending)
        pending.cancel()
    }

    private fun canUseExactAlarm(alarmManager: AlarmManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
}
