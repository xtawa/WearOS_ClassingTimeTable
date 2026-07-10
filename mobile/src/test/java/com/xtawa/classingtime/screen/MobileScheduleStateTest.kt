package com.xtawa.classingtime.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class MobileScheduleStateTest {

    private val semesterStart = LocalDate.of(2026, 3, 2)
    private val baseLesson = LessonUi(
        id = "math-base",
        title = "Math",
        teacher = "Alice",
        location = "A101",
        note = null,
        dayOfWeek = DayOfWeek.MONDAY,
        startTime = LocalTime.of(8, 0),
        endTime = LocalTime.of(9, 30),
        startWeek = 1,
        endWeek = 8,
        weekParity = LessonWeekParity.ALL,
    )

    @Test
    fun removeLesson_singleOccurrence_createsCancelException() {
        val result = removeLesson(
            baseLessons = listOf(baseLesson),
            exceptions = emptyList(),
            editContext = LessonEditContext(
                lesson = baseLesson,
                anchorDate = LocalDate.of(2026, 3, 16),
                allowedScopes = setOf(LessonEditScope.SingleOccurrence),
            ),
            scope = LessonEditScope.SingleOccurrence,
            weekNumberMode = WeekNumberMode.SEMESTER,
            semesterWeekStartDate = semesterStart,
        )

        assertEquals(1, result.baseLessons.size)
        assertEquals(1, result.exceptions.size)
        assertEquals(ScheduleExceptionKind.CANCEL, result.exceptions.first().type)
    }

    @Test
    fun applyLessonEdit_fromThisWeek_splitsBaseLesson() {
        val updated = baseLesson.copy(
            title = "Advanced Math",
            location = "B202",
            startTime = LocalTime.of(10, 0),
            endTime = LocalTime.of(11, 30),
        )

        val result = applyLessonEdit(
            baseLessons = listOf(baseLesson),
            exceptions = emptyList(),
            editContext = LessonEditContext(
                lesson = baseLesson,
                anchorDate = LocalDate.of(2026, 3, 23),
                allowedScopes = setOf(LessonEditScope.FromThisWeek),
            ),
            updatedLesson = updated,
            scope = LessonEditScope.FromThisWeek,
            weekNumberMode = WeekNumberMode.SEMESTER,
            semesterWeekStartDate = semesterStart,
        )

        assertEquals(2, result.baseLessons.size)
        assertEquals(3, result.baseLessons.first { it.id == "math-base" }.endWeek)
        val split = result.baseLessons.first { it.id != "math-base" }
        assertEquals(4, split.startWeek)
        assertEquals("Advanced Math", split.title)
        assertEquals(LocalTime.of(10, 0), split.startTime)
    }

    @Test
    fun buildEffectiveOccurrences_appliesCancelRescheduleAndMakeup() {
        val exceptions = listOf(
            ScheduleExceptionUi(
                id = "cancel-1",
                lessonId = "math-base",
                type = ScheduleExceptionKind.CANCEL,
                date = LocalDate.of(2026, 3, 9),
            ),
            ScheduleExceptionUi(
                id = "reschedule-1",
                lessonId = "math-base",
                type = ScheduleExceptionKind.RESCHEDULE,
                date = LocalDate.of(2026, 3, 16),
                title = "Math",
                teacher = "Alice",
                location = "Lab",
                dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(13, 0),
                endTime = LocalTime.of(14, 30),
            ),
            ScheduleExceptionUi(
                id = "makeup-1",
                lessonId = null,
                type = ScheduleExceptionKind.MAKE_UP,
                date = LocalDate.of(2026, 3, 18),
                title = "Math Makeup",
                teacher = "Alice",
                location = "C303",
                dayOfWeek = DayOfWeek.WEDNESDAY,
                startTime = LocalTime.of(15, 0),
                endTime = LocalTime.of(16, 0),
            ),
        )

        val occurrences = buildEffectiveOccurrencesForDateRange(
            baseLessons = listOf(baseLesson),
            exceptions = exceptions,
            startDate = LocalDate.of(2026, 3, 9),
            endDate = LocalDate.of(2026, 3, 22),
            weekNumberMode = WeekNumberMode.SEMESTER,
            semesterWeekStartDate = semesterStart,
        )

        assertTrue(occurrences.none { it.date == LocalDate.of(2026, 3, 9) && it.sourceLessonId == "math-base" })
        val rescheduled = occurrences.first { it.date == LocalDate.of(2026, 3, 16) }
        assertEquals(LocalTime.of(13, 0), rescheduled.lesson.startTime)
        assertEquals(EffectiveLessonOrigin.RESCHEDULED, rescheduled.origin)
        val makeup = occurrences.first { it.date == LocalDate.of(2026, 3, 18) }
        assertEquals("Math Makeup", makeup.lesson.title)
        assertEquals(EffectiveLessonOrigin.MAKE_UP, makeup.origin)
    }

    @Test
    fun resolveNextLessonForBoard_usesDateSpecificLessonsAcrossWeekBoundary() {
        val currentWeekMonday = baseLesson
        val nextWeekMonday = baseLesson.copy(
            id = "math-next-week",
            title = "Advanced Math",
            startTime = LocalTime.of(10, 0),
            endTime = LocalTime.of(11, 30),
        )

        val nextLesson = resolveNextLessonForBoard(
            lessonsForDate = { date ->
                when (date) {
                    LocalDate.of(2026, 3, 2) -> listOf(currentWeekMonday)
                    LocalDate.of(2026, 3, 9) -> listOf(nextWeekMonday)
                    else -> emptyList()
                }
            },
            now = LocalDateTime.of(2026, 3, 6, 18, 0),
        )

        assertEquals("Advanced Math", nextLesson?.lesson?.title)
        assertEquals(LocalDateTime.of(2026, 3, 9, 10, 0), nextLesson?.startAt)
    }
}
