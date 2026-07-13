package com.xtawa.classingtime.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfficialCloudRealtimeTest {
    @Test fun parsesCloudDocumentEventWithoutSettingsPayload() {
        val event = parseOfficialCloudEvent(
            """
            id: 7
            event: cloud-document
            data: {"version":7,"updatedAt":1234}
            """.trimIndent(),
        )
        assertEquals(7L, event?.version)
        assertEquals(1234L, event?.updatedAt)
    }

    @Test fun ignoresOtherAndMalformedEvents() {
        assertNull(parseOfficialCloudEvent("event: settings\ndata: {\"version\":2}"))
        assertNull(parseOfficialCloudEvent("event: cloud-document\ndata: {}"))
    }
}
