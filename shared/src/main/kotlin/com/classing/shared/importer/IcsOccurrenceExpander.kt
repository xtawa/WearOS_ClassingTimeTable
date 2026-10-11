package com.classing.shared.importer

import java.time.Instant
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class ImportedOccurrence(val event: ParsedEvent, val start: Instant, val end: Instant)
data class ExpandedImport(val occurrences: List<ImportedOccurrence>, val warnings: List<String>)

/** Expand the supported RFC 5545 subset without approximating unknown rules. */
class IcsOccurrenceExpander {
    fun expand(events: List<ParsedEvent>, from: LocalDate, through: LocalDate, zone: ZoneId): ExpandedImport {
        require(!through.isBefore(from) && ChronoUnit.DAYS.between(from, through) <= 3660)
        val warnings = mutableListOf<String>()
        val output = mutableListOf<ImportedOccurrence>()
        val overrides = events.filter { it.recurrenceId != null }.groupBy { it.rawFields["UID"] }
        events.filter { it.recurrenceId == null }.forEach { event ->
            val outcome = runCatching {
                val start = requireNotNull(event.dtStart)
                val end = requireNotNull(event.dtEnd)
                require(end > start) { "End must be after start" }
                require(event.rawFields.keys.none { it in setOf("RDATE", "EXRULE") }) { "RDATE/EXRULE requires manual review" }
                if (event.rawFields["STATUS"]?.uppercase() == "CANCELLED") return@runCatching
                val sourceZone = event.rawFields["DTSTART_TZID"]?.let(ZoneId::of)
                    ?: if (event.rawFields["DTSTART"]?.endsWith('Z') == true) ZoneOffset.UTC else zone
                val rawStart = event.rawFields["DTSTART"].orEmpty()
                val startLocal = if (rawStart.length == 15 && !rawStart.endsWith('Z'))
                    LocalDateTime.parse(rawStart, compact) else start.atZone(sourceZone).toLocalDateTime()
                val duration = Duration.between(start, end)
                val props = event.rRule.orEmpty().split(';').filter(String::isNotBlank).associate {
                    require('=' in it) { "Malformed RRULE" }; it.substringBefore('=').uppercase() to it.substringAfter('=').uppercase()
                }
                require(props.keys.all { it in setOf("FREQ", "INTERVAL", "UNTIL", "COUNT", "BYDAY", "WKST") }) { "Unsupported RRULE field" }
                val freq = props["FREQ"]
                require(event.rRule.isNullOrBlank() || freq != null) { "RRULE requires FREQ" }
                require(freq == null || freq in setOf("DAILY", "WEEKLY")) { "Unsupported recurrence frequency: $freq" }
                require(!props.containsKey("COUNT") || !props.containsKey("UNTIL")) { "COUNT and UNTIL cannot coexist" }
                val interval = props["INTERVAL"]?.toInt() ?: 1
                require(interval in 1..366)
                val count = props["COUNT"]?.toInt() ?: 10000
                require(count in 1..10000)
                val dayNames = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
                val days = props["BYDAY"]?.split(',')?.map { value ->
                    require(value in dayNames) { "Ordinal BYDAY is not supported" }; dayNames.indexOf(value) + 1
                } ?: listOf(startLocal.dayOfWeek.value)
                val weekStart = props["WKST"]?.let { require(it in dayNames); dayNames.indexOf(it) + 1 } ?: 1
                require(freq == null || !props.containsKey("BYDAY") || startLocal.dayOfWeek.value in days) { "DTSTART does not match BYDAY; review the source" }
                val anchorWeek = startLocal.toLocalDate().minusDays(Math.floorMod(startLocal.dayOfWeek.value - weekStart, 7).toLong())
                val until = props["UNTIL"]?.let { raw ->
                    if (raw.length == 8) LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE).plusDays(1).atStartOfDay(sourceZone).toInstant().minusNanos(1)
                    else LocalDateTime.parse(raw.removeSuffix("Z"), compact).atZone(if (raw.endsWith('Z')) ZoneOffset.UTC else sourceZone).toInstant()
                }
                val replacements = event.rawFields["UID"]?.takeIf(String::isNotBlank)?.let { overrides[it] }.orEmpty().associateBy { it.recurrenceId }
                val latestSourceDate = maxOf(through.plusDays(1), replacements.keys.filterNotNull().maxOrNull()?.atZone(sourceZone)?.toLocalDate() ?: through)
                require(ChronoUnit.DAYS.between(startLocal.toLocalDate(), latestSourceDate) <= 3660) { "Recurrence exceeds ten-year import limit" }
                var date = startLocal.toLocalDate()
                var emitted = 0
                while (!date.isAfter(latestSourceDate) && emitted < count) {
                    val weeks = ChronoUnit.WEEKS.between(anchorWeek, date.minusDays(Math.floorMod(date.dayOfWeek.value - weekStart, 7).toLong()))
                    val matches = when (freq) {
                        null -> date == startLocal.toLocalDate()
                        "DAILY" -> ChronoUnit.DAYS.between(startLocal.toLocalDate(), date) % interval == 0L && (!props.containsKey("BYDAY") || date.dayOfWeek.value in days)
                        else -> weeks % interval == 0L && date.dayOfWeek.value in days
                    }
                    val localStart = date.atTime(startLocal.toLocalTime())
                    if (matches && freq != null && date != startLocal.toLocalDate() && sourceZone.rules.getValidOffsets(localStart).isEmpty()) {
                        warnings += "${event.summary}: nonexistent local time on $date skipped (DST gap)"
                        date = date.plusDays(1)
                        continue
                    }
                    val occurrenceStart = if (date == startLocal.toLocalDate()) start else localStart.atZone(sourceZone).toInstant()
                    if (until != null && occurrenceStart > until) break
                    if (matches) {
                        emitted++
                        val replacement = replacements[occurrenceStart]
                        if (occurrenceStart !in event.exDates && replacement?.rawFields?.get("STATUS")?.uppercase() != "CANCELLED") {
                            val actualStart = replacement?.dtStart ?: occurrenceStart
                            val actualEnd = replacement?.dtEnd ?: occurrenceStart.plus(duration)
                            if (actualStart.atZone(zone).toLocalDate() in from..through) output += ImportedOccurrence(replacement ?: event, actualStart, actualEnd)
                        }
                    }
                    if (freq == null) break
                    date = date.plusDays(1)
                }
                if (freq != null && emitted < count && (until == null || until.atZone(zone).toLocalDate() > through)) {
                    warnings += "${event.summary}: recurrence imported through $through; later dates require another import"
                }
            }
            outcome.exceptionOrNull()?.let { warnings += "${event.summary}: ${it.message}" }
        }
        overrides.filterKeys { uid -> events.none { it.recurrenceId == null && it.rawFields["UID"] == uid } }
            .values.flatten().forEach { warnings += "${it.summary}: RECURRENCE-ID has no matching master event" }
        return ExpandedImport(output.sortedBy { it.start }, warnings)
    }
    private val compact = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
}
