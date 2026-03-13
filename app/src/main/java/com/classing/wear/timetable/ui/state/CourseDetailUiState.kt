package com.classing.wear.timetable.ui.state

import com.classing.wear.timetable.domain.model.Course
import com.classing.wear.timetable.domain.model.LessonOccurrence

data class CourseDetailUiState(
    val isLoading: Boolean = true,
    val course: Course? = null,
    val upcomingLessons: List<LessonOccurrence> = emptyList(),
    val errorMessage: String? = null,
)
