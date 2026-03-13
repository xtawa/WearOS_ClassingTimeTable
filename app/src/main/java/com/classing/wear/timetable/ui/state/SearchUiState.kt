package com.classing.wear.timetable.ui.state

import com.classing.wear.timetable.domain.model.Course

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<Course> = emptyList(),
)
