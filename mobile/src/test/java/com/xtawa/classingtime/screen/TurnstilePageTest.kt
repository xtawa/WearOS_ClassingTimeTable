package com.xtawa.classingtime.screen

import org.junit.Assert.*
import org.junit.Test

class TurnstilePageTest {
    private val origin = "https://api.example.com/"

    @Test fun baseUrlUsesConfiguredHttpsOriginWithoutPathsOrQueries() {
        assertEquals(origin, registrationVerificationOrigin("https://api.example.com/api?test=1"))
        assertEquals("https://api.example.com:8443/", registrationVerificationOrigin("https://api.example.com:8443/api"))
    }

    @Test fun insecureOrCredentialContainingOriginsAreRejected() {
        listOf("http://api.example.com", "file:///tmp/test", "https://user:secret@api.example.com", "https:///missing").forEach {
            assertTrue(it, runCatching { registrationVerificationOrigin(it) }.isFailure)
        }
    }

    @Test fun injectionIsRejectedRatherThanChangingTheServerSiteKey() {
        listOf("", "key');alert(1)//", "<script>", "key with spaces").forEach {
            assertTrue(it, runCatching { turnstilePage(it, false) }.isFailure)
        }
    }

    @Test fun widgetFitsNarrowDialogsAndUsesExplicitSingleAttemptCallbacks() {
        val html = turnstilePage("1x00000000000000000000AA", true)
        assertTrue(html.contains("size:'compact',theme:'dark',retry:'never'"))
        listOf("verified", "expired", "timeout", "error", "ready").forEach { assertTrue(html.contains("'$it'")) }
        assertTrue(html.contains("api.js?onload=startVerification&amp;render=explicit"))
    }

    @Test fun duplicateTokensAreAcceptedOnlyOnce() {
        val attempt = TurnstileAttempt()
        assertTrue(attempt.finish())
        assertFalse(attempt.isActive())
        assertFalse(attempt.finish())
    }

    @Test fun failedAndDisposedAttemptsCannotSubmitLateTokens() {
        val failed = TurnstileAttempt()
        failed.finish()
        assertFalse(failed.finish())
        val disposed = TurnstileAttempt()
        disposed.dispose()
        assertFalse(disposed.finish())
        assertTrue(TurnstileAttempt().finish()) // Retry gets a new attempt, never a reused token.
    }

    @Test fun verificationNavigationAllowsOnlyTrustedHttpsAndRequiredBlankFrames() {
        listOf(origin, "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/", "about:blank", "about:srcdoc").forEach {
            assertTrue(it, verificationNavigationAllowed(it, origin))
        }
        listOf("http://api.example.com", "https://api.example.com.evil.com", "https://evil.com", "file:///etc/passwd",
            "javascript:alert(1)", "https://user@api.example.com", "https://challenges.cloudflare.com:444/").forEach {
            assertFalse(it, verificationNavigationAllowed(it, origin))
        }
    }
}
