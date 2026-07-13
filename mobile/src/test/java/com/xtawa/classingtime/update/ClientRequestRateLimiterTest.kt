package com.xtawa.classingtime.update

import org.junit.Assert.assertTrue
	import org.junit.Assert.assertEquals
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

	@Test
	fun serverCooldownBlocksRepeatedRequestsAndReportsRemainingTime() {
		val key = "cooldown-${System.nanoTime()}"
		ClientRequestRateLimiter.recordCooldown(key, 30, 10_000L)
		val error = ClientRequestRateLimiter.acquire(key, 20_000L).exceptionOrNull()
		assertTrue(error is ClientRequestRateLimitException)
		assertEquals(20L, (error as ClientRequestRateLimitException).retryAfterSeconds)
		assertTrue(ClientRequestRateLimiter.acquire(key, 40_001L).isSuccess)
	}
}
