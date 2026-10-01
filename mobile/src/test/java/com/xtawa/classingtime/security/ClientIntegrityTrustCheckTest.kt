package com.xtawa.classingtime.security

import org.junit.Assert.assertEquals
import org.junit.Test

class ClientIntegrityTrustCheckTest {
    @Test
    fun successIsTrusted() {
        assertEquals(ClientTrustCheckOutcome.TRUSTED, ClientIntegrity.classifyTrustCheck(200, null))
        assertEquals(ClientTrustCheckOutcome.TRUSTED, ClientIntegrity.classifyTrustCheck(204, ""))
    }

    @Test
    fun explicitSignatureRejectionIsFatal() {
        assertEquals(ClientTrustCheckOutcome.REJECTED, ClientIntegrity.classifyTrustCheck(403, "CLIENT_SIGNATURE_INVALID"))
        assertEquals(ClientTrustCheckOutcome.REJECTED, ClientIntegrity.classifyTrustCheck(403, "CLIENT_MARKET_INVALID"))
        assertEquals(ClientTrustCheckOutcome.REJECTED, ClientIntegrity.classifyTrustCheck(403, ""))
        assertEquals(ClientTrustCheckOutcome.REJECTED, ClientIntegrity.classifyTrustCheck(403, null))
    }

    @Test
    fun throttlingAndOutagesAreInconclusive() {
        assertEquals(ClientTrustCheckOutcome.INCONCLUSIVE, ClientIntegrity.classifyTrustCheck(429, "AUTH_RATE_LIMITED"))
        assertEquals(ClientTrustCheckOutcome.INCONCLUSIVE, ClientIntegrity.classifyTrustCheck(500, "INTERNAL_ERROR"))
        assertEquals(ClientTrustCheckOutcome.INCONCLUSIVE, ClientIntegrity.classifyTrustCheck(502, ""))
        assertEquals(ClientTrustCheckOutcome.INCONCLUSIVE, ClientIntegrity.classifyTrustCheck(503, "CLIENT_SIGNATURE_POLICY_MISSING"))
        assertEquals(ClientTrustCheckOutcome.INCONCLUSIVE, ClientIntegrity.classifyTrustCheck(503, "SERVICE_SHUTTING_DOWN"))
    }

    @Test
    fun otherForbiddenReasonsAreNotTreatedAsTampering() {
        assertEquals(ClientTrustCheckOutcome.INCONCLUSIVE, ClientIntegrity.classifyTrustCheck(403, "AUTH_MARKET_MISMATCH"))
    }
}
