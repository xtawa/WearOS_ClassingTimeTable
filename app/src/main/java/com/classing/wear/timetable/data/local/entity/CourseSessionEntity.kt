package com.classing.wear.timetable.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "course_sessions",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["localId"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["localId"],
            childColumns = ["semesterId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TimeSlotEntity::class,
            parentColumns = ["localId"],
            childColumns = ["timeSlotId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("courseId"), Index("semesterId"), Index("timeSlotId")],
)
data class CourseSessionEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: String?,
    val semesterId: Long,
    val courseId: Long,
    val dayOfWeek: Int,
    val timeSlotId: Long,
    val startWeek: Int,
    val endWeek: Int,
    val weekParity: String,
    val version: Long,
)
