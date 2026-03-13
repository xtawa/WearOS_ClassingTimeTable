package com.classing.shared.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class Semester(
    val id: Long,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val zoneId: ZoneId,
)

data class TimeSlot(
    val id: Long,
    val label: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
)

enum class WeekParity { ALL, ODD, EVEN }

data class WeekRule(
    val startWeek: Int,
    val endWeek: Int,
    val parity: WeekParity,
)

data class Course(
    val id: Long,
    val semesterId: Long,
    val title: String,
    val teacher: String?,
    val location: String?,
    val description: String?,
    val dayOfWeek: DayOfWeek,
    val timeSlotId: Long,
    val weekRule: WeekRule,
    val reminderEnabled: Boolean,
)

enum class ExceptionType { RESCHEDULE, CANCEL, MAKEUP, HOLIDAY }

data class ScheduleException(
    val id: Long,
    val courseId: Long,
    val date: LocalDate,
    val type: ExceptionType,
    val overrideStart: LocalDateTime? = null,
    val overrideEnd: LocalDateTime? = null,
    val note: String? = null,
)

data class ReminderRule(
    val leadMinutes: Int,
    val enabled: Boolean,
)

data class ReminderInstance(
    val id: String,
    val courseId: Long,
    val triggerAt: Instant,
    val title: String,
    val body: String,
)

data class ImportMetadata(
    val sourceType: String,
    val rawPayload: String,
    val importedAt: Instant,
)

data class SyncMetadata(
    val entityType: String,
    val entityId: Long,
    val version: Long,
    val updatedAt: Instant,
    val deviceId: String,
)
