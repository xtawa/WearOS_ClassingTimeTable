package com.classing.wear.timetable.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import com.classing.shared.sync.SyncSource

class WearCloudDocumentParserTest {
    @Test
    fun parse_extracts_timetable_and_wear_settings() {
        val raw = """
            {
              "format":"classing_cloud_sync_v1",
              "timetable":{
                "updatedAt":1234,
                "revision":2234,
                "source":"PHONE_DIRECT",
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
                "revision":6678,
                "source":"WEAR_LOCAL",
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
        assertEquals(2234L, timetable?.revision)
        assertEquals(SyncSource.PHONE_DIRECT, timetable?.source)
        assertEquals("SEMESTER", timetable?.weekNumberMode)
        assertEquals("2026-03-02", timetable?.semesterWeekStartDate)
        assertEquals(1, timetable?.lessons?.size)
        assertEquals("Math", timetable?.lessons?.first()?.title)
        assertEquals(5678L, parsed.wearSettings?.updatedAt)
        assertEquals(6678L, parsed.wearSettings?.revision)
        assertEquals(SyncSource.WEAR_LOCAL, parsed.wearSettings?.source)
    }

    @Test
    fun parse_falls_back_when_revision_and_source_absent() {
        val raw = """
            {
              "format":"classing_cloud_sync_v1",
              "timetable":{
                "updatedAt":1234,
                "lessons":[]
              },
              "wearSettings":{
                "settingsUpdatedAt":5678,
                "settings":{}
              }
            }
        """.trimIndent()

        val parsed = WearCloudDocumentParser.parse(raw)

        assertEquals(1234L, parsed.timetable?.revision)
        assertEquals(SyncSource.UNKNOWN, parsed.timetable?.source)
        assertEquals(5678L, parsed.wearSettings?.revision)
        assertEquals(SyncSource.UNKNOWN, parsed.wearSettings?.source)
    }
}
