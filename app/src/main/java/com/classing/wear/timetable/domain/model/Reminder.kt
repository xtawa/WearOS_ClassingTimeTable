package com.classing.wear.timetable.domain.model

import java.time.Duration

data class Reminder(
    val localId: Long,
    val remoteId: String?,
    val semesterId: Long,
    val targetType: ReminderTarget,
    val targetId: Long,
    val minutesBefore: Int,
    val enabled: Boolean,
    val version: Long,
)

enum class ReminderTarget {
    SESSION,
    EXAM,
    HOMEWORK,
}

data class NextLessonHint(
    val lesson: LessonOccurrence?,
    val countdown: Duration?,
)
