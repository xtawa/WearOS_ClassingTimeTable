package com.xtawa.classingtime.ui.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.LocalTime

internal enum class HomePhase {
    Upcoming,
    InClass,
    Break,
    Finished,
    NoClasses,
}

@Immutable
internal data class HomeCourseUiModel(
    val id: String,
    val sourceLessonId: String,
    val title: String,
    val teacher: String?,
    val location: String?,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val accent: Color,
)

@Immutable
internal data class HomeUiState(
    val phase: HomePhase,
    val now: LocalTime,
    val date: LocalDate,
    val primaryCourse: HomeCourseUiModel?,
    val nextCourse: HomeCourseUiModel?,
    val futureCourses: List<HomeCourseUiModel>,
    val todayCourseCount: Int,
    val countdownMinutes: Long? = null,
    val remainingMinutes: Long? = null,
    val breakMinutes: Long? = null,
    val classProgress: Float = 0f,
    val hasImportedSchedule: Boolean = true,
)

@Immutable
internal data class HomeAssistantUiState(
    val focused: Boolean = false,
    val query: String = "",
    val processing: Boolean = false,
)
