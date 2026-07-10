package com.xtawa.classingtime.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.xtawa.classingtime.MainActivity
import com.xtawa.classingtime.R
import com.xtawa.classingtime.data.DailyBriefingChannel
import com.xtawa.classingtime.data.MobilePrefsStore
import com.xtawa.classingtime.data.MobileSettings
import com.xtawa.classingtime.screen.WeekNumberMode
import com.xtawa.classingtime.screen.buildEffectiveOccurrencesForDateRange
import com.xtawa.classingtime.screen.toLessonUi
import com.xtawa.classingtime.screen.toUi
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DailyBriefingScheduler {
    private const val REQUEST_CODE = 24011
    private const val CHANNEL_ID = "daily_briefing_channel"

    fun sync(context: Context, settings: MobileSettings) {
        if (!settings.dailyBriefingEnabled || settings.dailyBriefingChannel == DailyBriefingChannel.EMAIL) {
            cancel(context)
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextTriggerAt = computeNextTrigger(settings.dailyBriefingTime)
        val pendingIntent = pendingIntent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerAt, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextTriggerAt, pendingIntent)
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun handleTrigger(context: Context) {
        val settings = MobilePrefsStore.loadSettings(context)
        if (!settings.dailyBriefingEnabled) {
            cancel(context)
            return
        }
        if (settings.dailyBriefingChannel != DailyBriefingChannel.EMAIL) {
            postNotification(context, settings)
        }
        sync(context, settings)
    }

    private fun computeNextTrigger(rawTime: String): Long {
        val now = LocalDateTime.now()
        val target = runCatching { LocalTime.parse(rawTime, DateTimeFormatter.ofPattern("HH:mm")) }
            .getOrDefault(LocalTime.of(20, 0))
        var next = LocalDateTime.of(now.toLocalDate(), target)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun pendingIntent(context: Context): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, DailyBriefingAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun postNotification(context: Context, settings: MobileSettings) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        ensureChannel(context)
        val summary = buildSummary(context, settings)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.daily_briefing_notification_title))
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(REQUEST_CODE, notification)
    }

    private fun buildSummary(context: Context, settings: MobileSettings): String {
        val today = LocalDate.now()
        val state = MobilePrefsStore.loadTimetableState(context)
        val weekNumberMode = WeekNumberMode.entries.firstOrNull { it.name == settings.weekNumberMode } ?: WeekNumberMode.NATURAL
        val semesterWeekStartDate = runCatching { LocalDate.parse(settings.semesterWeekStartDate) }.getOrDefault(today)
        val occurrences = buildEffectiveOccurrencesForDateRange(
            baseLessons = state.baseLessons.map { it.toLessonUi() },
            exceptions = state.exceptions.map { it.toUi() },
            startDate = today,
            endDate = today,
            weekNumberMode = weekNumberMode,
            semesterWeekStartDate = semesterWeekStartDate,
        )
        val next = occurrences
            .map { it.lesson }
            .sortedBy { it.startTime }
            .firstOrNull { LocalDateTime.of(today, it.endTime).isAfter(LocalDateTime.now()) }
        return if (next == null) {
            context.getString(
                R.string.daily_briefing_notification_empty,
                occurrences.size,
            )
        } else {
            context.getString(
                R.string.daily_briefing_notification_body,
                occurrences.size,
                next.title,
                next.startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
            )
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.daily_briefing_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.daily_briefing_channel_desc)
            },
        )
    }
}

class DailyBriefingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        DailyBriefingScheduler.handleTrigger(context.applicationContext)
    }
}
