package com.xtawa.classingtime.screen

internal data class MobileTimetableUiState(
    val schedule: MobileScheduleUiState = MobileScheduleUiState(),
    val import: MobileImportUiState = MobileImportUiState(),
    val settings: MobileSettingsUiState = MobileSettingsUiState(),
    val sync: MobileSyncUiState = MobileSyncUiState(),
    val account: MobileAccountUiState = MobileAccountUiState(),
)

internal data class MobileScheduleUiState(
    val hasLessons: Boolean = false,
    val visibleLessonCount: Int = 0,
    val selectedLayerName: String = MobileLayer.Dashboard.name,
    val selectedScheduleSubviewName: String = ScheduleSubview.Timetable.name,
)

internal data class MobileImportUiState(
    val rawIcs: String = "",
    val rawJson: String = "",
    val parseMessage: String = "",
    val warningCount: Int = 0,
    val pendingImportCount: Int = 0,
)

internal data class MobileSettingsUiState(
    val showWeekend: Boolean = true,
    val reminderEnabled: Boolean = false,
    val reminderMinutes: Int = 15,
    val weekNumberModeName: String = WeekNumberMode.NATURAL.name,
)

internal data class MobileSyncUiState(
    val wearSyncInProgress: Boolean = false,
    val cloudSyncInProgress: Boolean = false,
    val wearConnectedCount: Int = 0,
    val cloudSyncEnabled: Boolean = false,
)

internal data class MobileAccountUiState(
    val loggedIn: Boolean = false,
    val busy: Boolean = false,
    val statusMessage: String = "",
)
