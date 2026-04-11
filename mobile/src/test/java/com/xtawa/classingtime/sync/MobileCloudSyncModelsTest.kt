package com.xtawa.classingtime.sync

import com.classing.shared.sync.SyncSource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MobileCloudSyncModelsTest {
    @Test
    fun fromJson_falls_back_to_updatedAt_when_revision_missing() {
        val raw = JSONObject(
            """
            {
              "format":"classing_cloud_sync_v1",
              "timetable":{
                "updatedAt":1234,
                "weekNumberMode":"NATURAL",
                "semesterWeekStartDate":"",
                "lessons":[]
              },
              "mobileSettings":{
                "settingsUpdatedAt":5678,
                "settings":{"showWeekend":true}
              }
            }
            """.trimIndent(),
        )

        val doc = CloudDocument.fromJson(raw)

        assertNotNull(doc.timetable)
        assertEquals(1234L, doc.timetable?.revision)
        assertEquals(SyncSource.UNKNOWN, doc.timetable?.source)
        assertEquals(5678L, doc.mobileSettings?.revision)
        assertEquals(SyncSource.UNKNOWN, doc.mobileSettings?.source)
    }
}
