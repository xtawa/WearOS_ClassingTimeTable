package com.classing.wear.timetable.ui.screen.assistant

import com.classing.wear.timetable.domain.model.Course
import com.classing.wear.timetable.domain.model.CourseSession
import com.classing.wear.timetable.domain.model.LessonOccurrence
import com.classing.wear.timetable.domain.model.LessonStatus
import com.classing.wear.timetable.domain.model.TimeSlot
import com.classing.wear.timetable.domain.model.WeekParity
import com.classing.wear.timetable.domain.model.WeekRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AskAiSnapshotTest {
    @Test
    fun snapshotKeepsAuthoritativeScheduleFields() {
        val date = LocalDate.of(2026, 8, 17)
        val course = Course(1, null, 1, "Biology", "Lin", "Lab 2", "Bring notes", "green", false, 1)
        val session = CourseSession(2, null, 1, 1, DayOfWeek.MONDAY, 3, WeekRule(1, 53, WeekParity.ODD), 1)
        val slot = TimeSlot(3, null, 1, 2, "2", LocalTime.of(10, 20), LocalTime.of(11, 5), 1)
        val lesson = LessonOccurrence(
            course = course,
            session = session,
            timeSlot = slot,
            date = date,
            weekIndex = 3,
            status = LessonStatus.NOT_STARTED,
            startAt = LocalDateTime.of(date, slot.startTime),
            endAt = LocalDateTime.of(date, slot.endTime),
        )

        val snapshot = buildWearTimetableSnapshot(date.toString(), 3, listOf(lesson))
        val encoded = snapshot.getJSONArray("lessons").getJSONObject(0)

        assertEquals("Biology", encoded.getString("title"))
        assertEquals("Lab 2", encoded.getString("location"))
        assertEquals(53, encoded.getInt("endWeek"))
        assertEquals("ODD", encoded.getString("weekParity"))
        assertFalse(snapshot.has("accessToken"))
    }
}
