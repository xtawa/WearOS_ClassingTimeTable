package com.classing.shared.importer

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class IcsImportParser : ImportParser {
    override fun parse(raw: String): ImportResult {
        if (!raw.contains("BEGIN:VEVENT")) return ImportResult.Failure("ICS missing VEVENT")

        val lines = unfold(raw)
        val events = mutableListOf<ParsedEvent>()
        val droppedLines = mutableListOf<String>()
        val warnings = mutableListOf<ImportWarning>()
        var skippedEvents = 0
        var block = mutableMapOf<String, String>()
        var inEvent = false

        lines.forEach { line ->
            when {
                line == "BEGIN:VEVENT" -> {
                    inEvent = true
                    block = mutableMapOf()
                }
                line == "END:VEVENT" && inEvent -> {
                    val outcome = toParsedEvent(block)
                    warnings += outcome.warnings
                    if (outcome.event == null) {
                        skippedEvents += 1
                    } else {
                        events += outcome.event
                    }
                    inEvent = false
                }
                inEvent -> {
                    val idx = line.indexOf(':')
                    if (idx <= 0) {
                        droppedLines += line
                        warnings += ImportWarning(
                            eventTitle = block["SUMMARY"],
                            field = "LINE",
                            rawValue = line,
                            reason = "Malformed content line",
                        )
                    } else {
                        val keyPart = line.substring(0, idx)
                        val key = keyPart.substringBefore(';').trim().uppercase()
                        val value = line.substring(idx + 1).trim()
                        block[key] = when (key) {
                            "EXDATE" -> listOfNotNull(block[key], value).joinToString(",")
                            else -> value
                        }
                        keyPart.substringAfter(';', "")
                            .split(';')
                            .mapNotNull { parameter ->
                                val split = parameter.indexOf('=')
                                if (split <= 0) null else parameter.substring(0, split).uppercase() to parameter.substring(split + 1)
                            }
                            .forEach { (name, parameterValue) -> block["${key}_$name"] = parameterValue }
                    }
                }
            }
        }

        val warningMessages = warnings.map { it.toDisplayText() }
        val payload = ParsedSchedulePayload(
            events = events,
            source = "ICS",
            warnings = warningMessages,
            summary = ImportSummary(
                importedCount = events.size,
                skippedCount = skippedEvents,
                warnings = warnings,
            ),
        )
        return if (droppedLines.isEmpty() && warnings.isEmpty() && skippedEvents == 0) {
            ImportResult.Success(payload)
        } else {
            ImportResult.PartialSuccess(payload, droppedLines + warningMessages)
        }
    }

    private fun toParsedEvent(fields: Map<String, String>): EventParseOutcome {
        val title = fields["SUMMARY"].orEmpty().ifBlank { "未命名课程" }
        val warnings = mutableListOf<ImportWarning>()

        val dtStart = when (val result = parseInstant(fields["DTSTART"], fields["DTSTART_TZID"], "DTSTART", required = true)) {
            is FieldParseResult.Success -> result.value
            is FieldParseResult.Failure -> {
                warnings += result.toWarning(title)
                return EventParseOutcome(event = null, warnings = warnings)
            }
        }
        val dtEnd = when (val result = parseInstant(fields["DTEND"], fields["DTEND_TZID"], "DTEND", required = true)) {
            is FieldParseResult.Success -> result.value
            is FieldParseResult.Failure -> {
                warnings += result.toWarning(title)
                return EventParseOutcome(event = null, warnings = warnings)
            }
        }

        val exDates = fields["EXDATE"]
            ?.split(',')
            ?.mapNotNull { rawExDate ->
                when (val result = parseInstant(rawExDate, fields["EXDATE_TZID"], "EXDATE", required = false)) {
                    is FieldParseResult.Success -> result.value
                    is FieldParseResult.Failure -> {
                        warnings += result.toWarning(title)
                        null
                    }
                }
            }
            .orEmpty()

        val recurrenceId = when (val result = parseInstant(fields["RECURRENCE-ID"], fields["RECURRENCE-ID_TZID"], "RECURRENCE-ID", required = false)) {
            is FieldParseResult.Success -> result.value
            is FieldParseResult.Failure -> {
                warnings += result.toWarning(title)
                null
            }
        }

        return EventParseOutcome(
            event = ParsedEvent(
                summary = title,
                dtStart = dtStart,
                dtEnd = dtEnd,
                location = fields["LOCATION"],
                description = fields["DESCRIPTION"],
                rRule = fields["RRULE"],
                exDates = exDates,
                recurrenceId = recurrenceId,
                rawFields = fields,
            ),
            warnings = warnings,
        )
    }

    private fun parseInstant(
        value: String?,
        tzid: String? = null,
        field: String,
        required: Boolean,
    ): FieldParseResult<Instant?> {
        if (value.isNullOrBlank()) {
            return if (required) {
                FieldParseResult.Failure(field = field, rawValue = "", reason = "Missing required field")
            } else {
                FieldParseResult.Success(null)
            }
        }

        val text = value.trim()
        return runCatching {
            when {
                text.endsWith('Z') -> Instant.parse(normalizeUtc(text))
                text.length == 8 -> {
                    val zone = when (val zoneResult = resolveZone(tzid, field)) {
                        is FieldParseResult.Success -> zoneResult.value
                        is FieldParseResult.Failure -> return zoneResult
                    }
                    LocalDate.parse(text, DateTimeFormatter.BASIC_ISO_DATE)
                        .atStartOfDay(zone)
                        .toInstant()
                }
                else -> {
                    val zone = when (val zoneResult = resolveZone(tzid, field)) {
                        is FieldParseResult.Success -> zoneResult.value
                        is FieldParseResult.Failure -> return zoneResult
                    }
                    LocalDateTime.parse(text, ICS_DATE_TIME_FORMATTER)
                        .atZone(zone)
                        .toInstant()
                }
            }
        }.fold(
            onSuccess = { FieldParseResult.Success(it) },
            onFailure = { FieldParseResult.Failure(field = field, rawValue = text, reason = "Invalid date/time value") },
        )
    }

    private fun resolveZone(tzid: String?, field: String): FieldParseResult<ZoneId> {
        if (tzid.isNullOrBlank()) return FieldParseResult.Success(ZoneId.systemDefault())
        return runCatching { ZoneId.of(tzid) }.fold(
            onSuccess = { FieldParseResult.Success(it) },
            onFailure = {
                FieldParseResult.Failure(
                    field = "${field}_TZID",
                    rawValue = tzid,
                    reason = "Invalid time zone id",
                )
            },
        )
    }

    private fun normalizeUtc(raw: String): String {
        val compact = raw.removeSuffix("Z")
        val parsed = LocalDateTime.parse(compact, ICS_DATE_TIME_FORMATTER)
        return parsed.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)
    }

    private fun unfold(raw: String): List<String> {
        val base = raw.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val result = mutableListOf<String>()
        base.forEach { line ->
            if ((line.startsWith(" ") || line.startsWith("\t")) && result.isNotEmpty()) {
                result[result.lastIndex] = result.last() + line.drop(1)
            } else {
                result += line.trimEnd()
            }
        }
        return result
    }

    private fun FieldParseResult.Failure.toWarning(eventTitle: String): ImportWarning {
        return ImportWarning(
            eventTitle = eventTitle,
            field = field,
            rawValue = rawValue,
            reason = reason,
        )
    }

    private fun ImportWarning.toDisplayText(): String {
        val title = eventTitle?.takeIf { it.isNotBlank() }?.let { "$it: " }.orEmpty()
        val raw = rawValue.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        return "$title$field $reason$raw"
    }

    private data class EventParseOutcome(
        val event: ParsedEvent?,
        val warnings: List<ImportWarning>,
    )

    private sealed interface FieldParseResult<out T> {
        data class Success<T>(val value: T) : FieldParseResult<T>
        data class Failure(
            val field: String,
            val rawValue: String,
            val reason: String,
        ) : FieldParseResult<Nothing>
    }

    private companion object {
        val ICS_DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    }
}
