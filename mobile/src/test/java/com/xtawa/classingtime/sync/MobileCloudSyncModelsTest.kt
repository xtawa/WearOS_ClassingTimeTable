package com.xtawa.classingtime.sync

import com.classing.shared.sync.SyncSource
import com.xtawa.classingtime.data.MobileSettings
import com.xtawa.classingtime.data.PersistedLesson
import com.xtawa.classingtime.data.PersistedScheduleException
import com.xtawa.classingtime.data.PersistedScheduleSnapshot
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
    fun toCloudConfigPayload_excludesCredentials() {
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
        assertTrue(!payload.has("password"))
        assertTrue(!payload.has("driveAccessToken"))
        assertTrue(!payload.has("driveAccessTokenExpireAt"))
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
            accountAccessToken = "",
            officialMemberAuthorized = false,
        )

        assertTrue(config.isComplete())
    }

    @Test
    fun cloudTimetable_roundTripsRawBaseLessonsAndExceptions() {
        val snapshot = CloudTimetableSnapshot(
            updatedAt = 100L,
            revision = 100L,
            source = SyncSource.PHONE_DIRECT,
            weekNumberMode = "SEMESTER",
            semesterWeekStartDate = "2026-03-02",
            lessons = listOf(
                PersistedLesson(
                    id = "flattened-1",
                    title = "Math",
                    teacher = "Alice",
                    location = "A101",
                    note = null,
                    dayOfWeek = 1,
                    startMinute = 480,
                    endMinute = 570,
                    startWeek = 1,
                    endWeek = 8,
                    weekParity = "ALL",
                ),
            ),
            baseLessons = listOf(
                PersistedLesson(
                    id = "base-1",
                    title = "Math",
                    teacher = "Alice",
                    location = "A101",
                    note = null,
                    dayOfWeek = 1,
                    startMinute = 480,
                    endMinute = 570,
                    startWeek = 1,
                    endWeek = 16,
                    weekParity = "ALL",
                ),
            ),
            exceptions = listOf(
                PersistedScheduleException(
                    id = "cancel-1",
                    lessonId = "base-1",
                    type = "CANCEL",
                    date = "2026-03-09",
                    title = null,
                    teacher = null,
                    location = null,
                    note = null,
                    dayOfWeek = null,
                    startMinute = null,
                    endMinute = null,
                ),
            ),
        )

        val document = CloudDocument(
            timetable = snapshot,
            mobileSettings = null,
            wearSettings = null,
        )

        val parsed = CloudDocument.fromJson(document.toJson())

        assertNotNull(parsed.timetable)
        assertEquals(1, parsed.timetable?.baseLessons?.size)
        assertEquals("base-1", parsed.timetable?.baseLessons?.firstOrNull()?.id)
        assertEquals(1, parsed.timetable?.exceptions?.size)
        assertEquals("CANCEL", parsed.timetable?.exceptions?.firstOrNull()?.type)
    }

    @Test
    fun toPersistedTimetableState_prefersRawStateWhenAvailable() {
        val snapshot = CloudTimetableSnapshot(
            updatedAt = 100L,
            revision = 100L,
            source = SyncSource.PHONE_DIRECT,
            weekNumberMode = "SEMESTER",
            semesterWeekStartDate = "2026-03-02",
            lessons = listOf(
                PersistedLesson(
                    id = "flattened-1",
                    title = "Math",
                    teacher = null,
                    location = null,
                    note = null,
                    dayOfWeek = 1,
                    startMinute = 480,
                    endMinute = 570,
                    startWeek = 1,
                    endWeek = 8,
                    weekParity = "ALL",
                ),
            ),
            baseLessons = listOf(
                PersistedLesson(
                    id = "base-1",
                    title = "Math",
                    teacher = null,
                    location = null,
                    note = null,
                    dayOfWeek = 1,
                    startMinute = 480,
                    endMinute = 570,
                    startWeek = 1,
                    endWeek = 16,
                    weekParity = "ALL",
                ),
            ),
            exceptions = listOf(
                PersistedScheduleException(
                    id = "cancel-1",
                    lessonId = "base-1",
                    type = "CANCEL",
                    date = "2026-03-09",
                    title = null,
                    teacher = null,
                    location = null,
                    note = null,
                    dayOfWeek = null,
                    startMinute = null,
                    endMinute = null,
                ),
            ),
        )

        val state = snapshot.toPersistedTimetableState(
            snapshots = listOf(
                PersistedScheduleSnapshot(
                    id = "snap-1",
                    createdAt = 1L,
                    reason = "test",
                    weekNumberMode = "SEMESTER",
                    semesterWeekStartDate = "2026-03-02",
                    baseLessons = emptyList(),
                    exceptions = emptyList(),
                ),
            ),
        )

        assertEquals("base-1", state.baseLessons.firstOrNull()?.id)
        assertEquals("cancel-1", state.exceptions.firstOrNull()?.id)
        assertEquals(1, state.snapshots.size)
    }
}
