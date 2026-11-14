package com.xtawa.classingtime.sync

import com.classing.shared.sync.CloudSyncV2
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossPlatformCloudSettingsTest {
    @Test fun missingSettingsAreNotImplicitCrossPlatformDeletes() {
        assertFalse(shouldDeleteMissingCloudRecord(CloudSyncV2.DOMAIN_MOBILE_SETTINGS))
        assertFalse(shouldDeleteMissingCloudRecord(CloudSyncV2.DOMAIN_WEAR_SETTINGS))
        assertFalse(shouldDeleteMissingCloudRecord(CloudSyncV2.DOMAIN_CLOUD_CONFIG))
    }
    @Test fun timetableDeletesStillPropagate() {
        assertTrue(shouldDeleteMissingCloudRecord(CloudSyncV2.DOMAIN_TIMETABLE_LESSONS))
        assertTrue(shouldDeleteMissingCloudRecord(CloudSyncV2.DOMAIN_TIMETABLE_EXCEPTIONS))
        assertTrue(shouldDeleteMissingCloudRecord(CloudSyncV2.DOMAIN_TIMETABLE_EXAMS))
    }
}
