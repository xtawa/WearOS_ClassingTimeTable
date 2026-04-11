package com.classing.shared.sync

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncArbitratorTest {
    @Test
    fun `should apply when incoming revision is newer`() {
        val current = SyncStamp(
            revision = 100,
            source = SyncSource.CLOUD_PULL,
            appliedAt = 100,
        )
        val incoming = SyncStamp(
            revision = 101,
            source = SyncSource.WEAR_LOCAL,
            appliedAt = 101,
        )

        assertTrue(
            SyncArbitrator.shouldApply(
                domain = SyncDomain.TIMETABLE,
                incoming = incoming,
                current = current,
            ),
        )
    }

    @Test
    fun `should use domain source priority when revision ties`() {
        val current = SyncStamp(
            revision = 200,
            source = SyncSource.CLOUD_PULL,
            appliedAt = 200,
        )
        val incoming = SyncStamp(
            revision = 200,
            source = SyncSource.PHONE_DIRECT,
            appliedAt = 190,
        )

        assertTrue(
            SyncArbitrator.shouldApply(
                domain = SyncDomain.TIMETABLE,
                incoming = incoming,
                current = current,
            ),
        )
    }

    @Test
    fun `should use appliedAt when revision and source tie`() {
        val current = SyncStamp(
            revision = 300,
            source = SyncSource.WEAR_LOCAL,
            appliedAt = 1000,
        )
        val incoming = SyncStamp(
            revision = 300,
            source = SyncSource.WEAR_LOCAL,
            appliedAt = 1001,
        )

        assertTrue(
            SyncArbitrator.shouldApply(
                domain = SyncDomain.WEAR_SETTINGS,
                incoming = incoming,
                current = current,
            ),
        )
    }

    @Test
    fun `should skip exact duplicate stamp`() {
        val current = SyncStamp(
            revision = 400,
            source = SyncSource.PHONE_LOCAL,
            appliedAt = 400,
        )
        val incoming = current.copy()

        assertFalse(
            SyncArbitrator.shouldApply(
                domain = SyncDomain.MOBILE_SETTINGS,
                incoming = incoming,
                current = current,
            ),
        )
    }
}
