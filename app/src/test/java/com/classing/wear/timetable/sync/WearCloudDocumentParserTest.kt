package com.classing.wear.timetable.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WearCloudDocumentParserTest {
    @Test
    fun parse_extracts_timetable_and_wear_settings() {
        val raw = """
            {
              "format":"classing_cloud_sync_v1",
              "timetable":{
                "updatedAt":1234,
                "weekNumberMode":"SEMESTER",
                "semesterWeekStartDate":"2026-03-02",
                "lessons":[
                  {
                    "title":"Math",
                    "dayOfWeek":1,
                    "startMinute":480,
                    "endMinute":570,
                    "location":"Room 101",
                    "note":"Bring notebook"
                  }
                ]
              },
              "wearSettings":{
                "settingsUpdatedAt":5678,
                "settings":{
                  "tileShowCountdown":false,
                  "showWeekend":true
                }
              }
            }
        """.trimIndent()

        val parsed = WearCloudDocumentParser.parse(raw)
        val timetable = parsed.timetable

        assertNotNull(timetable)
        assertEquals(1234L, timetable?.updatedAt)
        assertEquals("SEMESTER", timetable?.weekNumberMode)
        assertEquals("2026-03-02", timetable?.semesterWeekStartDate)
        assertEquals(1, timetable?.lessons?.size)
        assertEquals("Math", timetable?.lessons?.first()?.title)
        assertEquals(5678L, parsed.wearSettingsUpdatedAt)
    }
}
