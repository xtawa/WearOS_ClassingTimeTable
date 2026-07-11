package com.xtawa.classingtime.reminder

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.xtawa.classingtime.screen.EffectiveLessonOccurrence
import com.xtawa.classingtime.data.PersistedLesson
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class KeepAliveRuntimeStatus(
    val canScheduleExactAlarm: Boolean,
    val ignoringBatteryOptimizations: Boolean,
)

data class NextReminderAlarm(
    val triggerAtMillis: Long,
    val reminderKey: String,
    val lessonId: String,
    val title: String,
    val location: String?,
    val startMinute: Int,
)

object ReminderRuntime {
    const val DEDUP_PREF_NAME = "mobile_timetable_prefs"
    const val DEDUP_KEY_NOTIFIED_DATE = "reminder_notified_date"
    const val DEDUP_KEY_NOTIFIED_KEYS = "reminder_notified_keys"

    fun reminderKey(date: LocalDate, lessonId: String, startMinute: Int): String {
        return "$date:$lessonId:$startMinute"
    }

    fun resolveStatus(context: Context): KeepAliveRuntimeStatus {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val ignoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        return KeepAliveRuntimeStatus(
            canScheduleExactAlarm = canExact,
            ignoringBatteryOptimizations = ignoringBattery,
        )
    }

    fun findNextAlarm(
        lessons: List<PersistedLesson>,
        now: LocalDateTime,
        leadMinutes: Int,
    ): NextReminderAlarm? {
        val currentDay = now.toLocalDate()
        val currentMinute = now.hour * 60 + now.minute

        val candidates = buildList {
            for (offset in 0..7) {
                val date = currentDay.plusDays(offset.toLong())
                val dayOfWeek = date.dayOfWeek.value
                lessons.filter { it.dayOfWeek == dayOfWeek }.forEach { lesson ->
                    val triggerMinute = lesson.startMinute - leadMinutes
                    if (triggerMinute < 0) return@forEach
                    if (offset == 0 && triggerMinute <= currentMinute) return@forEach
                    val triggerTime = LocalTime.of(triggerMinute / 60, triggerMinute % 60)
                    val triggerAt = LocalDateTime.of(date, triggerTime)
                    add(
                        NextReminderAlarm(
                            triggerAtMillis = triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            reminderKey = reminderKey(date, lesson.id, lesson.startMinute),
                            lessonId = lesson.id,
                            title = lesson.title,
                            location = lesson.location,
                            startMinute = lesson.startMinute,
                        ),
                    )
                }
            }
        }
        return candidates.minByOrNull { it.triggerAtMillis }
    }

    internal fun findNextAlarm(
        occurrences: List<EffectiveLessonOccurrence>,
        now: LocalDateTime,
        leadMinutes: Int,
    ): NextReminderAlarm? {
        val currentMinute = now.hour * 60 + now.minute
        val currentDay = now.toLocalDate()
        val candidates = occurrences.mapNotNull { occurrence ->
            val startMinute = occurrence.lesson.startTime.hour * 60 + occurrence.lesson.startTime.minute
            val triggerMinute = startMinute - leadMinutes
            if (triggerMinute < 0) return@mapNotNull null
            if (occurrence.date == currentDay && triggerMinute <= currentMinute) return@mapNotNull null
            val triggerTime = LocalTime.of(triggerMinute / 60, triggerMinute % 60)
            val triggerAt = LocalDateTime.of(occurrence.date, triggerTime)
            NextReminderAlarm(
                triggerAtMillis = triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                reminderKey = reminderKey(occurrence.date, occurrence.lesson.id, startMinute),
                lessonId = occurrence.lesson.id,
                title = occurrence.lesson.title,
                location = occurrence.lesson.location,
                startMinute = startMinute,
            )
        }
        return candidates.minByOrNull { it.triggerAtMillis }
    }

}
