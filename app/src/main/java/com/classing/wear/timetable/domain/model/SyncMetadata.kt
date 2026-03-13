package com.classing.wear.timetable.domain.model

import java.time.Instant

data class SyncMetadata(
    val lastFullSyncAt: Instant?,
    val lastDeltaSyncAt: Instant?,
    val lastSuccessAt: Instant?,
    val lastErrorMessage: String?,
    val dataVersion: Long,
    val pendingChanges: Int,
)

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
