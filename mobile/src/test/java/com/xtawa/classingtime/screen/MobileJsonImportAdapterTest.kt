package com.xtawa.classingtime.screen

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class MobileJsonImportAdapterTest {
    @Test
    fun parseJsonToLessons_supportsLegacyBackupCoursesSchema() {
        val raw = """
            {
              "format": "classingtime_backup_v1",
              "courses": [
                {
                  "title": "Math",
                  "dayOfWeek": 1,
                  "startTime": "08:00",
                  "endTime": "09:40",
                  "teacher": "Li",
                  "startWeek": 2,
                  "endWeek": 16,
                  "weekParity": "EVEN"
                }
              ]
            }
        """.trimIndent()

        val result = parseJsonToLessons(raw, ApplicationProvider.getApplicationContext())

        assertEquals(1, result.lessons.size)
        val lesson = result.lessons.first()
        assertEquals("Math", lesson.title)
        assertEquals(LocalTime.of(8, 0), lesson.startTime)
        assertEquals(LocalTime.of(9, 40), lesson.endTime)
        assertEquals(2, lesson.startWeek)
        assertEquals(16, lesson.endWeek)
        assertEquals(LessonWeekParity.EVEN, lesson.weekParity)
    }

    @Test
    fun parseJsonToLessons_supportsCloudTimetableMinuteFields() {
        val raw = """
            {
              "format": "classingtime_cloud_v1",
              "timetable": {
                "lessons": [
                  {
                    "title": "Physics",
                    "dayOfWeek": 2,
                    "startMinute": 600,
                    "endMinute": 690,
                    "teacher": "Wang",
                    "location": "A201",
                    "note": "Lab",
                    "startWeek": 3,
                    "endWeek": 8,
                    "weekParity": "ODD"
                  }
                ]
              }
            }
        """.trimIndent()

        val result = parseJsonToLessons(raw, ApplicationProvider.getApplicationContext())

        assertEquals(1, result.lessons.size)
        val lesson = result.lessons.first()
        assertEquals(LocalTime.of(10, 0), lesson.startTime)
        assertEquals(LocalTime.of(11, 30), lesson.endTime)
        assertEquals(LessonWeekParity.ODD, lesson.weekParity)
        assertEquals("Wang", lesson.teacher)
    }

    @Test
    fun parseJsonToLessons_skipsInvalidEntriesAndKeepsWarnings() {
        val raw = """
            {
              "timetable": {
                "lessons": [
                  { "dayOfWeek": 1, "startMinute": 480, "endMinute": 540 },
                  { "title": "Broken", "dayOfWeek": 1, "startMinute": 560, "endMinute": 500 },
                  { "title": "Valid", "dayOfWeek": 1, "startMinute": 480, "endMinute": 540 }
                ]
              }
            }
        """.trimIndent()

        val result = parseJsonToLessons(raw, ApplicationProvider.getApplicationContext())

        assertEquals(1, result.lessons.size)
        assertEquals("Valid", result.lessons.first().title)
        assertTrue(result.warnings.size >= 2)
    }

    @Test
    fun parseJsonToLessons_fallsBackToDefaultWeekRuleWhenInvalid() {
        val raw = """
            {
              "timetable": {
                "lessons": [
                  {
                    "title": "Chemistry",
                    "dayOfWeek": 3,
                    "startMinute": 480,
                    "endMinute": 540,
                    "startWeek": "foo",
                    "endWeek": "bar",
                    "weekParity": "UNKNOWN"
                  }
                ]
              }
            }
        """.trimIndent()

        val result = parseJsonToLessons(raw, ApplicationProvider.getApplicationContext())

        assertEquals(1, result.lessons.size)
        val lesson = result.lessons.first()
        assertEquals(DEFAULT_START_WEEK, lesson.startWeek)
        assertEquals(DEFAULT_END_WEEK, lesson.endWeek)
        assertEquals(LessonWeekParity.ALL, lesson.weekParity)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun parseJsonToLessons_prefersStartTimeOverMinuteFallback() {
        val raw = """
            {
              "timetable": {
                "lessons": [
                  {
                    "title": "History",
                    "dayOfWeek": 4,
                    "startTime": "09:15",
                    "endTime": "10:45",
                    "startMinute": 600,
                    "endMinute": 660
                  }
                ]
              }
            }
        """.trimIndent()

        val result = parseJsonToLessons(raw, ApplicationProvider.getApplicationContext())

        assertEquals(1, result.lessons.size)
        val lesson = result.lessons.first()
        assertEquals(LocalTime.of(9, 15), lesson.startTime)
        assertEquals(LocalTime.of(10, 45), lesson.endTime)
    }

    @Test
    fun parseJsonToLessons_ignoresCloudSettingsNamespaces() {
        val raw = """
            {
              "timetable": {
                "lessons": [
                  {
                    "title": "Biology",
                    "dayOfWeek": 5,
                    "startMinute": 780,
                    "endMinute": 840
                  }
                ]
              },
              "mobileSettings": {
                "revision": 10,
                "settings": { "showWeekend": true }
              },
              "wearSettings": {
                "revision": 11,
                "settings": { "hapticsEnabled": true }
              }
            }
        """.trimIndent()

        val result = parseJsonToLessons(raw, ApplicationProvider.getApplicationContext())

        assertEquals(1, result.lessons.size)
        assertEquals("Biology", result.lessons.first().title)
    }
}
