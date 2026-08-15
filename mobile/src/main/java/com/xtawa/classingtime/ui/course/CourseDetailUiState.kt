package com.xtawa.classingtime.ui.course

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.LocalTime

internal enum class CourseDetailStatus {
    Upcoming,
    InClass,
    Finished,
}

@Immutable
internal data class CourseDetailUiState(
    val id: String,
    val title: String,
    val date: LocalDate,
    val dateLabel: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val location: String?,
    val teacher: String?,
    val note: String?,
    val recurrenceLabel: String,
    val accent: Color,
    val status: CourseDetailStatus,
    val temporalLabel: String,
    val progress: Float? = null,
)
