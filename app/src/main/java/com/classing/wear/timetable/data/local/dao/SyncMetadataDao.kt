package com.classing.wear.timetable.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.classing.wear.timetable.data.local.entity.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE id = 0")
    fun observe(): Flow<SyncMetadataEntity?>

    @Query("SELECT * FROM sync_metadata WHERE id = 0")
    suspend fun get(): SyncMetadataEntity?

    @Upsert
    suspend fun upsert(metadata: SyncMetadataEntity)
}
