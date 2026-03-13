package com.classing.wear.timetable.data.sync

import com.classing.wear.timetable.domain.model.SyncMode
import java.time.LocalDate
import java.time.LocalTime

data class RemoteSchedulePayload(
    val dataVersion: Long,
    val semesters: List<RemoteSemester>,
    val timeSlots: List<RemoteTimeSlot>,
    val courses: List<RemoteCourse>,
    val sessions: List<RemoteSession>,
    val exceptions: List<RemoteException>,
)

data class RemoteSemester(
    val remoteId: String,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalWeeks: Int,
    val isActive: Boolean,
    val version: Long,
)

data class RemoteTimeSlot(
    val remoteId: String,
    val semesterRemoteId: String,
    val indexInDay: Int,
    val label: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val version: Long,
)

data class RemoteCourse(
    val remoteId: String,
    val semesterRemoteId: String,
    val name: String,
    val teacher: String,
    val classroom: String,
    val note: String,
    val colorLabel: String,
    val isFavorite: Boolean,
    val version: Long,
)

data class RemoteSession(
    val remoteId: String,
    val semesterRemoteId: String,
    val courseRemoteId: String,
    val dayOfWeek: Int,
    val timeSlotRemoteId: String,
    val startWeek: Int,
    val endWeek: Int,
    val weekParity: String,
    val version: Long,
)

data class RemoteException(
    val remoteId: String,
    val semesterRemoteId: String,
    val sessionRemoteId: String?,
    val exceptionType: String,
    val date: LocalDate,
    val reason: String,
    val courseRemoteId: String?,
    val timeSlotRemoteId: String?,
    val dayOfWeek: Int?,
    val newCourseRemoteId: String?,
    val newTimeSlotRemoteId: String?,
    val version: Long,
)

data class LocalChangeSet(
    val mode: SyncMode,
    val localVersion: Long,
)

data class RemoteSyncAck(
    val success: Boolean,
    val acceptedVersion: Long,
    val conflictCount: Int,
    val message: String,
)
