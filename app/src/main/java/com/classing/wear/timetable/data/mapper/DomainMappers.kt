package com.classing.wear.timetable.data.mapper

import com.classing.wear.timetable.data.local.entity.CourseEntity
import com.classing.wear.timetable.data.local.entity.CourseSessionEntity
import com.classing.wear.timetable.data.local.entity.ScheduleExceptionEntity
import com.classing.wear.timetable.data.local.entity.SemesterEntity
import com.classing.wear.timetable.data.local.entity.TimeSlotEntity
import com.classing.wear.timetable.domain.model.Course
import com.classing.wear.timetable.domain.model.CourseSession
import com.classing.wear.timetable.domain.model.ScheduleException
import com.classing.wear.timetable.domain.model.Semester
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

fun CourseSessionEntity.asDomainOrNull(): CourseSession? {
    val safeDayOfWeek = runCatching { DayOfWeek.of(dayOfWeek) }.getOrNull() ?: return null
    val safeParity = runCatching { WeekParity.valueOf(weekParity) }.getOrNull() ?: return null
    return CourseSession(
        localId = localId,
        remoteId = remoteId,
        semesterId = semesterId,
        courseId = courseId,
        dayOfWeek = safeDayOfWeek,
        timeSlotId = timeSlotId,
        weekRule = WeekRule(
            startWeek = startWeek,
            endWeek = endWeek,
            parity = safeParity,
        ),
        version = version,
    )
}

fun ScheduleExceptionEntity.asDomainOrNull(): ScheduleException? {
    return when (exceptionType) {
        "CANCEL" -> sessionId?.let {
            ScheduleException.Cancel(
                localId = localId,
                remoteId = remoteId,
                semesterId = semesterId,
                sessionId = it,
                date = date,
                reason = reason,
                version = version,
            )
        }

        "MAKE_UP" -> {
            val safeCourseId = courseId ?: return null
            val safeTimeSlotId = timeSlotId ?: return null
            ScheduleException.MakeUp(
                localId = localId,
                remoteId = remoteId,
                semesterId = semesterId,
                sessionId = sessionId,
                courseId = safeCourseId,
                timeSlotId = safeTimeSlotId,
                dayOfWeek = dayOfWeek ?: date.dayOfWeek.value,
                date = date,
                reason = reason,
                version = version,
            )
        }

        "RESCHEDULE" -> {
            val safeSessionId = sessionId ?: return null
            val safeNewCourseId = newCourseId ?: return null
            val safeNewTimeSlotId = newTimeSlotId ?: return null
            ScheduleException.Reschedule(
                localId = localId,
                remoteId = remoteId,
                semesterId = semesterId,
                sessionId = safeSessionId,
                newCourseId = safeNewCourseId,
                newTimeSlotId = safeNewTimeSlotId,
                date = date,
                reason = reason,
                version = version,
            )
        }

        else -> null
    }
}

