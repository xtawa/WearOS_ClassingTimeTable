package com.classing.shared.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleProjectorTest {
    private val projector = ScheduleProjector()

    @Test
    fun project_includesRescheduleWhoseOriginalDateIsOutsideWindow() {
        val monday = LocalDate.of(2026, 2, 23)
        val target = monday.plusWeeks(1).plusDays(2)
        val course = CourseRule("math", "Math", DayOfWeek.MONDAY,
            LocalTime.of(8, 0), LocalTime.of(9, 0), 1, 4)
        val input = ScheduleInput(monday, courses = listOf(course), exceptions = listOf(
            ScheduleExceptionRule.Reschedule("move", "math", monday, target,
                LocalTime.of(10, 0), LocalTime.of(11, 0)),
        ))
        assertEquals(listOf("move"), projector.project(input, target, target).map { it.id })
        assertEquals(emptyList(), projector.project(input, monday, monday))
    }

    @Test
    fun project_includesRescheduleFromFutureAndCancellationWins() {
        val monday = LocalDate.of(2026, 2, 23)
        val course = CourseRule("math", "Math", DayOfWeek.MONDAY,
            LocalTime.of(8, 0), LocalTime.of(9, 0), 1, 4)
        val moved = ScheduleExceptionRule.Reschedule("move", "math", monday.plusWeeks(1),
            monday.plusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0))
        val input = ScheduleInput(monday, courses = listOf(course), exceptions = listOf(moved))
        assertEquals(listOf("move"), projector.project(input, monday.plusDays(1), monday.plusDays(1)).map { it.id })
        assertEquals(emptyList(), projector.project(input.copy(exceptions = listOf(moved,
            ScheduleExceptionRule.Cancel("cancel", "math", moved.date))), moved.newDate, moved.newDate))
    }

    @Test
    fun project_appliesWeekParityAndDateRange() {
        val input = ScheduleInput(
            semesterWeekStartDate = LocalDate.of(2026, 2, 23),
            courses = listOf(
                CourseRule(
                    id = "math",
                    title = "Math",
                    dayOfWeek = DayOfWeek.MONDAY,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(8, 45),
                    startWeek = 1,
                    endWeek = 4,
                    weekParity = WeekParity.ODD,
                ),
            ),
        )

        val occurrences = projector.project(
            input = input,
            startDate = LocalDate.of(2026, 2, 23),
            endDate = LocalDate.of(2026, 3, 16),
        )

        assertEquals(
            listOf(LocalDate.of(2026, 2, 23), LocalDate.of(2026, 3, 9)),
            occurrences.map { it.date },
        )
        assertEquals(listOf(1, 3), occurrences.map { it.weekIndex })
    }

    @Test
    fun project_appliesCancelRescheduleAndMakeUpRules() {
        val regularDate = LocalDate.of(2026, 2, 23)
        val input = ScheduleInput(
            semesterWeekStartDate = regularDate,
            courses = listOf(
                CourseRule(
                    id = "math",
                    title = "Math",
                    dayOfWeek = DayOfWeek.MONDAY,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(8, 45),
                    startWeek = 1,
                    endWeek = 2,
                    location = "A101",
                ),
                CourseRule(
                    id = "physics",
                    title = "Physics",
                    dayOfWeek = DayOfWeek.TUESDAY,
                    startTime = LocalTime.of(9, 0),
                    endTime = LocalTime.of(9, 45),
                    startWeek = 1,
                    endWeek = 2,
                ),
            ),
            exceptions = listOf(
                ScheduleExceptionRule.Cancel(
                    id = "cancel-physics",
                    courseId = "physics",
                    date = LocalDate.of(2026, 2, 24),
                ),
                ScheduleExceptionRule.Reschedule(
                    id = "move-math",
                    courseId = "math",
                    date = regularDate,
                    newDate = LocalDate.of(2026, 2, 25),
                    newStartTime = LocalTime.of(10, 0),
                    newEndTime = LocalTime.of(10, 45),
                    newLocation = "B202",
                ),
                ScheduleExceptionRule.MakeUp(
                    id = "makeup-chem",
                    date = LocalDate.of(2026, 2, 26),
                    title = "Chemistry",
                    startTime = LocalTime.of(11, 0),
                    endTime = LocalTime.of(11, 45),
                ),
            ),
        )

        val occurrences = projector.project(
            input = input,
            startDate = LocalDate.of(2026, 2, 23),
            endDate = LocalDate.of(2026, 2, 26),
        )

        assertEquals(listOf("Math", "Chemistry"), occurrences.map { it.title })
        assertEquals(listOf(OccurrenceSource.RESCHEDULED, OccurrenceSource.MAKE_UP), occurrences.map { it.source })
        assertEquals(LocalDate.of(2026, 2, 25), occurrences.first().date)
        assertEquals("B202", occurrences.first().location)
    }

    @Test
    fun weekIndex_usesConfiguredWeekStartDay() {
        val sundayBasedWeek = projector.weekIndexFor(
            date = LocalDate.of(2026, 3, 1),
            semesterWeekStartDate = LocalDate.of(2026, 2, 23),
            weekStartDay = DayOfWeek.SUNDAY,
        )
        val mondayBasedWeek = projector.weekIndexFor(
            date = LocalDate.of(2026, 3, 1),
            semesterWeekStartDate = LocalDate.of(2026, 2, 23),
            weekStartDay = DayOfWeek.MONDAY,
        )

        assertEquals(2, sundayBasedWeek)
        assertEquals(1, mondayBasedWeek)
    }
}
