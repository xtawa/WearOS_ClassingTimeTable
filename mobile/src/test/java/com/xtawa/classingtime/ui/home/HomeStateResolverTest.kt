package com.xtawa.classingtime.ui.home

import com.xtawa.classingtime.screen.LessonUi
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeStateResolverTest {
    private val mondayLessons = listOf(
        lesson("physics", "Physics", 14, 10, 14, 55),
        lesson("english", "English", 15, 5, 15, 50),
    )
    private val schedule = mapOf(DayOfWeek.MONDAY to mondayLessons)

    @Test
    fun beforeFirstClass_resolvesUpcoming() {
        val state = resolveHomeUiState(
            now = LocalDateTime.of(2026, 8, 17, 13, 57),
            lessonsByDay = schedule,
            hasImportedSchedule = true,
        )

        assertEquals(HomePhase.Upcoming, state.phase)
        assertEquals("Physics", state.primaryCourse?.title)
        assertEquals(13L, state.countdownMinutes)
    }

    @Test
    fun duringClass_resolvesInClassAndProgress() {
        val state = resolveHomeUiState(
            now = LocalDateTime.of(2026, 8, 17, 14, 23),
            lessonsByDay = schedule,
            hasImportedSchedule = true,
        )

        assertEquals(HomePhase.InClass, state.phase)
        assertEquals("Physics", state.primaryCourse?.title)
        assertEquals(32L, state.remainingMinutes)
        assertEquals("English", state.nextCourse?.title)
    }

    @Test
    fun betweenClasses_resolvesBreakWithNextCourseIdentity() {
        val state = resolveHomeUiState(
            now = LocalDateTime.of(2026, 8, 17, 14, 56),
            lessonsByDay = schedule,
            hasImportedSchedule = true,
        )

        assertEquals(HomePhase.Break, state.phase)
        assertEquals("English", state.primaryCourse?.title)
        assertEquals(9L, state.breakMinutes)
    }

    @Test
    fun afterLastClass_resolvesFinished() {
        val state = resolveHomeUiState(
            now = LocalDateTime.of(2026, 8, 17, 17, 0),
            lessonsByDay = schedule,
            hasImportedSchedule = true,
        )

        assertEquals(HomePhase.Finished, state.phase)
        assertNull(state.primaryCourse)
    }

    @Test
    fun emptyDay_resolvesNoClassesAndKeepsNextAcademicAnchor() {
        val state = resolveHomeUiState(
            now = LocalDateTime.of(2026, 8, 18, 10, 0),
            lessonsByDay = schedule,
            hasImportedSchedule = true,
        )

        assertEquals(HomePhase.NoClasses, state.phase)
        assertEquals("Physics", state.nextCourse?.title)
    }

    private fun lesson(
        id: String,
        title: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
    ) = LessonUi(
        id = id,
        title = title,
        location = "A302",
        note = null,
        dayOfWeek = DayOfWeek.MONDAY,
        startTime = LocalTime.of(startHour, startMinute),
        endTime = LocalTime.of(endHour, endMinute),
    )
}
