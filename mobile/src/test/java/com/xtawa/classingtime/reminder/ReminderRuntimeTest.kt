package com.xtawa.classingtime.reminder

import com.xtawa.classingtime.data.PersistedLesson
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ReminderRuntimeTest {
    @Test
    fun findNextAlarm_returns_nearest_future_trigger() {
        val now = LocalDateTime.of(2026, 4, 13, 7, 30) // Monday
        val lessons = listOf(
            PersistedLesson(
                id = "a",
                title = "Math",
                teacher = null,
                location = null,
                note = null,
                dayOfWeek = 1,
                startMinute = 8 * 60,
                endMinute = 9 * 60,
                startWeek = 1,
                endWeek = 30,
                weekParity = "ALL",
            ),
            PersistedLesson(
                id = "b",
                title = "History",
                teacher = null,
                location = null,
                note = null,
                dayOfWeek = 1,
                startMinute = 10 * 60,
                endMinute = 11 * 60,
                startWeek = 1,
                endWeek = 30,
                weekParity = "ALL",
            ),
        )

        val next = ReminderRuntime.findNextAlarm(lessons = lessons, now = now, leadMinutes = 15)

        assertNotNull(next)
        assertEquals("a", next?.lessonId)
    }
}
