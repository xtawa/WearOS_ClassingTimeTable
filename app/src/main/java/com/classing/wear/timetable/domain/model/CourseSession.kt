package com.classing.wear.timetable.domain.model

import java.time.DayOfWeek

data class CourseSession(
    val localId: Long,
    val remoteId: String?,
    val semesterId: Long,
    val courseId: Long,
    val dayOfWeek: DayOfWeek,
    val timeSlotId: Long,
    val weekRule: WeekRule,
    val version: Long,
)
