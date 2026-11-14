package com.classing.shared.ai

/** Reservation spans both weekly allowance and purchased credits. Use Long arithmetic. */
data class PointBalance(val weeklyRemaining: Long?, val creditsAvailable: Long, val totalAvailable: Long?, val weeklyProgress: Float)

fun pointBalance(limit: Int, used: Int, reserved: Int, credits: Int, blocked: Boolean = false): PointBalance {
    if(blocked) return PointBalance(0,0,0,0f)
    val held = reserved.toLong().coerceAtLeast(0)
    val base = if (limit < 0) null else (limit.toLong() - used.toLong().coerceAtLeast(0)).coerceAtLeast(0)
    val weekly = base?.let { (it - held).coerceAtLeast(0) }
    val creditHeld = if (base == null) 0 else (held - base).coerceAtLeast(0)
    val availableCredits = (credits.toLong().coerceAtLeast(0) - creditHeld).coerceAtLeast(0)
    return PointBalance(weekly, availableCredits, weekly?.plus(availableCredits),
        if (limit <= 0) 0f else ((used.toLong().coerceAtLeast(0) + held).toDouble() / limit).coerceIn(0.0, 1.0).toFloat())
}
