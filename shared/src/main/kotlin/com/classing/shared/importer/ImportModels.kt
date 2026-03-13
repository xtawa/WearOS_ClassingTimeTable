package com.classing.shared.importer

import java.time.Instant

interface ImportParser {
    fun parse(raw: String): ImportResult
}

data class ParsedSchedulePayload(
    val events: List<ParsedEvent>,
    val source: String,
    val warnings: List<String>,
)

data class ParsedEvent(
    val summary: String,
    val dtStart: Instant?,
    val dtEnd: Instant?,
    val location: String?,
    val description: String?,
    val rRule: String?,
    val exDates: List<Instant>,
    val rawFields: Map<String, String>,
)

sealed interface ImportResult {
    data class Success(val payload: ParsedSchedulePayload) : ImportResult
    data class PartialSuccess(val payload: ParsedSchedulePayload, val droppedLines: List<String>) : ImportResult
    data class Failure(val reason: String) : ImportResult
}

data class CourseDraft(
    val title: String,
    val location: String?,
    val note: String?,
    val start: Instant?,
    val end: Instant?,
    val recurrence: String?,
    val excludes: List<Instant>,
    val sourceRaw: Map<String, String>,
)

class ScheduleImportAdapter {
    fun toDrafts(result: ImportResult): List<CourseDraft> {
        val payload = when (result) {
            is ImportResult.Success -> result.payload
            is ImportResult.PartialSuccess -> result.payload
            is ImportResult.Failure -> return emptyList()
        }
        return payload.events.map {
            CourseDraft(
                title = it.summary,
                location = it.location,
                note = listOfNotNull(it.description, it.rRule?.let { r -> "RRULE=$r" }).joinToString("\n").ifBlank { null },
                start = it.dtStart,
                end = it.dtEnd,
                recurrence = it.rRule,
                excludes = it.exDates,
                sourceRaw = it.rawFields,
            )
        }
    }
}
