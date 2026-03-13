package com.classing.wear.timetable.domain.repository

import com.classing.wear.timetable.domain.model.SyncMetadata
import com.classing.wear.timetable.domain.model.SyncMode
import com.classing.wear.timetable.domain.model.SyncResult
import com.classing.wear.timetable.domain.model.SyncState
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    fun observeSyncState(): Flow<SyncState>
    fun observeSyncMetadata(): Flow<SyncMetadata>
    suspend fun sync(mode: SyncMode = SyncMode.DELTA): SyncResult
}
