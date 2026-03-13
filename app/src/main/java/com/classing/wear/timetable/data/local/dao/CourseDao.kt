package com.classing.wear.timetable.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.classing.wear.timetable.data.local.entity.CourseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses WHERE semesterId = :semesterId")
    fun observeBySemester(semesterId: Long): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE localId = :courseId LIMIT 1")
    fun observeById(courseId: Long): Flow<CourseEntity?>

    @Query("SELECT * FROM courses WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): CourseEntity?

    @Query("SELECT * FROM courses WHERE semesterId = :semesterId AND name LIKE '%' || :keyword || '%' ORDER BY isFavorite DESC, name ASC")
    fun search(semesterId: Long, keyword: String): Flow<List<CourseEntity>>

    @Upsert
    suspend fun upsert(course: CourseEntity): Long

    @Upsert
    suspend fun upsertAll(courses: List<CourseEntity>)

    @Query("DELETE FROM courses WHERE semesterId = :semesterId")
    suspend fun deleteBySemester(semesterId: Long)
}
