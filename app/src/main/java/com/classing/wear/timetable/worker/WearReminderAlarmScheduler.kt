package com.classing.wear.timetable.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.classing.wear.timetable.domain.model.KeepAliveLevel
import com.classing.wear.timetable.reminder.ReminderAlarmReceiver
import com.classing.wear.timetable.sync.MobileSyncPrefs
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.json.JSONObject

object WearReminderAlarmScheduler {
    private const val ALARM_REQUEST_CODE = 13022

    fun refresh(context: Context, enabled: Boolean, level: KeepAliveLevel) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!enabled || level == KeepAliveLevel.ECO) {
            cancel(context, alarmManager)
            return
        }

        val payload = context.getSharedPreferences(MobileSyncPrefs.PREF_NAME, Context.MODE_PRIVATE)
            .getString(MobileSyncPrefs.KEY_LAST_PAYLOAD, "")
            .orEmpty()
        val nextAlarm = findNextAlarm(payload) ?: run {
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

    private fun findNextAlarm(payload: String): AlarmCandidate? {
        val root = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        val lessons = root.optJSONArray("lessons") ?: return null
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val nowMinute = now.hour * 60 + now.minute

        val candidates = buildList {
            for (offset in 0..7) {
                val date = today.plusDays(offset.toLong())
                val dayOfWeek = date.dayOfWeek.value
                for (i in 0 until lessons.length()) {
                    val item = lessons.optJSONObject(i) ?: continue
                    if (item.optInt("dayOfWeek", -1) != dayOfWeek) continue
                    val lessonId = item.optString("id").ifBlank { "lesson-$i" }
                    val title = item.optString("title").ifBlank { "Class" }
                    val start = parseTime(item.optString("startTime")) ?: continue
                    val startMinute = start.hour * 60 + start.minute
                    val triggerMinute = startMinute - ReminderCheckLogic.LEAD_MINUTES
                    if (triggerMinute < 0) continue
                    if (offset == 0 && triggerMinute <= nowMinute) continue

                    val triggerTime = LocalTime.of(triggerMinute / 60, triggerMinute % 60)
                    val triggerAt = LocalDateTime.of(date, triggerTime)
                    val reminderKey = ReminderCheckLogic.reminderKey(
                        date = date,
                        lesson = SyncedLesson(
                            id = lessonId,
                            title = title,
                            dayOfWeek = dayOfWeek,
                            startTime = start,
                            location = item.optString("location").ifBlank { null },
                        ),
                    )
                    add(
                        AlarmCandidate(
                            triggerAtMillis = triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            reminderKey = reminderKey,
                            lessonId = lessonId,
                            title = title,
                            location = item.optString("location").ifBlank { null },
                            startMinute = startMinute,
                        ),
                    )
                }
            }
        }
        return candidates.minByOrNull { it.triggerAtMillis }
    }

    private fun parseTime(raw: String): LocalTime? {
        val text = raw.trim()
        if (text.isBlank()) return null
        return runCatching { LocalTime.parse(text, java.time.format.DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull()
            ?: runCatching { LocalTime.parse(text, java.time.format.DateTimeFormatter.ofPattern("H:mm")) }.getOrNull()
    }

    private data class AlarmCandidate(
        val triggerAtMillis: Long,
        val reminderKey: String,
        val lessonId: String,
        val title: String,
        val location: String?,
        val startMinute: Int,
    )
}
