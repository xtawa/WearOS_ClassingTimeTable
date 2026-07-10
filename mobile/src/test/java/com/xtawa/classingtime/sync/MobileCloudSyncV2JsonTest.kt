package com.xtawa.classingtime.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.classing.shared.sync.CloudSyncDocumentV2
import com.classing.shared.sync.CloudSyncV2
import com.classing.shared.sync.LogicalVersion
import com.classing.shared.sync.SyncSource
import com.classing.shared.sync.VersionedRecord
import com.xtawa.classingtime.data.PersistedLesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MobileCloudSyncV2JsonTest {
    @Test
    fun document_roundTripsVersionAndTombstone() {
        val version = LogicalVersion(7, "device-a", 100)
        val document = CloudSyncDocumentV2(records = mapOf(
            CloudSyncV2.DOMAIN_TIMETABLE_LESSONS to mapOf(
                "active" to VersionedRecord("active", "{\"id\":\"active\"}", version),
                "deleted" to VersionedRecord("deleted", "{\"id\":\"deleted\"}", version, 110, 220),
            ),
        ), updatedAt = 120)

        val parsed = MobileCloudSyncV2Json.fromJson(MobileCloudSyncV2Json.toJson(document))

        assertEquals(document, parsed)
    }

    @Test
    fun v1Migration_keepsRawLessonsAndSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val legacy = CloudDocument(
            timetable = CloudTimetableSnapshot(
                updatedAt = 50,
                revision = 50,
                source = SyncSource.PHONE_DIRECT,
                weekNumberMode = "NATURAL",
                semesterWeekStartDate = "",
                lessons = emptyList(),
                baseLessons = listOf(
                    PersistedLesson("lesson-1", "Math", null, null, null, 1, 480, 540, 1, 16, "ALL"),
                ),
            ),
            mobileSettings = CloudNamespaceSnapshot(60, 60, SyncSource.PHONE_LOCAL, org.json.JSONObject().put("showWeekend", true)),
            wearSettings = null,
        )

        val migrated = MobileCloudSyncV2Store.migrateV1(context, legacy, 100)

        assertNotNull(migrated.records[CloudSyncV2.DOMAIN_TIMETABLE_LESSONS]?.get("lesson-1"))
        assertNotNull(migrated.records[CloudSyncV2.DOMAIN_MOBILE_SETTINGS]?.get("showWeekend"))
    }
}
