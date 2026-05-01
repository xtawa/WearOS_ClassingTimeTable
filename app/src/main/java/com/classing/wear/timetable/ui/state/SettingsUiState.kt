package com.classing.wear.timetable.ui.state

import com.classing.wear.timetable.domain.repository.UserPreferences

enum class SyncFeedback {
    SUCCESS,
    CHECK_PHONE_CONNECTION,
}

data class SettingsUiState(
    val isLoading: Boolean = true,
    val preferences: UserPreferences = UserPreferences(),
    val syncMessage: String = "",
    val syncFeedback: SyncFeedback? = null,
)
