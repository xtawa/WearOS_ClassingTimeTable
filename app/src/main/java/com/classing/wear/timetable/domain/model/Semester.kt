package com.classing.wear.timetable.domain.model

import java.time.LocalDate

data class Semester(
    val localId: Long,
    val remoteId: String?,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalWeeks: Int,
    val isActive: Boolean,
    val version: Long,
)
