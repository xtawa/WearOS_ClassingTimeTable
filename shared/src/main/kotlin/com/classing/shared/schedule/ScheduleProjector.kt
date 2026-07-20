package com.classing.shared.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class ScheduleInput(
    val semesterWeekStartDate: LocalDate,
    val weekStartDay: DayOfWeek = DayOfWeek.MONDAY,
    val courses: List<CourseRule>,
    val exceptions: List<ScheduleExceptionRule> = emptyList(),
)

data class CourseRule(
    val id: String,
    val title: String,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val startWeek: Int,
    val endWeek: Int,
    val weekParity: WeekParity = WeekParity.ALL,
    val teacher: String? = null,
    val location: String? = null,
    val note: String? = null,
)

enum class WeekParity {
    ALL,
    ODD,
    EVEN;

    fun matches(weekIndex: Int): Boolean {
        return when (this) {
            ALL -> true
            ODD -> weekIndex % 2 == 1
            EVEN -> weekIndex % 2 == 0
        }
    }
}

sealed interface ScheduleExceptionRule {
    val id: String
    val date: LocalDate

    data class Cancel(
        override val id: String,
        val courseId: String,
        override val date: LocalDate,
    ) : ScheduleExceptionRule

    data class Reschedule(
        override val id: String,
        val courseId: String,
        override val date: LocalDate,
        val newDate: LocalDate = date,
        val newStartTime: LocalTime,
        val newEndTime: LocalTime,
        val newTeacher: String? = null,
        val newLocation: String? = null,
        val newNote: String? = null,
    ) : ScheduleExceptionRule

    data class MakeUp(
        override val id: String,
        override val date: LocalDate,
        val title: String,
        val startTime: LocalTime,
        val endTime: LocalTime,
        val teacher: String? = null,
        val location: String? = null,
        val note: String? = null,
    ) : ScheduleExceptionRule
}

data class CourseOccurrence(
    val id: String,
    val sourceCourseId: String?,
    val sourceExceptionId: String?,
    val date: LocalDate,
    val weekIndex: Int,
    val title: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val teacher: String?,
    val location: String?,
    val note: String?,
    val source: OccurrenceSource,
)

enum class OccurrenceSource {
    REGULAR,
    RESCHEDULED,
    MAKE_UP,
}

class ScheduleProjector {
    fun project(
        input: ScheduleInput,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<CourseOccurrence> {
        require(!endDate.isBefore(startDate)) { "endDate must not be before startDate" }

        val cancellations = input.exceptions
            .filterIsInstance<ScheduleExceptionRule.Cancel>()
            .groupBy { it.date to it.courseId }
        val reschedules = input.exceptions
            .filterIsInstance<ScheduleExceptionRule.Reschedule>()
            .groupBy { it.date to it.courseId }
        val makeUps = input.exceptions.filterIsInstance<ScheduleExceptionRule.MakeUp>()

        val regular = generateSequence(startDate) { date ->
            date.plusDays(1).takeUnless { it.isAfter(endDate) }
        }.flatMap { date ->
            val weekIndex = weekIndexFor(date, input.semesterWeekStartDate, input.weekStartDay)
            input.courses.asSequence()
                .filter { course -> course.dayOfWeek == date.dayOfWeek }
                .filter { course -> weekIndex in course.startWeek..course.endWeek }
                .filter { course -> course.weekParity.matches(weekIndex) }
                .filter { course -> cancellations[date to course.id].isNullOrEmpty() }
                .flatMap { course ->
                    val moved = reschedules[date to course.id].orEmpty()
                    if (moved.isNotEmpty()) {
                        moved.asSequence().map { exception ->
                            CourseOccurrence(
                                id = exception.id,
                                sourceCourseId = course.id,
                                sourceExceptionId = exception.id,
                                date = exception.newDate,
                                weekIndex = weekIndexFor(exception.newDate, input.semesterWeekStartDate, input.weekStartDay),
                                title = course.title,
                                startTime = exception.newStartTime,
                                endTime = exception.newEndTime,
                                teacher = exception.newTeacher ?: course.teacher,
                                location = exception.newLocation ?: course.location,
                                note = exception.newNote ?: course.note,
                                source = OccurrenceSource.RESCHEDULED,
                            )
                        }
                    } else {
                        sequenceOf(
                            CourseOccurrence(
                                id = regularOccurrenceId(course.id, date),
                                sourceCourseId = course.id,
                                sourceExceptionId = null,
                                date = date,
                                weekIndex = weekIndex,
                                title = course.title,
                                startTime = course.startTime,
                                endTime = course.endTime,
                                teacher = course.teacher,
                                location = course.location,
                                note = course.note,
                                source = OccurrenceSource.REGULAR,
                            ),
                        )
                    }
                }
        }.toList()

        val makeUpOccurrences = makeUps
            .filter { it.date in startDate..endDate }
            .map { exception ->
                CourseOccurrence(
                    id = exception.id,
                    sourceCourseId = null,
                    sourceExceptionId = exception.id,
                    date = exception.date,
                    weekIndex = weekIndexFor(exception.date, input.semesterWeekStartDate, input.weekStartDay),
                    title = exception.title,
                    startTime = exception.startTime,
                    endTime = exception.endTime,
                    teacher = exception.teacher,
                    location = exception.location,
                    note = exception.note,
                    source = OccurrenceSource.MAKE_UP,
                )
            }

        return (regular + makeUpOccurrences)
            .filter { it.date in startDate..endDate }
            .sortedWith(compareBy<CourseOccurrence> { it.date }.thenBy { it.startTime }.thenBy { it.title })
    }

    fun weekIndexFor(date: LocalDate, semesterWeekStartDate: LocalDate, weekStartDay: DayOfWeek): Int {
        val semesterWeekStart = alignToWeekStart(semesterWeekStartDate, weekStartDay)
        val dateWeekStart = alignToWeekStart(date, weekStartDay)
        return ((dateWeekStart.toEpochDay() - semesterWeekStart.toEpochDay()) / 7L).toInt() + 1
    }

    private fun alignToWeekStart(date: LocalDate, weekStartDay: DayOfWeek): LocalDate {
        val diff = Math.floorMod(date.dayOfWeek.value - weekStartDay.value, 7)
        return date.minusDays(diff.toLong())
    }

    private fun regularOccurrenceId(courseId: String, date: LocalDate): String {
        return "$courseId@$date"
    }
}
