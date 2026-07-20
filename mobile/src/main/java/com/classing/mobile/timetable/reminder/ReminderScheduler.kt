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
import com.xtawa.classingtime.screen.WeekNumberMode
import com.xtawa.classingtime.screen.buildEffectiveOccurrencesForDateRange
import com.xtawa.classingtime.screen.toLessonUi
import com.xtawa.classingtime.screen.toUi
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val UNIQUE_REMINDER_WORK = "mobile_lesson_reminder_periodic"
    private const val LEGACY_REMINDER_ALARM_REQUEST_CODE = 22031

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
            cancelScheduledAlarms(context, alarmManager)
            ReminderReconcileStore.clear(context)
            return
        }

        val request = PeriodicWorkRequestBuilder<LessonReminderWorker>(15, TimeUnit.MINUTES).build()
        manager.enqueueUniquePeriodicWork(
            UNIQUE_REMINDER_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )

        if (keepAliveLevel == KeepAliveLevel.ECO) {
            cancelScheduledAlarms(context, alarmManager)
            ReminderReconcileStore.save(
                context,
                ReminderReconcileState(lastReconcileAt = System.currentTimeMillis()),
            )
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
            cancelScheduledAlarms(context, alarmManager)
            ReminderReconcileStore.save(
                context,
                ReminderReconcileState(lastReconcileAt = System.currentTimeMillis()),
            )
            return
        }

        val state = MobilePrefsStore.loadTimetableState(context)
        val settings = MobilePrefsStore.loadSettings(context)
        val weekNumberMode = WeekNumberMode.entries.firstOrNull { it.name == settings.weekNumberMode } ?: WeekNumberMode.NATURAL
        val semesterWeekStartDate = runCatching { LocalDate.parse(settings.semesterWeekStartDate) }.getOrDefault(LocalDate.now())
        val occurrences = buildEffectiveOccurrencesForDateRange(
            baseLessons = state.baseLessons.map { it.toLessonUi() },
            exceptions = state.exceptions.map { it.toUi() },
            startDate = LocalDate.now(),
            endDate = LocalDate.now().plusDays(7),
            weekNumberMode = weekNumberMode,
            semesterWeekStartDate = semesterWeekStartDate,
            weekStartDay = runCatching { DayOfWeek.valueOf(settings.weekStartDay) }.getOrDefault(DayOfWeek.MONDAY),
        )
        val next = ReminderRuntime.findNextAlarm(
            occurrences = occurrences,
            now = LocalDateTime.now(),
            leadMinutes = reminderMinutes.coerceIn(5, 60),
        )
        if (next == null) {
            cancelScheduledAlarms(context, alarmManager)
            ReminderReconcileStore.save(
                context,
                ReminderReconcileState(lastReconcileAt = System.currentTimeMillis()),
            )
            return
        }

        reconcileNextAlarm(context, alarmManager, next, keepAliveLevel)
    }

    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelScheduledAlarms(context, alarmManager)
        ReminderReconcileStore.clear(context)
    }

    private fun reconcileNextAlarm(
        context: Context,
        alarmManager: AlarmManager,
        next: NextReminderAlarm,
        keepAliveLevel: KeepAliveLevel,
    ) {
        val previous = ReminderReconcileStore.load(context)
        val requestCode = ReminderRuntime.requestCodeForReminder(next.reminderKey)
        var keptScheduledKey = previous.scheduledReminderKey
        var keptScheduledRequestCode = previous.scheduledRequestCode
        var keptScheduledTriggerAtMillis = previous.scheduledTriggerAtMillis

        if (previous.scheduledRequestCode > 0 && previous.scheduledRequestCode != requestCode) {
            cancelAlarm(context, alarmManager, previous.scheduledRequestCode)
            keptScheduledKey = null
            keptScheduledRequestCode = 0
            keptScheduledTriggerAtMillis = 0L
        }
        cancelAlarm(context, alarmManager, LEGACY_REMINDER_ALARM_REQUEST_CODE)

        val result = scheduleAlarm(context, alarmManager, next, requestCode, keepAliveLevel)
        val reconciledAt = System.currentTimeMillis()
        val reconcileState = if (result.isSuccess) {
            ReminderReconcileState(
                desiredReminderKey = next.reminderKey,
                desiredRequestCode = requestCode,
                desiredTriggerAtMillis = next.triggerAtMillis,
                scheduledReminderKey = next.reminderKey,
                scheduledRequestCode = requestCode,
                scheduledTriggerAtMillis = next.triggerAtMillis,
                lastFailure = null,
                lastReconcileAt = reconciledAt,
            )
        } else {
            ReminderReconcileState(
                desiredReminderKey = next.reminderKey,
                desiredRequestCode = requestCode,
                desiredTriggerAtMillis = next.triggerAtMillis,
                scheduledReminderKey = keptScheduledKey,
                scheduledRequestCode = keptScheduledRequestCode,
                scheduledTriggerAtMillis = keptScheduledTriggerAtMillis,
                lastFailure = result.exceptionOrNull()?.message ?: "Alarm scheduling failed",
                lastReconcileAt = reconciledAt,
            )
        }
        ReminderReconcileStore.save(context, reconcileState)
    }

    private fun scheduleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        next: NextReminderAlarm,
        requestCode: Int,
        keepAliveLevel: KeepAliveLevel,
    ): kotlin.Result<Unit> {
        return runCatching {
            val alarmIntent = Intent(context, LessonReminderAlarmReceiver::class.java).apply {
                putExtra(LessonReminderAlarmReceiver.EXTRA_LESSON_ID, next.lessonId)
                putExtra(LessonReminderAlarmReceiver.EXTRA_LESSON_TITLE, next.title)
                putExtra(LessonReminderAlarmReceiver.EXTRA_LESSON_LOCATION, next.location)
                putExtra(LessonReminderAlarmReceiver.EXTRA_START_MINUTE, next.startMinute)
                putExtra(LessonReminderAlarmReceiver.EXTRA_REMINDER_KEY, next.reminderKey)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
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
    }

    private fun canUseExactAlarm(alarmManager: AlarmManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun cancelScheduledAlarms(context: Context, alarmManager: AlarmManager) {
        val state = ReminderReconcileStore.load(context)
        val requestCodes = buildSet {
            add(LEGACY_REMINDER_ALARM_REQUEST_CODE)
            if (state.desiredRequestCode > 0) add(state.desiredRequestCode)
            if (state.scheduledRequestCode > 0) add(state.scheduledRequestCode)
        }
        requestCodes.forEach { requestCode ->
            cancelAlarm(context, alarmManager, requestCode)
        }
    }

    private fun cancelAlarm(context: Context, alarmManager: AlarmManager, requestCode: Int) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, LessonReminderAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}
