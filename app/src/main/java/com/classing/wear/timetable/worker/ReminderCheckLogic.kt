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
    internal const val LEAD_MINUTES = 15
    internal const val WINDOW_MINUTES = 10

    fun dueLessons(
        lessons: List<SyncedLesson>,
        now: LocalDateTime,
        notifiedKeys: Set<String>,
    ): List<SyncedLesson> {
        return lessons.filter { lesson ->
            val window = reminderWindow(now, lesson) ?: return@filter false
            val key = reminderKey(window.lessonDate, lesson)
            key !in notifiedKeys
        }
    }

    fun reminderKey(date: LocalDate, lesson: SyncedLesson): String {
        val startMinute = lesson.startTime.hour * 60 + lesson.startTime.minute
        return "${date}:${lesson.id}:${startMinute}"
    }

    private fun reminderWindow(now: LocalDateTime, lesson: SyncedLesson): ReminderWindow? {
        val today = now.toLocalDate()
        candidateLessonDates(today, lesson.dayOfWeek).forEach { lessonDate ->
            val triggerAt = LocalDateTime.of(lessonDate, lesson.startTime)
                .minusMinutes(LEAD_MINUTES.toLong())
            val windowEnd = triggerAt.plusMinutes(WINDOW_MINUTES.toLong())
            if (!now.isBefore(triggerAt) && now.isBefore(windowEnd)) {
                return ReminderWindow(lessonDate = lessonDate)
            }
        }
        return null
    }

    private fun candidateLessonDates(today: LocalDate, lessonDayOfWeek: Int): List<LocalDate> {
        val candidates = ArrayList<LocalDate>(2)
        if (today.dayOfWeek.value == lessonDayOfWeek) {
            candidates += today
        }
        val tomorrow = today.plusDays(1)
        if (tomorrow.dayOfWeek.value == lessonDayOfWeek) {
            candidates += tomorrow
        }
        return candidates
    }

    private data class ReminderWindow(
        val lessonDate: LocalDate,
    )
}
