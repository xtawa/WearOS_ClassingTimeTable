package com.xtawa.classingtime.screen

import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileScheduleDisplayProjectionTest {
    private val imported = listOf(
        LessonUi(
            id = "math",
            title = "Math",
            location = "A101",
            note = null,
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(9, 30),
            startWeek = 1,
            endWeek = 16,
        ),
    )

    @Test
    fun fallsBackToImportedCourseTemplateWhenCurrentWeekIsEmpty() {
        val projection = buildScheduleDisplayProjection(imported, emptyList())

        assertFalse(projection.isCurrentWeek)
        assertEquals(imported, projection.lessons)
        assertEquals(imported, projection.lessonsByDay[DayOfWeek.MONDAY])
    }

    @Test
    fun keepsCurrentWeekProjectionWhenItHasOccurrences() {
        val current = imported.map { it.copy(id = "math-current") }

        val projection = buildScheduleDisplayProjection(imported, current)

        assertTrue(projection.isCurrentWeek)
        assertEquals(current, projection.lessons)
    }
}
