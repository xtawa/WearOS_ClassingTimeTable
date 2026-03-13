package com.classing.wear.timetable.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.classing.wear.timetable.data.local.entity.CourseSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseSessionDao {
    @Query("SELECT * FROM course_sessions WHERE semesterId = :semesterId")
    fun observeBySemester(semesterId: Long): Flow<List<CourseSessionEntity>>

    @Query("SELECT * FROM course_sessions WHERE localId = :sessionId LIMIT 1")
    suspend fun getById(sessionId: Long): CourseSessionEntity?

    @Query("SELECT * FROM course_sessions WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): CourseSessionEntity?

    @Upsert
    suspend fun upsert(session: CourseSessionEntity): Long

    @Upsert
    suspend fun upsertAll(sessions: List<CourseSessionEntity>)

    @Query("DELETE FROM course_sessions WHERE semesterId = :semesterId")
    suspend fun deleteBySemester(semesterId: Long)
}
