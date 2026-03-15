package com.classing.wear.timetable.worker

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class SyncedLesson(
    val id: String,
    val title: String,
    val dayOfWeek: Int,
    val startTime: LocalTime,
    val location: String?,
)

object ReminderCheckLogic {
    private const val LEAD_MINUTES = 15
    private const val WINDOW_MINUTES = 15

    fun dueLessons(
        lessons: List<SyncedLesson>,
        now: LocalDateTime,
        notifiedKeys: Set<String>,
    ): List<SyncedLesson> {
        val nowMinute = now.hour * 60 + now.minute
        val day = now.dayOfWeek.value
        val today = now.toLocalDate()

        return lessons.filter { lesson ->
            if (lesson.dayOfWeek != day) return@filter false

            val startMinute = lesson.startTime.hour * 60 + lesson.startTime.minute
            val triggerMinute = startMinute - LEAD_MINUTES
            if (triggerMinute < 0) return@filter false

            val key = reminderKey(today, lesson)
            val inWindow = nowMinute in triggerMinute until (triggerMinute + WINDOW_MINUTES)
            inWindow && key !in notifiedKeys
        }
    }

    fun reminderKey(date: LocalDate, lesson: SyncedLesson): String {
        val startMinute = lesson.startTime.hour * 60 + lesson.startTime.minute
        return "${date}:${lesson.id}:${startMinute}"
    }
}
