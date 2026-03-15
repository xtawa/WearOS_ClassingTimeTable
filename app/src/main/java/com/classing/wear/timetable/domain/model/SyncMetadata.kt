package com.classing.wear.timetable.domain.model

import java.time.Instant

enum class SyncMode {
    FULL,
    DELTA,
}

sealed class SyncState {
    data object Idle : SyncState()
    data object Syncing : SyncState()
    data class Success(val at: Instant) : SyncState()
    data class Failed(val message: String) : SyncState()
}
