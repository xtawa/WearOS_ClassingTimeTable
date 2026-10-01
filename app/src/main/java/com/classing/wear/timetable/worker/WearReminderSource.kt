package com.classing.wear.timetable.worker

import android.content.Context
import android.util.Log
import com.classing.wear.timetable.ClassingTimetableApplication
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException

/**
 * Loads reminder candidates from the Wear Room database (the same projection the UI, tile and
 * complication render) instead of the last raw phone payload.
 *
 * The raw payload is unsuitable as a reminder source: INCREMENTAL payloads only carry changed
 * lessons, stale/duplicate payloads used to blank it, and official-cloud (independent) mode never
 * writes it at all. The database is the single source of truth for what is actually scheduled.
 */
object WearReminderSource {
    private const val TAG = "WearReminderSource"

    /**
     * Returns the projected occurrences for today plus [ReminderCheckLogic.LOOKAHEAD_DAYS], or `null`
     * when the database could not be read. A transient read failure is deliberately *not* reported as
     * "no lessons": callers must keep any existing alarm instead of cancelling it. Cancellation is
     * propagated untouched.
     */
    suspend fun loadOrNull(context: Context, now: LocalDateTime): List<ReminderOccurrence>? {
        val app = context.applicationContext as? ClassingTimetableApplication ?: return null
        val today = now.toLocalDate()
        return try {
            val occurrences = app.appContainer.scheduleRepository.loadOccurrences(
                startDate = today,
                endDate = today.plusDays(ReminderCheckLogic.LOOKAHEAD_DAYS),
                now = now,
            )
            ReminderCheckLogic.fromOccurrences(occurrences)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Failed to load reminder occurrences from database", error)
            null
        }
    }
}
