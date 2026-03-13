package com.classing.wear.timetable.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.classing.wear.timetable.data.local.entity.ScheduleExceptionEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ScheduleExceptionDao {
    @Query("SELECT * FROM schedule_exceptions WHERE semesterId = :semesterId")
    fun observeBySemester(semesterId: Long): Flow<List<ScheduleExceptionEntity>>

    @Query("SELECT * FROM schedule_exceptions WHERE semesterId = :semesterId AND date BETWEEN :startDate AND :endDate")
    suspend fun getByDateRange(
        semesterId: Long,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<ScheduleExceptionEntity>

    @Query("SELECT * FROM schedule_exceptions WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): ScheduleExceptionEntity?

    @Upsert
    suspend fun upsert(exception: ScheduleExceptionEntity): Long

    @Upsert
    suspend fun upsertAll(exceptions: List<ScheduleExceptionEntity>)

    @Query("DELETE FROM schedule_exceptions WHERE semesterId = :semesterId")
    suspend fun deleteBySemester(semesterId: Long)
}
