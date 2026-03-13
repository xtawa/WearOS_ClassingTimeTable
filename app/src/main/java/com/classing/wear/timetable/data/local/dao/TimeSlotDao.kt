package com.classing.wear.timetable.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.classing.wear.timetable.data.local.entity.TimeSlotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeSlotDao {
    @Query("SELECT * FROM time_slots WHERE semesterId = :semesterId ORDER BY indexInDay ASC")
    fun observeBySemester(semesterId: Long): Flow<List<TimeSlotEntity>>

    @Query("SELECT * FROM time_slots WHERE localId = :slotId LIMIT 1")
    suspend fun getById(slotId: Long): TimeSlotEntity?

    @Query("SELECT * FROM time_slots WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): TimeSlotEntity?

    @Upsert
    suspend fun upsert(slot: TimeSlotEntity): Long

    @Upsert
    suspend fun upsertAll(slots: List<TimeSlotEntity>)

    @Query("DELETE FROM time_slots WHERE semesterId = :semesterId")
    suspend fun deleteBySemester(semesterId: Long)
}
