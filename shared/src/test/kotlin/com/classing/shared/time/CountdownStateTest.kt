package com.classing.shared.time

import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CountdownStateTest {
    @Test
    fun countdownStateFor_mapsBoundaries() {
        assertEquals(CountdownState.Soon, countdownStateFor(Duration.ZERO, inProgress = false))
        assertEquals(CountdownState.Minutes(1), countdownStateFor(Duration.ofMinutes(1), inProgress = false))
        assertEquals(CountdownState.Minutes(59), countdownStateFor(Duration.ofMinutes(59), inProgress = false))
        assertEquals(CountdownState.HoursMinutes(1, 0), countdownStateFor(Duration.ofMinutes(60), inProgress = false))
        assertEquals(CountdownState.HoursMinutes(1, 1), countdownStateFor(Duration.ofMinutes(61), inProgress = false))
    }

    @Test
    fun countdownStateFor_handlesInProgressAndMissingCountdown() {
        assertEquals(CountdownState.InProgress, countdownStateFor(null, inProgress = true))
        assertNull(countdownStateFor(null, inProgress = false))
    }

    @Test
    fun nextMinuteDelay_alignsToTheNextMinuteBoundary() {
        assertEquals(
            Duration.ofSeconds(60),
            nextMinuteDelay(LocalDateTime.of(2026, 3, 15, 8, 30, 0, 0)),
        )
        assertEquals(
            Duration.ofMillis(29_750),
            nextMinuteDelay(LocalDateTime.of(2026, 3, 15, 8, 30, 30, 250_000_000)),
        )
        assertEquals(
            Duration.ofMillis(1),
            nextMinuteDelay(LocalDateTime.of(2026, 3, 15, 8, 30, 59, 999_000_000)),
        )
    }
}
