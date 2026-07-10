package com.classing.shared.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudProviderTest {
    @Test
    fun fromWire_defaultsToWebDav_whenMissing() {
        assertEquals(CloudProvider.WEBDAV, CloudProvider.fromWire(null))
        assertEquals(CloudProvider.WEBDAV, CloudProvider.fromWire(""))
        assertEquals(CloudProvider.WEBDAV, CloudProvider.fromWire("unknown"))
    }

    @Test
    fun fromWire_parsesGoogleDrive() {
        assertEquals(CloudProvider.GOOGLE_DRIVE, CloudProvider.fromWire("GOOGLE_DRIVE"))
        assertEquals(CloudProvider.GOOGLE_DRIVE, CloudProvider.fromWire("google_drive"))
    }

    @Test
    fun fromWire_parsesOfficial() {
        assertEquals(CloudProvider.OFFICIAL, CloudProvider.fromWire("OFFICIAL"))
        assertEquals(CloudProvider.OFFICIAL, CloudProvider.fromWire("official"))
    }
}
