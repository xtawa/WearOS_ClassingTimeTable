package com.classing.wear.timetable.worker

import com.classing.wear.timetable.domain.model.LessonOccurrence
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class SyncedLesson(
    val id: String,
    val title: String,
    val dayOfWeek: Int,
    val startTime: LocalTime,
    val location: String?,
)

/**
 * A lesson bound to a concrete calendar date. Unlike [SyncedLesson] (a weekly template), this
 * already has week rules and schedule exceptions applied, so the reminder pipeline never has to
 * guess whether a weekday template is really taught on a given date.
 */
data class ReminderOccurrence(
    val id: String,
    val title: String,
    val date: LocalDate,
    val startTime: LocalTime,
    val location: String?,
) {
    val startMinute: Int get() = startTime.hour * 60 + startTime.minute

    fun asSyncedLesson(): SyncedLesson = SyncedLesson(
        id = id,
        title = title,
        dayOfWeek = date.dayOfWeek.value,
        startTime = startTime,
        location = location,
    )
}

data class ReminderAlarmCandidate(
    val triggerAtMillis: Long,
    val reminderKey: String,
    val lessonId: String,
    val title: String,
    val location: String?,
    val startMinute: Int,
)

object ReminderCheckLogic {
    internal const val LEAD_MINUTES = 15
    internal const val WINDOW_MINUTES = 10

    /** How many days ahead the alarm scheduler looks for the next reminder. */
    internal const val LOOKAHEAD_DAYS = 7L

    /** Stable reminder identity for a projected occurrence (session remote id, or local id fallback). */
    fun occurrenceId(occurrence: LessonOccurrence): String {
        val session = occurrence.session
        return session.remoteId?.takeIf { it.isNotBlank() } ?: "session-${session.localId}"
    }

    fun fromOccurrences(occurrences: List<LessonOccurrence>): List<ReminderOccurrence> {
        return occurrences.map { occurrence ->
            ReminderOccurrence(
                id = occurrenceId(occurrence),
                title = occurrence.course.name,
                date = occurrence.date,
                startTime = occurrence.startAt.toLocalTime(),
                location = occurrence.course.classroom.takeIf { it.isNotBlank() },
            )
        }.distinctBy { reminderKey(it) }
    }

    fun reminderKey(occurrence: ReminderOccurrence): String = reminderKey(occurrence.date, occurrence.asSyncedLesson())

    /** Occurrences whose reminder window ([LEAD_MINUTES] before start, lasting [WINDOW_MINUTES]) contains [now]. */
    fun dueOccurrences(
        occurrences: List<ReminderOccurrence>,
        now: LocalDateTime,
        notifiedKeys: Set<String>,
    ): List<ReminderOccurrence> {
        return occurrences.filter { occurrence ->
            val triggerAt = LocalDateTime.of(occurrence.date, occurrence.startTime)
                .minusMinutes(LEAD_MINUTES.toLong())
            val windowEnd = triggerAt.plusMinutes(WINDOW_MINUTES.toLong())
            !now.isBefore(triggerAt) && now.isBefore(windowEnd) && reminderKey(occurrence) !in notifiedKeys
        }
    }

    /**
     * Resolves the occurrence an exact alarm was armed for. The alarm payload may be stale (the lesson
     * was cancelled or rescheduled after the alarm was set), so the reminder is only honoured when the
     * same key still exists in the current projection and the lesson has not started yet.
     */
    fun resolveDirectReminder(
        occurrences: List<ReminderOccurrence>,
        reminderKey: String,
        now: LocalDateTime,
    ): ReminderOccurrence? {
        if (reminderKey.isBlank()) return null
        val occurrence = occurrences.firstOrNull { reminderKey(it) == reminderKey } ?: return null
        val startAt = LocalDateTime.of(occurrence.date, occurrence.startTime)
        return occurrence.takeIf { now.isBefore(startAt) }
    }

    /** The earliest future reminder trigger among [occurrences], or null when nothing is left to remind. */
    fun nextAlarm(
        occurrences: List<ReminderOccurrence>,
        now: LocalDateTime,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ReminderAlarmCandidate? {
        return occurrences.mapNotNull { occurrence ->
            val triggerAt = LocalDateTime.of(occurrence.date, occurrence.startTime)
                .minusMinutes(LEAD_MINUTES.toLong())
            if (!triggerAt.isAfter(now)) return@mapNotNull null
            ReminderAlarmCandidate(
                triggerAtMillis = triggerAt.atZone(zoneId).toInstant().toEpochMilli(),
                reminderKey = reminderKey(occurrence),
                lessonId = occurrence.id,
                title = occurrence.title,
                location = occurrence.location,
                startMinute = occurrence.startMinute,
            )
        }.minByOrNull { it.triggerAtMillis }
    }

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
