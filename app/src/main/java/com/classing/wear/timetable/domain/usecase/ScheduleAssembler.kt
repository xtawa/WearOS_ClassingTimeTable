package com.classing.wear.timetable.domain.usecase

import com.classing.wear.timetable.core.time.LessonStatusResolver
import com.classing.wear.timetable.core.time.WeekCalculator
import com.classing.wear.timetable.domain.model.Course
import com.classing.wear.timetable.domain.model.CourseSession
import com.classing.wear.timetable.domain.model.LessonStatus
import com.classing.wear.timetable.domain.model.LessonOccurrence
import com.classing.wear.timetable.domain.model.NextLessonHint
import com.classing.wear.timetable.domain.model.ScheduleException
import com.classing.wear.timetable.domain.model.Semester
import com.classing.wear.timetable.domain.model.TimeSlot
import com.classing.wear.timetable.domain.model.WeekParity
import com.classing.wear.timetable.domain.model.WeekRule
import com.classing.wear.timetable.domain.model.WeekSchedule
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.WeekFields

class ScheduleAssembler {
    fun buildDayOccurrences(
        date: LocalDate,
        now: LocalDateTime,
        semester: Semester,
        courses: List<Course>,
        sessions: List<CourseSession>,
        slots: List<TimeSlot>,
        exceptions: List<ScheduleException>,
    ): List<LessonOccurrence> {
        if (date.isBefore(semester.startDate) || date.isAfter(semester.endDate)) return emptyList()

        val weekIndex = WeekCalculator.weekIndex(semester.startDate, date)
        val courseMap = courses.associateBy { it.localId }
        val slotMap = slots.associateBy { it.localId }

        val baseList = sessions
            .filter {
                it.dayOfWeek == date.dayOfWeek && it.weekRule.contains(weekIndex)
            }
            .mapNotNull { session ->
                val course = courseMap[session.courseId] ?: return@mapNotNull null
                val slot = slotMap[session.timeSlotId] ?: return@mapNotNull null
                toOccurrence(date, now, weekIndex, course, session, slot)
            }
            .toMutableList()

        // Exception records override the base timetable for a specific date.
        val dayExceptions = exceptions.filter { it.date == date }

        dayExceptions.forEach { exception ->
            when (exception) {
                is ScheduleException.Cancel -> {
                    baseList.removeAll { it.session.localId == exception.sessionId }
                }

                is ScheduleException.Reschedule -> {
                    val idx = baseList.indexOfFirst { it.session.localId == exception.sessionId }
                    if (idx >= 0) {
                        val old = baseList[idx]
                        val newCourse = courseMap[exception.newCourseId] ?: old.course
                        val newSlot = slotMap[exception.newTimeSlotId] ?: old.timeSlot
                        baseList[idx] = toOccurrence(
                            date = date,
                            now = now,
                            weekIndex = weekIndex,
                            course = newCourse,
                            session = old.session,
                            slot = newSlot,
                        )
                    }
                }

                is ScheduleException.MakeUp -> {
                    val course = courseMap[exception.courseId] ?: return@forEach
                    val slot = slotMap[exception.timeSlotId] ?: return@forEach
                    val pseudoSession = CourseSession(
                        localId = -exception.localId,
                        remoteId = exception.remoteId,
                        semesterId = exception.semesterId,
                        courseId = exception.courseId,
                        dayOfWeek = date.dayOfWeek,
                        timeSlotId = exception.timeSlotId,
                        weekRule = WeekRule(weekIndex, weekIndex, WeekParity.ALL),
                        version = exception.version,
                    )
                    baseList += toOccurrence(date, now, weekIndex, course, pseudoSession, slot)
                }
            }
        }

        return baseList.sortedBy { it.startAt }
    }

    fun buildWeekSchedule(
        weekStart: LocalDate,
        now: LocalDateTime,
        semester: Semester,
        courses: List<Course>,
        sessions: List<CourseSession>,
        slots: List<TimeSlot>,
        exceptions: List<ScheduleException>,
    ): WeekSchedule {
        val weekIndex = if (isNaturalWeekSemester(semester)) {
            weekStart.get(WeekFields.ISO.weekOfWeekBasedYear()).coerceAtLeast(1)
        } else {
            WeekCalculator.weekIndex(semester.startDate, weekStart)
        }
        val days = (0..6).associate { offset ->
            val date = weekStart.plusDays(offset.toLong())
            date.dayOfWeek to buildDayOccurrences(
                date = date,
                now = now,
                semester = semester,
                courses = courses,
                sessions = sessions,
                slots = slots,
                exceptions = exceptions,
            )
        }

        return WeekSchedule(
            weekIndex = weekIndex,
            days = days,
        )
    }

    fun findNextLessonHint(
        now: LocalDateTime,
        semester: Semester,
        courses: List<Course>,
        sessions: List<CourseSession>,
        slots: List<TimeSlot>,
        exceptions: List<ScheduleException>,
    ): NextLessonHint {
        val candidates = buildList {
            for (i in 0..6) {
                val date = now.toLocalDate().plusDays(i.toLong())
                addAll(
                    buildDayOccurrences(
                        date = date,
                        now = now,
                        semester = semester,
                        courses = courses,
                        sessions = sessions,
                        slots = slots,
                        exceptions = exceptions,
                    ),
                )
            }
        }

        val target = candidates.firstOrNull { it.startAt.isAfter(now) || it.status == LessonStatus.IN_PROGRESS }
        val countdown = target?.let {
            val start = if (it.startAt.isBefore(now)) now else it.startAt
            Duration.between(now, start)
        }

        return NextLessonHint(target, countdown)
    }

    private fun toOccurrence(
        date: LocalDate,
        now: LocalDateTime,
        weekIndex: Int,
        course: Course,
        session: CourseSession,
        slot: TimeSlot,
    ): LessonOccurrence {
        val startAt = LocalDateTime.of(date, slot.startTime)
        val endAt = LocalDateTime.of(date, slot.endTime)
        return LessonOccurrence(
            course = course,
            session = session,
            timeSlot = slot,
            date = date,
            weekIndex = weekIndex,
            status = LessonStatusResolver.resolve(now, startAt, endAt),
            startAt = startAt,
            endAt = endAt,
        )
    }

    private fun isNaturalWeekSemester(semester: Semester): Boolean {
        return semester.remoteId == "mobile-sync-semester-natural"
    }
}
