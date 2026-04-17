package com.xtawa.classingtime.sync

import com.classing.shared.sync.SyncSource
import com.xtawa.classingtime.data.MobileSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
                "lessons":[
                  {
                    "id":"c1",
                    "title":"Math",
                    "teacher":"Alice",
                    "dayOfWeek":1,
                    "startMinute":480,
                    "endMinute":570,
                    "startWeek":2,
                    "endWeek":18,
                    "weekParity":"EVEN"
                  }
                ]
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
        assertEquals("Alice", doc.timetable?.lessons?.firstOrNull()?.teacher)
        assertEquals(2, doc.timetable?.lessons?.firstOrNull()?.startWeek)
        assertEquals(18, doc.timetable?.lessons?.firstOrNull()?.endWeek)
        assertEquals("EVEN", doc.timetable?.lessons?.firstOrNull()?.weekParity)
        assertEquals(5678L, doc.mobileSettings?.revision)
        assertEquals(SyncSource.UNKNOWN, doc.mobileSettings?.source)
    }

    @Test
    fun toCloudConfigPayload_includesProviderAndDriveFields() {
        val settings = MobileSettings(
            showWeekend = true,
            reminderEnabled = false,
            reminderMinutes = 15,
            keepAliveLevel = "BALANCED",
            experimentalAccessibilityKeepAliveEnabled = false,
            rawIcs = "",
            parseMessage = "",
            wearSyncMode = "AUTO",
            weekNumberMode = "NATURAL",
            semesterWeekStartDate = "",
            weekStartDay = "MONDAY",
            cloudProvider = "GOOGLE_DRIVE",
            cloudSyncEnabled = true,
            cloudServerUrl = "",
            cloudRemotePath = "/classing/classing_sync.json",
            cloudUsername = "",
            cloudDriveFileName = "classing_sync.json",
            cloudDriveTokenExpireAt = 0L,
            cloudConfigPushStatus = "",
            cloudLastResult = "",
            cloudLastSyncedAt = 0L,
        )

        val payload = settings.toCloudConfigPayload(
            password = "",
            driveAccessToken = "token-123",
            driveAccessTokenExpireAt = 12345L,
        )

        assertEquals("GOOGLE_DRIVE", payload.optString("cloudProvider"))
        assertEquals("classing_sync.json", payload.optString("driveFileName"))
        assertEquals("token-123", payload.optString("driveAccessToken"))
        assertEquals(12345L, payload.optLong("driveAccessTokenExpireAt"))
    }

    @Test
    fun runtimeConfig_googleDriveCompletenessDependsOnTokenExpiry() {
        val config = CloudRuntimeConfig(
            provider = com.classing.shared.sync.CloudProvider.GOOGLE_DRIVE,
            enabled = true,
            serverUrl = "",
            remotePath = "",
            username = "",
            password = "",
            driveFileName = "classing_sync.json",
            driveAccessToken = "abc",
            driveAccessTokenExpireAt = System.currentTimeMillis() + 120_000L,
        )

        assertTrue(config.isComplete())
    }
}
