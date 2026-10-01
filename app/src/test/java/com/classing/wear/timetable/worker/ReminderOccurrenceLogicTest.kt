package com.classing.wear.timetable.worker

import com.classing.wear.timetable.domain.model.Course
import com.classing.wear.timetable.domain.model.CourseSession
import com.classing.wear.timetable.domain.model.LessonOccurrence
import com.classing.wear.timetable.domain.model.LessonStatus
import com.classing.wear.timetable.domain.model.TimeSlot
import com.classing.wear.timetable.domain.model.WeekRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class ReminderOccurrenceLogicTest {

    private fun occurrence(
        id: String,
        date: LocalDate,
        start: LocalTime,
        title: String = "Algorithms",
        location: String? = "A101",
    ) = ReminderOccurrence(id = id, title = title, date = date, startTime = start, location = location)

    @Test
    fun dueOccurrences_matchesWithinWindowAndSkipsNotified() {
        val now = LocalDateTime.of(2026, 3, 16, 7, 50)
        val lesson = occurrence("s1", now.toLocalDate(), LocalTime.of(8, 0))

        assertEquals(1, ReminderCheckLogic.dueOccurrences(listOf(lesson), now, emptySet()).size)
        val key = ReminderCheckLogic.reminderKey(lesson)
        assertEquals("2026-03-16:s1:480", key)
        assertEquals(0, ReminderCheckLogic.dueOccurrences(listOf(lesson), now, setOf(key)).size)
    }

    @Test
    fun dueOccurrences_onlyFiresForTheOccurrenceDate() {
        // Same weekday template, but the projection says it is taught next week, not today.
        val now = LocalDateTime.of(2026, 3, 16, 7, 50)
        val nextWeek = occurrence("s1", now.toLocalDate().plusWeeks(1), LocalTime.of(8, 0))

        assertEquals(0, ReminderCheckLogic.dueOccurrences(listOf(nextWeek), now, emptySet()).size)
    }

    @Test
    fun dueOccurrences_handlesMidnightLessonAcrossDayBoundary() {
        val now = LocalDateTime.of(2026, 3, 16, 23, 50)
        val lesson = occurrence("s2", LocalDate.of(2026, 3, 17), LocalTime.of(0, 5))

        assertEquals(1, ReminderCheckLogic.dueOccurrences(listOf(lesson), now, emptySet()).size)
        assertEquals(0, ReminderCheckLogic.dueOccurrences(listOf(lesson), now.plusMinutes(10), emptySet()).size)
    }

    @Test
    fun nextAlarm_picksEarliestFutureTriggerAndSkipsPast() {
        val now = LocalDateTime.of(2026, 3, 16, 9, 0)
        val past = occurrence("past", now.toLocalDate(), LocalTime.of(8, 0))
        val soon = occurrence("soon", now.toLocalDate(), LocalTime.of(10, 0))
        val later = occurrence("later", now.toLocalDate().plusDays(1), LocalTime.of(8, 0))

        val alarm = ReminderCheckLogic.nextAlarm(listOf(later, past, soon), now, ZoneOffset.UTC)

        assertNotNull(alarm)
        assertEquals("soon", alarm!!.lessonId)
        assertEquals(600, alarm.startMinute)
        assertEquals("2026-03-16:soon:600", alarm.reminderKey)
        val expectedTrigger = LocalDateTime.of(2026, 3, 16, 9, 45).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(expectedTrigger, alarm.triggerAtMillis)
    }

    @Test
    fun resolveDirectReminder_requiresKeyStillPresentAndLessonNotStarted() {
        val now = LocalDateTime.of(2026, 3, 16, 7, 45)
        val lesson = occurrence("s1", now.toLocalDate(), LocalTime.of(8, 0))
        val key = ReminderCheckLogic.reminderKey(lesson)

        // Armed alarm still matches the projection -> honoured with the DB copy.
        assertEquals(lesson, ReminderCheckLogic.resolveDirectReminder(listOf(lesson), key, now))
        // Lesson cancelled/rescheduled after arming: the key is gone -> stale alarm ignored.
        assertNull(ReminderCheckLogic.resolveDirectReminder(emptyList(), key, now))
        val moved = occurrence("s1", now.toLocalDate(), LocalTime.of(10, 0))
        assertNull(ReminderCheckLogic.resolveDirectReminder(listOf(moved), key, now))
        // Alarm delivered late, lesson already started -> nothing to remind.
        assertNull(ReminderCheckLogic.resolveDirectReminder(listOf(lesson), key, LocalDateTime.of(2026, 3, 16, 8, 0)))
        assertNull(ReminderCheckLogic.resolveDirectReminder(listOf(lesson), "", now))
    }

    @Test
    fun nextAlarm_returnsNullWhenNothingAhead() {
        val now = LocalDateTime.of(2026, 3, 16, 9, 0)
        val past = occurrence("past", now.toLocalDate(), LocalTime.of(8, 0))

        assertNull(ReminderCheckLogic.nextAlarm(listOf(past), now, ZoneOffset.UTC))
        assertNull(ReminderCheckLogic.nextAlarm(emptyList(), now, ZoneOffset.UTC))
    }

    @Test
    fun fromOccurrences_usesSessionRemoteIdAndDedupes() {
        val date = LocalDate.of(2026, 3, 16)
        val course = Course(
            localId = 10,
            remoteId = "mobile-course-abc",
            semesterId = 1,
            name = "Algorithms",
            teacher = "T",
            classroom = "",
            note = "",
            colorLabel = "teal",
            isFavorite = false,
            version = 1,
        )
        val slot = TimeSlot(
            localId = 5,
            remoteId = "slot",
            semesterId = 1,
            indexInDay = 1,
            label = "1",
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(9, 35),
            version = 1,
        )
        val withRemote = CourseSession(
            localId = 7,
            remoteId = "mobile-session-abc",
            semesterId = 1,
            courseId = 10,
            dayOfWeek = DayOfWeek.MONDAY,
            timeSlotId = 5,
            weekRule = WeekRule(1, 20),
            version = 1,
        )
        val localOnly = withRemote.copy(localId = 8, remoteId = null)
        fun lesson(session: CourseSession) = LessonOccurrence(
            course = course,
            session = session,
            timeSlot = slot,
            date = date,
            weekIndex = 4,
            status = LessonStatus.NOT_STARTED,
            startAt = LocalDateTime.of(date, slot.startTime),
            endAt = LocalDateTime.of(date, slot.endTime),
        )

        val mapped = ReminderCheckLogic.fromOccurrences(listOf(lesson(withRemote), lesson(withRemote), lesson(localOnly)))

        assertEquals(2, mapped.size)
        assertEquals("mobile-session-abc", mapped[0].id)
        assertNull(mapped[0].location) // blank classroom is reported as "no location"
        assertEquals("session-8", mapped[1].id)
        assertEquals(LocalTime.of(8, 0), mapped[1].startTime)
    }
}
