package com.classing.wear.timetable.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.classing.wear.timetable.data.local.entity.ExamEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams ORDER BY id") fun observeAll(): Flow<List<ExamEntity>>
    @Query("SELECT * FROM exams ORDER BY id") suspend fun getAll(): List<ExamEntity>
    @Upsert suspend fun upsert(exams: List<ExamEntity>)
    @Query("DELETE FROM exams") suspend fun deleteAll()
}
