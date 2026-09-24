package com.xtawa.classingtime.screen

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class SystemCalendarChoice(val id: Long, val name: String, val writable: Boolean)
internal data class SystemCalendarEvent(
    val eventId: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val location: String?,
    val allDay: Boolean,
)
internal data class CalendarBridgePreferences(
    val exportEnabled: Boolean = false,
    val importEnabled: Boolean = false,
    val showOnHome: Boolean = false,
    val showOnHeatmap: Boolean = false,
    val exportCalendarId: Long = -1L,
)

internal object SystemCalendarBridge {
    private const val PREFS = "system_calendar_bridge"
    private const val MARKER = "ClassingSync:"

    fun loadPreferences(context: Context): CalendarBridgePreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).run {
            CalendarBridgePreferences(
                exportEnabled = getBoolean("export_enabled", false),
                importEnabled = getBoolean("import_enabled", false),
                showOnHome = getBoolean("show_on_home", false),
                showOnHeatmap = getBoolean("show_on_heatmap", false),
                exportCalendarId = getLong("export_calendar_id", -1L),
            )
        }

    fun savePreferences(context: Context, value: CalendarBridgePreferences) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("export_enabled", value.exportEnabled)
            .putBoolean("import_enabled", value.importEnabled)
            .putBoolean("show_on_home", value.showOnHome)
            .putBoolean("show_on_heatmap", value.showOnHeatmap)
            .putLong("export_calendar_id", value.exportCalendarId)
            .apply()
    }

    fun canRead(context: Context): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    fun canWrite(context: Context): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun calendars(context: Context): List<SystemCalendarChoice> = withContext(Dispatchers.IO) {
        if (!canRead(context)) return@withContext emptyList()
        val rows = mutableListOf<SystemCalendarChoice>()
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL),
            "${CalendarContract.Calendars.VISIBLE}=1", null, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)?.use { cursor ->
            while (cursor.moveToNext()) {
                rows += SystemCalendarChoice(cursor.getLong(0), cursor.getString(1).orEmpty(),
                    cursor.getInt(2) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)
            }
        }
        rows
    }

    suspend fun importEvents(context: Context, start: LocalDate, end: LocalDate): List<SystemCalendarEvent> = withContext(Dispatchers.IO) {
        if (!canRead(context)) return@withContext emptyList()
        val zone = ZoneId.systemDefault()
        val begin = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val finish = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(begin.toString()).appendPath(finish.toString()).build()
        val events = mutableListOf<SystemCalendarEvent>()
        context.contentResolver.query(uri, arrayOf(CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.ALL_DAY), null, null, CalendarContract.Instances.BEGIN)?.use { cursor ->
            while (cursor.moveToNext() && events.size < 500) {
                if (cursor.getString(5).orEmpty().startsWith(MARKER)) continue
                val title = cursor.getString(1).orEmpty().trim()
                if (title.isBlank()) continue
                events += SystemCalendarEvent(cursor.getLong(0), title, cursor.getLong(2), cursor.getLong(3),
                    cursor.getString(4), cursor.getInt(6) != 0)
            }
        }
        events
    }

    suspend fun exportOccurrences(context: Context, calendarId: Long, occurrences: List<EffectiveLessonOccurrence>,
        start: LocalDate, end: LocalDate): Int = withContext(Dispatchers.IO) {
        require(canRead(context) && canWrite(context)) { "Calendar permission required" }
        require(calendars(context).any { it.id == calendarId && it.writable }) { "Choose a writable calendar" }
        val zone = ZoneId.systemDefault()
        val begin = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val finish = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val existing = linkedMapOf<String, Long>()
        context.contentResolver.query(CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID, CalendarContract.Events.DESCRIPTION),
            "${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.DTSTART}>=? AND ${CalendarContract.Events.DTSTART}<? AND ${CalendarContract.Events.DESCRIPTION} LIKE ?",
            arrayOf(calendarId.toString(), begin.toString(), finish.toString(), "$MARKER%"), null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val marker = cursor.getString(1).orEmpty().lineSequence().firstOrNull().orEmpty()
                existing[marker] = cursor.getLong(0)
            }
        }
        val desired = occurrences.take(500).associateBy { markerFor(it.occurrenceId) }
        var changed = 0
        for ((marker, occurrence) in desired) {
            val lesson = occurrence.lesson
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, lesson.title)
                put(CalendarContract.Events.EVENT_LOCATION, lesson.location.orEmpty())
                put(CalendarContract.Events.DESCRIPTION, "$marker\n${lesson.note.orEmpty()}")
                put(CalendarContract.Events.DTSTART, occurrence.date.atTime(lesson.startTime).atZone(zone).toInstant().toEpochMilli())
                put(CalendarContract.Events.DTEND, occurrence.date.atTime(lesson.endTime).atZone(zone).toInstant().toEpochMilli())
                put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            }
            val id = existing.remove(marker)
            if (id == null) {
                context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: error("Calendar insert failed")
            } else {
                context.contentResolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), values, null, null)
            }
            changed++
        }
        for (id in existing.values) {
            context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null)
            changed++
        }
        changed
    }

    private fun markerFor(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return MARKER + digest.joinToString("") { "%02x".format(it) }
    }
}
