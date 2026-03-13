package com.classing.wear.timetable.ui.state

import com.classing.wear.timetable.domain.repository.UserPreferences

data class SettingsUiState(
    val isLoading: Boolean = true,
    val preferences: UserPreferences = UserPreferences(),
    val syncMessage: String = "",
)
