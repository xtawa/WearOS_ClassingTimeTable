package com.xtawa.classingtime.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearQrLoginParserTest {
    @Test
    fun parsesExpectedWearLoginPayload() {
        assertEquals(
            "dva_example-123",
            parseWearLoginAuthorizationId("classing://wear-login?authorizationId=dva_example-123"),
        )
    }

    @Test
    fun rejectsUnexpectedSchemesHostsAndIds() {
        assertNull(parseWearLoginAuthorizationId("https://example.com/?authorizationId=dva_example"))
        assertNull(parseWearLoginAuthorizationId("classing://mobile-login?authorizationId=dva_example"))
        assertNull(parseWearLoginAuthorizationId("classing://wear-login?authorizationId=bad%20id"))
    }
}
