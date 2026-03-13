package com.classing.wear.timetable.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "semesters")
data class SemesterEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: String?,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalWeeks: Int,
    val isActive: Boolean,
    val version: Long,
)
