package com.classing.wear.timetable.domain.usecase

import com.classing.wear.timetable.domain.model.Course
import com.classing.wear.timetable.domain.model.CourseSession
import com.classing.wear.timetable.domain.model.ScheduleException
import com.classing.wear.timetable.domain.model.Semester
import com.classing.wear.timetable.domain.model.TimeSlot
import com.classing.wear.timetable.domain.model.WeekParity
import com.classing.wear.timetable.domain.model.WeekRule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduleAssemblerTest {

    private val assembler = ScheduleAssembler()

    @Test
    fun cancel_exception_removes_lesson() {
        val date = LocalDate.of(2026, 3, 17)
        val semester = Semester(1, null, "春季", LocalDate.of(2026, 2, 23), LocalDate.of(2026, 7, 5), 19, true, 1)
        val slot = TimeSlot(1, null, 1, 2, "3-4", LocalTime.of(10, 0), LocalTime.of(11, 35), 1)
        val course = Course(1, null, 1, "高数", "李老师", "A201", "", "red", false, 1)
        val session = CourseSession(1, null, 1, 1, DayOfWeek.TUESDAY, 1, WeekRule(1, 19, WeekParity.ALL), 1)
        val exception = ScheduleException.Cancel(1, null, 1, 1, date, "停课", 1)

        val list = assembler.buildDayOccurrences(
            date = date,
            now = LocalDateTime.of(date, LocalTime.of(9, 0)),
            semester = semester,
            courses = listOf(course),
            sessions = listOf(session),
            slots = listOf(slot),
            exceptions = listOf(exception),
        )

        assertEquals(0, list.size)
    }
}
