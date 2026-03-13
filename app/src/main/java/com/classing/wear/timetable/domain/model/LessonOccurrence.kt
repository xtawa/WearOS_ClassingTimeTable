package com.classing.wear.timetable.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

data class LessonOccurrence(
    val course: Course,
    val session: CourseSession,
    val timeSlot: TimeSlot,
    val date: LocalDate,
    val weekIndex: Int,
    val status: LessonStatus,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
)

data class WeekSchedule(
    val weekIndex: Int,
    val days: Map<DayOfWeek, List<LessonOccurrence>>,
)
