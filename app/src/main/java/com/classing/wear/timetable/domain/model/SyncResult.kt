package com.classing.wear.timetable.domain.model

enum class ConflictStrategy {
    WATCH_WINS,
    PHONE_WINS,
    LATEST_VERSION_WINS,
    MANUAL_REVIEW,
}

data class SyncResult(
    val success: Boolean,
    val syncedRecords: Int,
    val conflictCount: Int,
    val message: String,
)
