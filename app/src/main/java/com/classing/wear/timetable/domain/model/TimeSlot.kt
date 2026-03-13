package com.classing.wear.timetable.domain.model

import java.time.LocalTime

data class TimeSlot(
    val localId: Long,
    val remoteId: String?,
    val semesterId: Long,
    val indexInDay: Int,
    val label: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val version: Long,
)
