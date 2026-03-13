package com.classing.wear.timetable.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.classing.wear.timetable.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE semesterId = :semesterId AND enabled = 1")
    fun observeEnabledBySemester(semesterId: Long): Flow<List<ReminderEntity>>

    @Upsert
    suspend fun upsertAll(reminders: List<ReminderEntity>)
}
