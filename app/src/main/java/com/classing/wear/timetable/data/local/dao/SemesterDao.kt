package com.classing.wear.timetable.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.classing.wear.timetable.data.local.entity.SemesterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SemesterDao {
    @Query("SELECT * FROM semesters WHERE isActive = 1 LIMIT 1")
    fun observeActiveSemester(): Flow<SemesterEntity?>

    @Query("SELECT * FROM semesters WHERE localId = :semesterId LIMIT 1")
    suspend fun getById(semesterId: Long): SemesterEntity?

    @Query("SELECT * FROM semesters WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): SemesterEntity?

    @Upsert
    suspend fun upsert(semester: SemesterEntity): Long

    @Upsert
    suspend fun upsertAll(semesters: List<SemesterEntity>)

    @Query("UPDATE semesters SET isActive = CASE WHEN localId = :semesterId THEN 1 ELSE 0 END")
    suspend fun setActiveSemester(semesterId: Long)
}
