package com.classing.shared.time

import java.time.Duration
import java.time.LocalDateTime

sealed interface CountdownState {
    data object InProgress : CountdownState
    data object Soon : CountdownState
    data class Minutes(val value: Long) : CountdownState
    data class HoursMinutes(val hours: Long, val minutes: Long) : CountdownState
}

fun countdownStateFor(
    countdown: Duration?,
    inProgress: Boolean,
): CountdownState? {
    if (inProgress) return CountdownState.InProgress

    val minutes = countdown?.toMinutes()?.coerceAtLeast(0L) ?: return null
    return when {
        minutes <= 0L -> CountdownState.Soon
        minutes < 60L -> CountdownState.Minutes(minutes)
        else -> CountdownState.HoursMinutes(
            hours = minutes / 60L,
            minutes = minutes % 60L,
        )
    }
}

fun nextMinuteDelay(now: LocalDateTime): Duration {
    val elapsedMillis = now.second * 1_000L + now.nano / 1_000_000L
    val remainingMillis = 60_000L - elapsedMillis
    return Duration.ofMillis(remainingMillis.coerceAtLeast(1L))
}
