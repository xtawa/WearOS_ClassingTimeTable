package com.xtawa.classingtime.ui.timetable

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.LocalTime

internal enum class TimetableCourseStatus {
    Past,
    Current,
    Future,
}

@Immutable
internal data class TimetableDayUiModel(
    val date: LocalDate,
    val dayLabel: String,
    val dateLabel: String,
    val courseCount: Int,
    val isToday: Boolean,
)

@Immutable
internal data class TimetableCourseUiModel(
    val id: String,
    val title: String,
    val teacher: String?,
    val location: String?,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val accent: Color,
    val status: TimetableCourseStatus,
)

@Immutable
internal data class TimetableUiState(
    val weekLabel: String,
    val selectedDate: LocalDate,
    val selectedDateLabel: String,
    val days: List<TimetableDayUiModel>,
    val courses: List<TimetableCourseUiModel>,
    val hasImportedSchedule: Boolean,
    val scheduleChangeCount: Int = 0,
)
