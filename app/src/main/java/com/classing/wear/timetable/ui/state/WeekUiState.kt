package com.classing.wear.timetable.ui.state

import com.classing.wear.timetable.domain.model.WeekSchedule

data class WeekUiState(
    val isLoading: Boolean = true,
    val weekLabel: String = "",
    val schedule: WeekSchedule = WeekSchedule(weekIndex = 1, days = emptyMap()),
    val errorMessage: String? = null,
)
