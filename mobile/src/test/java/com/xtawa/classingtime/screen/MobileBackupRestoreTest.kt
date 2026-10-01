package com.xtawa.classingtime.screen

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MobileBackupRestoreTest {

    private val lesson = LessonUi(
        id = "physics",
        title = "Physics",
        teacher = "Bob",
        location = "Lab 1",
        note = "bring notebook",
        dayOfWeek = DayOfWeek.TUESDAY,
        startTime = LocalTime.of(9, 0),
        endTime = LocalTime.of(10, 30),
        startWeek = 2,
        endWeek = 12,
        weekParity = LessonWeekParity.EVEN,
    )

    @Test
    fun buildAndParseBackup_v2_roundTripsLessonsAndExceptions() {
        val exception = ScheduleExceptionUi(
            id = "makeup-1",
            lessonId = null,
            type = ScheduleExceptionKind.MAKE_UP,
            date = LocalDate.of(2026, 4, 1),
            title = "Physics Makeup",
            teacher = "Bob",
            location = "Hall 2",
            startTime = LocalTime.of(14, 0),
            endTime = LocalTime.of(15, 0),
        )

        val backup = buildScheduleBackupJson(
            baseLessons = listOf(lesson),
            exceptions = listOf(exception),
            zoneId = ZoneId.of("Asia/Shanghai"),
            weekNumberMode = WeekNumberMode.SEMESTER,
            semesterWeekStartDate = LocalDate.of(2026, 3, 2),
        )

        val parsed = parseScheduleBackupJson(
            raw = backup,
            context = ApplicationProvider.getApplicationContext(),
        )

        assertNotNull(parsed)
        assertEquals(1, parsed?.baseLessons?.size)
        assertEquals("Physics", parsed?.baseLessons?.firstOrNull()?.title)
        assertEquals(1, parsed?.exceptions?.size)
        assertEquals(ScheduleExceptionKind.MAKE_UP, parsed?.exceptions?.firstOrNull()?.type)
        assertEquals(WeekNumberMode.SEMESTER, parsed?.weekNumberMode)
        assertEquals(LocalDate.of(2026, 3, 2), parsed?.semesterWeekStartDate)
    }

    @Test
    fun parseScheduleBackup_v2_preservesLessonIdsSoCancelAndRescheduleExceptionsStillApply() {
        val cancel = ScheduleExceptionUi(
            id = "cancel-1",
            lessonId = lesson.id,
            type = ScheduleExceptionKind.CANCEL,
            date = LocalDate.of(2026, 3, 10),
        )
        val backup = buildScheduleBackupJson(
            baseLessons = listOf(lesson),
            exceptions = listOf(cancel),
            zoneId = ZoneId.of("Asia/Shanghai"),
            weekNumberMode = WeekNumberMode.SEMESTER,
            semesterWeekStartDate = LocalDate.of(2026, 3, 2),
        )

        val parsed = parseScheduleBackupJson(
            raw = backup,
            context = ApplicationProvider.getApplicationContext(),
        )

        assertNotNull(parsed)
        val restoredLesson = parsed!!.baseLessons.single()
        assertEquals(lesson.id, restoredLesson.id)
        assertEquals(lesson.id, parsed.exceptions.single().lessonId)

        // 2026-03-10 is a Tuesday in semester week 2: the base lesson is due but cancelled.
        val occurrences = buildEffectiveOccurrencesForDateRange(
            baseLessons = parsed.baseLessons,
            exceptions = parsed.exceptions,
            startDate = LocalDate.of(2026, 3, 10),
            endDate = LocalDate.of(2026, 3, 10),
            weekNumberMode = WeekNumberMode.SEMESTER,
            semesterWeekStartDate = LocalDate.of(2026, 3, 2),
        )
        assertTrue(occurrences.isEmpty())
    }

    @Test
    fun parseJsonToLessons_ignoresIncomingIdsForPlainImports() {
        val raw = """[{"id":"keep-me","title":"Math","dayOfWeek":1,"startTime":"08:00","endTime":"09:00"}]"""
        val imported = parseJsonToLessons(raw, ApplicationProvider.getApplicationContext())
        assertEquals(1, imported.lessons.size)
        assertTrue(imported.lessons.single().id.startsWith("json-"))

        val preserved = parseJsonToLessons(raw, ApplicationProvider.getApplicationContext(), preserveIds = true)
        assertEquals("keep-me", preserved.lessons.single().id)
    }

    @Test
    fun parseScheduleBackup_v1_keepsCompatibility() {
        val raw = """
            {
              "format":"classingtime_backup_v1",
              "version":1,
              "courses":[
                {
                  "id":"chem-1",
                  "title":"Chemistry",
                  "teacher":"Cindy",
                  "dayOfWeek":3,
                  "startTime":"10:00",
                  "endTime":"11:30",
                  "location":"A201",
                  "note":"",
                  "startWeek":1,
                  "endWeek":16,
                  "weekParity":"ALL"
                }
              ]
            }
        """.trimIndent()

        val parsed = parseScheduleBackupJson(
            raw = raw,
            context = ApplicationProvider.getApplicationContext(),
        )

        assertNotNull(parsed)
        assertEquals(1, parsed?.baseLessons?.size)
        assertTrue(parsed?.exceptions?.isEmpty() == true)
        assertEquals("Chemistry", parsed?.baseLessons?.firstOrNull()?.title)
        assertEquals(null, parsed?.weekNumberMode)
        assertEquals(null, parsed?.semesterWeekStartDate)
    }
}
