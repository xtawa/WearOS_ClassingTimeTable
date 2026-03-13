package com.classing.wear.timetable.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalTime

@Entity(
    tableName = "time_slots",
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["localId"],
            childColumns = ["semesterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("semesterId")],
)
data class TimeSlotEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: String?,
    val semesterId: Long,
    val indexInDay: Int,
    val label: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val version: Long,
)
