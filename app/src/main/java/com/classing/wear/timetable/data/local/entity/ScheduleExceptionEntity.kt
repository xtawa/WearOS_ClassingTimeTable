package com.classing.wear.timetable.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "schedule_exceptions",
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["localId"],
            childColumns = ["semesterId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CourseSessionEntity::class,
            parentColumns = ["localId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("semesterId"), Index("sessionId"), Index("date")],
)
data class ScheduleExceptionEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: String?,
    val semesterId: Long,
    val sessionId: Long?,
    val exceptionType: String,
    val date: LocalDate,
    val reason: String,
    val courseId: Long?,
    val timeSlotId: Long?,
    val dayOfWeek: Int?,
    val newCourseId: Long?,
    val newTimeSlotId: Long?,
    val version: Long,
)
