package com.classing.wear.timetable.worker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderCheckLogicTest {
    @Test
    fun dueLessons_matchesWithinReminderWindow() {
        val now = LocalDateTime.of(2026, 3, 16, 7, 50)
        val lesson = SyncedLesson(
            id = "c1",
            title = "Algorithms",
            dayOfWeek = now.dayOfWeek.value,
            startTime = LocalTime.of(8, 0),
            location = "A101",
        )

        val due = ReminderCheckLogic.dueLessons(listOf(lesson), now, emptySet())

        assertEquals(1, due.size)
    }

    @Test
    fun dueLessons_skipsAlreadyNotified() {
        val now = LocalDateTime.of(2026, 3, 16, 7, 50)
        val lesson = SyncedLesson(
            id = "c1",
            title = "Algorithms",
            dayOfWeek = now.dayOfWeek.value,
            startTime = LocalTime.of(8, 0),
            location = "A101",
        )
        val key = ReminderCheckLogic.reminderKey(now.toLocalDate(), lesson)

        val due = ReminderCheckLogic.dueLessons(listOf(lesson), now, setOf(key))

        assertEquals(0, due.size)
    }

    @Test
    fun reminderKey_resetsAcrossDays() {
        val lesson = SyncedLesson(
            id = "c1",
            title = "Algorithms",
            dayOfWeek = 1,
            startTime = LocalTime.of(8, 0),
            location = null,
        )

        val keyToday = ReminderCheckLogic.reminderKey(LocalDate.of(2026, 3, 16), lesson)
        val keyTomorrow = ReminderCheckLogic.reminderKey(LocalDate.of(2026, 3, 17), lesson)

        assertEquals(false, keyToday == keyTomorrow)
    }

    @Test
    fun dueLessons_matchesPreviousDayWindowForMidnightLesson() {
        val now = LocalDateTime.of(2026, 3, 16, 23, 50)
        val lesson = SyncedLesson(
            id = "c2",
            title = "Astronomy",
            dayOfWeek = now.plusDays(1).dayOfWeek.value,
            startTime = LocalTime.of(0, 5),
            location = "Lab",
        )

        val due = ReminderCheckLogic.dueLessons(listOf(lesson), now, emptySet())

        assertEquals(1, due.size)
    }

    @Test
    fun dueLessons_expiresAtWindowEndForMidnightLesson() {
        val now = LocalDateTime.of(2026, 3, 17, 0, 0)
        val lesson = SyncedLesson(
            id = "c2",
            title = "Astronomy",
            dayOfWeek = now.dayOfWeek.value,
            startTime = LocalTime.of(0, 5),
            location = "Lab",
        )

        val due = ReminderCheckLogic.dueLessons(listOf(lesson), now, emptySet())

        assertEquals(0, due.size)
    }
}
