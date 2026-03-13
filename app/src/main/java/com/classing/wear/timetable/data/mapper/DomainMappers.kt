package com.classing.wear.timetable.data.mapper

import com.classing.wear.timetable.data.local.entity.CourseEntity
import com.classing.wear.timetable.data.local.entity.CourseSessionEntity
import com.classing.wear.timetable.data.local.entity.ScheduleExceptionEntity
import com.classing.wear.timetable.data.local.entity.SemesterEntity
import com.classing.wear.timetable.data.local.entity.SyncMetadataEntity
import com.classing.wear.timetable.data.local.entity.TimeSlotEntity
import com.classing.wear.timetable.domain.model.Course
import com.classing.wear.timetable.domain.model.CourseSession
import com.classing.wear.timetable.domain.model.ScheduleException
import com.classing.wear.timetable.domain.model.Semester
import com.classing.wear.timetable.domain.model.SyncMetadata
import com.classing.wear.timetable.domain.model.TimeSlot
import com.classing.wear.timetable.domain.model.WeekParity
import com.classing.wear.timetable.domain.model.WeekRule
import java.time.DayOfWeek

fun SemesterEntity.asDomain(): Semester = Semester(
    localId = localId,
    remoteId = remoteId,
    name = name,
    startDate = startDate,
    endDate = endDate,
    totalWeeks = totalWeeks,
    isActive = isActive,
    version = version,
)

fun TimeSlotEntity.asDomain(): TimeSlot = TimeSlot(
    localId = localId,
    remoteId = remoteId,
    semesterId = semesterId,
    indexInDay = indexInDay,
    label = label,
    startTime = startTime,
    endTime = endTime,
    version = version,
)

fun CourseEntity.asDomain(): Course = Course(
    localId = localId,
    remoteId = remoteId,
    semesterId = semesterId,
    name = name,
    teacher = teacher,
    classroom = classroom,
    note = note,
    colorLabel = colorLabel,
    isFavorite = isFavorite,
    version = version,
)

fun CourseSessionEntity.asDomain(): CourseSession = CourseSession(
    localId = localId,
    remoteId = remoteId,
    semesterId = semesterId,
    courseId = courseId,
    dayOfWeek = DayOfWeek.of(dayOfWeek),
    timeSlotId = timeSlotId,
    weekRule = WeekRule(
        startWeek = startWeek,
        endWeek = endWeek,
        parity = WeekParity.valueOf(weekParity),
    ),
    version = version,
)

fun ScheduleExceptionEntity.asDomain(): ScheduleException {
    return when (exceptionType) {
        "CANCEL" -> ScheduleException.Cancel(
            localId = localId,
            remoteId = remoteId,
            semesterId = semesterId,
            sessionId = requireNotNull(sessionId),
            date = date,
            reason = reason,
            version = version,
        )

        "MAKE_UP" -> ScheduleException.MakeUp(
            localId = localId,
            remoteId = remoteId,
            semesterId = semesterId,
            sessionId = sessionId,
            courseId = requireNotNull(courseId),
            timeSlotId = requireNotNull(timeSlotId),
            dayOfWeek = dayOfWeek ?: date.dayOfWeek.value,
            date = date,
            reason = reason,
            version = version,
        )

        else -> ScheduleException.Reschedule(
            localId = localId,
            remoteId = remoteId,
            semesterId = semesterId,
            sessionId = requireNotNull(sessionId),
            newCourseId = requireNotNull(newCourseId),
            newTimeSlotId = requireNotNull(newTimeSlotId),
            date = date,
            reason = reason,
            version = version,
        )
    }
}

fun SyncMetadataEntity.asDomain(): SyncMetadata = SyncMetadata(
    lastFullSyncAt = lastFullSyncAt,
    lastDeltaSyncAt = lastDeltaSyncAt,
    lastSuccessAt = lastSuccessAt,
    lastErrorMessage = lastErrorMessage,
    dataVersion = dataVersion,
    pendingChanges = pendingChanges,
)
