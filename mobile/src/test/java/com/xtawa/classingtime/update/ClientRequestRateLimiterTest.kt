package com.xtawa.classingtime.update

import org.junit.Assert.assertTrue
import org.junit.Test

class ClientRequestRateLimiterTest {
    @Test
    fun allowsThreeRequestsPerMinuteAndThenRecovers() {
        val key = "test-${System.nanoTime()}"
        assertTrue(ClientRequestRateLimiter.acquire(key, 1_000L).isSuccess)
        assertTrue(ClientRequestRateLimiter.acquire(key, 2_000L).isSuccess)
        assertTrue(ClientRequestRateLimiter.acquire(key, 3_000L).isSuccess)
        assertTrue(ClientRequestRateLimiter.acquire(key, 4_000L).isFailure)
        assertTrue(ClientRequestRateLimiter.acquire(key, 61_000L).isSuccess)
    }
}
