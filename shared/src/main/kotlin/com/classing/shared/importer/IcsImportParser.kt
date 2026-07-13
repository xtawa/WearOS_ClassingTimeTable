package com.classing.shared.importer

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class IcsImportParser : ImportParser {
    override fun parse(raw: String): ImportResult {
        if (!raw.contains("BEGIN:VEVENT")) return ImportResult.Failure("ICS missing VEVENT")

        val lines = unfold(raw)
        val events = mutableListOf<ParsedEvent>()
        val dropped = mutableListOf<String>()
        var block = mutableMapOf<String, String>()
        var inEvent = false

        lines.forEach { line ->
            when {
                line == "BEGIN:VEVENT" -> {
                    inEvent = true
                    block = mutableMapOf()
                }
                line == "END:VEVENT" && inEvent -> {
                    events += toParsedEvent(block)
                    inEvent = false
                }
                inEvent -> {
                    val idx = line.indexOf(':')
                    if (idx <= 0) {
                        dropped += line
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

        val payload = ParsedSchedulePayload(events, "ICS", warnings = emptyList())
        return if (dropped.isEmpty()) ImportResult.Success(payload) else ImportResult.PartialSuccess(payload, dropped)
    }

    private fun toParsedEvent(fields: Map<String, String>): ParsedEvent {
        return ParsedEvent(
            summary = fields["SUMMARY"].orEmpty().ifBlank { "未命名课程" },
            dtStart = parseInstant(fields["DTSTART"], fields["DTSTART_TZID"]),
            dtEnd = parseInstant(fields["DTEND"], fields["DTEND_TZID"]),
            location = fields["LOCATION"],
            description = fields["DESCRIPTION"],
            rRule = fields["RRULE"],
            exDates = fields["EXDATE"]?.split(',')?.mapNotNull { parseInstant(it, fields["EXDATE_TZID"]) }.orEmpty(),
            recurrenceId = parseInstant(fields["RECURRENCE-ID"], fields["RECURRENCE-ID_TZID"]),
            rawFields = fields,
        )
    }

    private fun parseInstant(value: String?, tzid: String? = null): Instant? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            when {
                value.endsWith('Z') -> Instant.parse(value.replace("T", "T").let { normalizeUtc(it) })
                value.length == 8 -> LocalDateTime.parse("${value}T000000", DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                    .atZone(resolveZone(tzid)).toInstant()
                else -> LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                    .atZone(resolveZone(tzid)).toInstant()
            }
        }.getOrNull()
    }

    private fun resolveZone(tzid: String?): ZoneId = runCatching {
        if (tzid.isNullOrBlank()) ZoneId.systemDefault() else ZoneId.of(tzid)
    }.getOrDefault(ZoneId.systemDefault())

    private fun normalizeUtc(raw: String): String {
        val compact = raw.removeSuffix("Z")
        val parsed = LocalDateTime.parse(compact, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
        return parsed.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)
    }

    private fun unfold(raw: String): List<String> {
        val base = raw.replace("\r\n", "\n").split('\n')
        val result = mutableListOf<String>()
        base.forEach { line ->
            if ((line.startsWith(" ") || line.startsWith("\t")) && result.isNotEmpty()) {
                result[result.lastIndex] = result.last() + line.trim()
            } else {
                result += line.trimEnd()
            }
        }
        return result
    }
}
