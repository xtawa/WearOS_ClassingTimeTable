package com.classing.wear.timetable.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val id: Int = 0,
    val lastFullSyncAt: Instant?,
    val lastDeltaSyncAt: Instant?,
    val lastSuccessAt: Instant?,
    val lastErrorMessage: String?,
    val dataVersion: Long,
    val pendingChanges: Int,
)
