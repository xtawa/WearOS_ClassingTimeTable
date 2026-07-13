package com.xtawa.classingtime.sync

import com.classing.shared.sync.CloudSyncDocumentV2
import com.classing.shared.sync.CloudSyncV2
import com.classing.shared.sync.LogicalVersion
import com.classing.shared.sync.VersionedRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncInitializationTest {
    private val remoteLesson = VersionedRecord(
        id = "lesson-1",
        payload = "{\"title\":\"Remote\"}",
        version = LogicalVersion(10, "remote", 10),
    )

    @Test fun missingBaselineInitializesFromRemoteWithoutCreatingDeletion() {
        val remote = CloudSyncDocumentV2(records = mapOf(
            CloudSyncV2.DOMAIN_TIMETABLE_LESSONS to mapOf(remoteLesson.id to remoteLesson),
        ))
        val result = mergeForSyncInitialization(remote, CloudSyncDocumentV2(), true, false, 20)
        val lesson = result.document.records.getValue(CloudSyncV2.DOMAIN_TIMETABLE_LESSONS).getValue("lesson-1")
        assertFalse(lesson.isDeleted)
        assertEquals("remote", lesson.version.deviceId)
    }

    @Test fun establishedBaselineStillPropagatesExplicitTombstone() {
        val remote = CloudSyncDocumentV2(records = mapOf(
            CloudSyncV2.DOMAIN_TIMETABLE_LESSONS to mapOf(remoteLesson.id to remoteLesson),
        ))
        val tombstone = remoteLesson.copy(
            version = LogicalVersion(11, "local", 20),
            deletedAt = 20,
            recoverableUntil = 100,
        )
        val local = CloudSyncDocumentV2(records = mapOf(
            CloudSyncV2.DOMAIN_TIMETABLE_LESSONS to mapOf(tombstone.id to tombstone),
        ))
        val result = mergeForSyncInitialization(remote, local, true, true, 21)
        assertTrue(result.document.records.getValue(CloudSyncV2.DOMAIN_TIMETABLE_LESSONS).getValue("lesson-1").isDeleted)
    }

    @Test fun missingBaselineMergesNewerLocalSettingWithoutDroppingRemoteLessons() {
        val remoteSetting = VersionedRecord(
            id = "showWeekend",
            payload = "{\"value\":true}",
            version = LogicalVersion(10, "remote", 10),
        )
        val localSetting = remoteSetting.copy(
            payload = "{\"value\":false}",
            version = LogicalVersion(11, "phone", 20),
        )
        val remote = CloudSyncDocumentV2(records = mapOf(
            CloudSyncV2.DOMAIN_TIMETABLE_LESSONS to mapOf(remoteLesson.id to remoteLesson),
            CloudSyncV2.DOMAIN_MOBILE_SETTINGS to mapOf(remoteSetting.id to remoteSetting),
        ))
        val local = CloudSyncDocumentV2(records = mapOf(
            CloudSyncV2.DOMAIN_MOBILE_SETTINGS to mapOf(localSetting.id to localSetting),
        ))

        val result = mergeForSyncInitialization(remote, local, true, false, 21).document

        assertEquals(localSetting, result.records.getValue(CloudSyncV2.DOMAIN_MOBILE_SETTINGS).getValue("showWeekend"))
        assertEquals(remoteLesson, result.records.getValue(CloudSyncV2.DOMAIN_TIMETABLE_LESSONS).getValue("lesson-1"))
    }
}
