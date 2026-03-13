package com.classing.wear.timetable.domain.model

import java.time.LocalDate

sealed class ScheduleException {
    abstract val localId: Long
    abstract val remoteId: String?
    abstract val semesterId: Long
    abstract val sessionId: Long?
    abstract val date: LocalDate
    abstract val reason: String
    abstract val version: Long

    data class Cancel(
        override val localId: Long,
        override val remoteId: String?,
        override val semesterId: Long,
        override val sessionId: Long,
        override val date: LocalDate,
        override val reason: String,
        override val version: Long,
    ) : ScheduleException()

    data class MakeUp(
        override val localId: Long,
        override val remoteId: String?,
        override val semesterId: Long,
        override val sessionId: Long?,
        val courseId: Long,
        val timeSlotId: Long,
        val dayOfWeek: Int,
        override val date: LocalDate,
        override val reason: String,
        override val version: Long,
    ) : ScheduleException()

    data class Reschedule(
        override val localId: Long,
        override val remoteId: String?,
        override val semesterId: Long,
        override val sessionId: Long,
        val newCourseId: Long,
        val newTimeSlotId: Long,
        override val date: LocalDate,
        override val reason: String,
        override val version: Long,
    ) : ScheduleException()
}
