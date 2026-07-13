package com.xtawa.classingtime.screen

import com.xtawa.classingtime.data.PersistedScheduleException
import com.xtawa.classingtime.data.PersistedScheduleSnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

internal enum class ScheduleExceptionKind {
    CANCEL,
    RESCHEDULE,
    MAKE_UP,
    ;

    companion object {
        fun fromRaw(raw: String?): ScheduleExceptionKind {
            return entries.firstOrNull { it.name == raw?.trim()?.uppercase() } ?: CANCEL
        }
    }
}

internal enum class LessonEditScope {
    SingleOccurrence,
    FromThisWeek,
    WholeLesson,
}

internal data class LessonEditContext(
    val lesson: LessonUi,
    val anchorDate: LocalDate?,
    val allowedScopes: Set<LessonEditScope>,
    val isNewLesson: Boolean = false,
)

internal data class ScheduleExceptionUi(
    val id: String,
    val lessonId: String?,
    val type: ScheduleExceptionKind,
    val date: LocalDate,
    val title: String? = null,
    val teacher: String? = null,
    val location: String? = null,
    val note: String? = null,
    val dayOfWeek: DayOfWeek? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
)

internal enum class EffectiveLessonOrigin {
    BASE,
    RESCHEDULED,
    MAKE_UP,
}

internal data class EffectiveLessonOccurrence(
    val occurrenceId: String,
    val lesson: LessonUi,
    val date: LocalDate,
    val weekIndex: Int,
    val origin: EffectiveLessonOrigin,
    val sourceLessonId: String?,
    val exceptionId: String? = null,
)

internal data class ScheduleProjection(
    val currentWeekLessons: List<LessonUi>,
    val currentWeekLessonsByDay: Map<DayOfWeek, List<LessonUi>>,
    val effectiveLessonsForSync: List<LessonUi>,
)

internal data class ScheduleDisplayProjection(
    val lessons: List<LessonUi>,
    val lessonsByDay: Map<DayOfWeek, List<LessonUi>>,
    val isCurrentWeek: Boolean,
)

/**
 * Keep imported courses visible even when none of their week rules match the
 * current calendar week. The current-week projection remains authoritative for
 * reminders and "today" status; this projection is only for browsing the
 * timetable and dashboard instead of presenting a misleading empty import.
 */
internal fun buildScheduleDisplayProjection(
    baseLessons: List<LessonUi>,
    currentWeekLessons: List<LessonUi>,
): ScheduleDisplayProjection {
    val isCurrentWeek = currentWeekLessons.isNotEmpty() || baseLessons.isEmpty()
    val displayLessons = (if (isCurrentWeek) currentWeekLessons else baseLessons)
        .sortedWith(compareBy<LessonUi> { it.dayOfWeek.value }.thenBy { it.startTime })
    return ScheduleDisplayProjection(
        lessons = displayLessons,
        lessonsByDay = DayOfWeek.entries.associateWith { day ->
            displayLessons.filter { it.dayOfWeek == day }.sortedBy { it.startTime }
        },
        isCurrentWeek = isCurrentWeek,
    )
}

internal data class ScheduleStateSnapshot(
    val id: String,
    val createdAt: Long,
    val reason: String,
    val weekNumberMode: WeekNumberMode,
    val semesterWeekStartDate: LocalDate,
    val weekStartDay: DayOfWeek,
    val baseLessons: List<LessonUi>,
    val exceptions: List<ScheduleExceptionUi>,
)

internal data class ScheduleMutationResult(
    val baseLessons: List<LessonUi>,
    val exceptions: List<ScheduleExceptionUi>,
)

internal fun PersistedScheduleException.toUi(): ScheduleExceptionUi {
    return ScheduleExceptionUi(
        id = id,
        lessonId = lessonId,
        type = ScheduleExceptionKind.fromRaw(type),
        date = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now()),
        title = title,
        teacher = teacher,
        location = location,
        note = note,
        dayOfWeek = dayOfWeek?.let { DayOfWeek.of(it.coerceIn(1, 7)) },
        startTime = startMinute?.let { LocalTime.of(it / 60, it % 60) },
        endTime = endMinute?.let { LocalTime.of(it / 60, it % 60) },
    )
}

internal fun ScheduleExceptionUi.toPersisted(): PersistedScheduleException {
    return PersistedScheduleException(
        id = id,
        lessonId = lessonId,
        type = type.name,
        date = date.toString(),
        title = title,
        teacher = teacher,
        location = location,
        note = note,
        dayOfWeek = dayOfWeek?.value,
        startMinute = startTime?.let { it.hour * 60 + it.minute },
        endMinute = endTime?.let { it.hour * 60 + it.minute },
    )
}

internal fun PersistedScheduleSnapshot.toUi(): ScheduleStateSnapshot {
    return ScheduleStateSnapshot(
        id = id,
        createdAt = createdAt,
        reason = reason,
        weekNumberMode = WeekNumberMode.entries.firstOrNull { it.name == weekNumberMode } ?: WeekNumberMode.NATURAL,
        semesterWeekStartDate = runCatching { LocalDate.parse(semesterWeekStartDate) }.getOrDefault(LocalDate.now()),
        weekStartDay = runCatching { DayOfWeek.valueOf(weekStartDay) }.getOrDefault(DayOfWeek.MONDAY),
        baseLessons = baseLessons.map { it.toLessonUi() },
        exceptions = exceptions.map { it.toUi() },
    )
}

internal fun ScheduleStateSnapshot.toPersisted(): PersistedScheduleSnapshot {
    return PersistedScheduleSnapshot(
        id = id,
        createdAt = createdAt,
        reason = reason,
        weekNumberMode = weekNumberMode.name,
        semesterWeekStartDate = semesterWeekStartDate.toString(),
        weekStartDay = weekStartDay.name,
        baseLessons = baseLessons.map { it.toPersistedLesson() },
        exceptions = exceptions.map { it.toPersisted() },
    )
}

internal fun buildScheduleProjection(
    baseLessons: List<LessonUi>,
    exceptions: List<ScheduleExceptionUi>,
    weekNumberMode: WeekNumberMode,
    semesterWeekStartDate: LocalDate,
    weekStartDay: DayOfWeek,
    now: LocalDate = LocalDate.now(),
): ScheduleProjection {
    val weekStart = resolveVisibleWeekStart(now, weekStartDay)
    val currentWeekOccurrences = buildEffectiveOccurrencesForDateRange(
        baseLessons = baseLessons,
        exceptions = exceptions,
        startDate = weekStart,
        endDate = weekStart.plusDays(6),
        weekNumberMode = weekNumberMode,
        semesterWeekStartDate = semesterWeekStartDate,
        weekStartDay = weekStartDay,
    )
    val currentWeekLessons = currentWeekOccurrences.map { it.lesson }
    val currentWeekLessonsByDay = DayOfWeek.entries.associateWith { day ->
        currentWeekOccurrences
            .filter { it.date.dayOfWeek == day }
            .sortedBy { it.lesson.startTime }
            .map { it.lesson }
    }

    return ScheduleProjection(
        currentWeekLessons = currentWeekLessons,
        currentWeekLessonsByDay = currentWeekLessonsByDay,
        effectiveLessonsForSync = buildFlattenedEffectiveLessons(
            baseLessons = baseLessons,
            exceptions = exceptions,
            weekNumberMode = weekNumberMode,
            semesterWeekStartDate = semesterWeekStartDate,
        ),
    )
}

internal fun buildEffectiveOccurrencesForDateRange(
    baseLessons: List<LessonUi>,
    exceptions: List<ScheduleExceptionUi>,
    startDate: LocalDate,
    endDate: LocalDate,
    weekNumberMode: WeekNumberMode,
    semesterWeekStartDate: LocalDate,
    weekStartDay: DayOfWeek = DayOfWeek.MONDAY,
): List<EffectiveLessonOccurrence> {
    if (endDate.isBefore(startDate)) return emptyList()
    val exceptionMap = exceptions.groupBy { it.date }
    val occurrences = mutableListOf<EffectiveLessonOccurrence>()
    var date = startDate
    while (!date.isAfter(endDate)) {
        val weekIndex = weekIndexForMode(
            date = date,
            mode = weekNumberMode,
            semesterWeekStartDate = semesterWeekStartDate,
            weekStartDay = weekStartDay,
        )
        val dayLessons = baseLessons
            .filter { it.dayOfWeek == date.dayOfWeek && it.matchesWeek(weekIndex) }
            .sortedBy { it.startTime }
            .map { lesson ->
                EffectiveLessonOccurrence(
                    occurrenceId = "${lesson.id}:${date}",
                    lesson = lesson,
                    date = date,
                    weekIndex = weekIndex,
                    origin = EffectiveLessonOrigin.BASE,
                    sourceLessonId = lesson.id,
                )
            }
            .toMutableList()
        exceptionMap[date].orEmpty().forEach { exception ->
            when (exception.type) {
                ScheduleExceptionKind.CANCEL -> {
                    if (!exception.lessonId.isNullOrBlank()) {
                        dayLessons.removeAll { it.sourceLessonId == exception.lessonId }
                    }
                }

                ScheduleExceptionKind.RESCHEDULE -> {
                    if (!exception.lessonId.isNullOrBlank()) {
                        dayLessons.removeAll { it.sourceLessonId == exception.lessonId }
                    }
                    dayLessons += exception.toOccurrence(date, weekIndex, EffectiveLessonOrigin.RESCHEDULED)
                }

                ScheduleExceptionKind.MAKE_UP -> {
                    dayLessons += exception.toOccurrence(date, weekIndex, EffectiveLessonOrigin.MAKE_UP)
                }
            }
        }
        occurrences += dayLessons.sortedBy { it.lesson.startTime }
        date = date.plusDays(1)
    }
    return occurrences.sortedWith(compareBy<EffectiveLessonOccurrence> { it.date }.thenBy { it.lesson.startTime })
}

internal fun buildFlattenedEffectiveLessons(
    baseLessons: List<LessonUi>,
    exceptions: List<ScheduleExceptionUi>,
    weekNumberMode: WeekNumberMode,
    semesterWeekStartDate: LocalDate,
): List<LessonUi> {
    val occurrences = buildOccurrencesForWeekRange(
        baseLessons = baseLessons,
        exceptions = exceptions,
        weekNumberMode = weekNumberMode,
        semesterWeekStartDate = semesterWeekStartDate,
        weekRange = DEFAULT_START_WEEK..DEFAULT_END_WEEK,
    )
    val grouped = occurrences.groupBy {
        FlattenedLessonKey(
            sourceLessonId = it.sourceLessonId,
            title = it.lesson.title,
            teacher = it.lesson.teacher,
            location = it.lesson.location,
            note = it.lesson.note,
            dayOfWeek = it.date.dayOfWeek,
            startTime = it.lesson.startTime,
            endTime = it.lesson.endTime,
        )
    }
    return grouped.flatMap { (key, items) ->
        compressWeekRuns(items.map { it.weekIndex }.distinct().sorted()).map { run ->
            LessonUi(
                id = "${key.sourceLessonId ?: key.title.hashCode()}-${run.startWeek}-${run.endWeek}-${key.dayOfWeek.value}-${key.startTime}",
                title = key.title,
                teacher = key.teacher,
                location = key.location,
                note = key.note,
                dayOfWeek = key.dayOfWeek,
                startTime = key.startTime,
                endTime = key.endTime,
                startWeek = run.startWeek,
                endWeek = run.endWeek,
                weekParity = run.weekParity,
            )
        }
    }.sortedWith(compareBy<LessonUi> { it.dayOfWeek.value }.thenBy { it.startTime }.thenBy { it.startWeek })
}

internal fun createScheduleSnapshot(
    reason: String,
    weekNumberMode: WeekNumberMode,
    semesterWeekStartDate: LocalDate,
    weekStartDay: DayOfWeek = DayOfWeek.MONDAY,
    baseLessons: List<LessonUi>,
    exceptions: List<ScheduleExceptionUi>,
    createdAt: Long = System.currentTimeMillis(),
): ScheduleStateSnapshot {
    return ScheduleStateSnapshot(
        id = "snapshot-$createdAt-${reason.hashCode()}",
        createdAt = createdAt,
        reason = reason,
        weekNumberMode = weekNumberMode,
        semesterWeekStartDate = semesterWeekStartDate,
        weekStartDay = weekStartDay,
        baseLessons = baseLessons.sortedWith(compareBy<LessonUi> { it.dayOfWeek.value }.thenBy { it.startTime }),
        exceptions = exceptions.sortedWith(compareBy<ScheduleExceptionUi> { it.date }.thenBy { it.startTime }),
    )
}

internal fun capSnapshots(
    snapshots: List<ScheduleStateSnapshot>,
    maxSize: Int = 8,
): List<ScheduleStateSnapshot> {
    return snapshots.sortedByDescending { it.createdAt }.take(maxSize)
}

internal fun restoreOriginalOccurrence(
    exceptions: List<ScheduleExceptionUi>,
    lessonId: String,
    date: LocalDate,
): List<ScheduleExceptionUi> = exceptions.filterNot { it.lessonId == lessonId && it.date == date }

internal fun resolveVisibleWeekStart(now: LocalDate, weekStartDay: DayOfWeek): LocalDate {
    val offset = (7 + (now.dayOfWeek.value - weekStartDay.value)) % 7
    return now.minusDays(offset.toLong())
}

internal fun resolveAnchorWeek(
    anchorDate: LocalDate,
    weekNumberMode: WeekNumberMode,
    semesterWeekStartDate: LocalDate,
): Int {
    return weekIndexForMode(
        date = anchorDate,
        mode = weekNumberMode,
        semesterWeekStartDate = semesterWeekStartDate,
    ).coerceIn(DEFAULT_START_WEEK, DEFAULT_END_WEEK)
}

internal fun LessonUi.matchesWeek(weekIndex: Int): Boolean {
    if (weekIndex !in startWeek..endWeek) return false
    return when (weekParity) {
        LessonWeekParity.ALL -> true
        LessonWeekParity.ODD -> weekIndex % 2 == 1
        LessonWeekParity.EVEN -> weekIndex % 2 == 0
    }
}

private data class FlattenedLessonKey(
    val sourceLessonId: String?,
    val title: String,
    val teacher: String?,
    val location: String?,
    val note: String?,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
)

private data class WeekRun(
    val startWeek: Int,
    val endWeek: Int,
    val weekParity: LessonWeekParity,
)

private fun buildOccurrencesForWeekRange(
    baseLessons: List<LessonUi>,
    exceptions: List<ScheduleExceptionUi>,
    weekNumberMode: WeekNumberMode,
    semesterWeekStartDate: LocalDate,
    weekRange: IntRange,
): List<EffectiveLessonOccurrence> {
    val startDate = dateForWeekWindowStart(weekRange.first, weekNumberMode, semesterWeekStartDate)
    val endDate = dateForWeekWindowStart(weekRange.last, weekNumberMode, semesterWeekStartDate).plusDays(6)
    return buildEffectiveOccurrencesForDateRange(
        baseLessons = baseLessons,
        exceptions = exceptions,
        startDate = startDate,
        endDate = endDate,
        weekNumberMode = weekNumberMode,
        semesterWeekStartDate = semesterWeekStartDate,
    ).filter { it.weekIndex in weekRange }
}

private fun dateForWeekWindowStart(
    weekIndex: Int,
    weekNumberMode: WeekNumberMode,
    semesterWeekStartDate: LocalDate,
    referenceDate: LocalDate = LocalDate.now(),
): LocalDate {
    val anchor = when (weekNumberMode) {
        WeekNumberMode.SEMESTER -> semesterWeekStartDate
        WeekNumberMode.NATURAL -> {
            val yearStart = LocalDate.of(referenceDate.year, 1, 4)
            val offset = ((yearStart.dayOfWeek.value - DayOfWeek.MONDAY.value) + 7) % 7
            yearStart.minusDays(offset.toLong())
        }
    }
    return anchor.plusDays(((weekIndex - 1) * 7L).coerceAtLeast(0L))
}

private fun compressWeekRuns(weeks: List<Int>): List<WeekRun> {
    if (weeks.isEmpty()) return emptyList()
    if (weeks.size == 1) {
        return listOf(WeekRun(weeks.first(), weeks.first(), LessonWeekParity.ALL))
    }
    val runs = mutableListOf<WeekRun>()
    var index = 0
    while (index < weeks.size) {
        val start = weeks[index]
        var end = start
        var step = 1
        if (index + 1 < weeks.size) {
            val candidateStep = weeks[index + 1] - weeks[index]
            step = if (candidateStep == 2 && start % 2 == weeks[index + 1] % 2) 2 else 1
        }
        var cursor = index + 1
        while (cursor < weeks.size) {
            val diff = weeks[cursor] - end
            if (diff != step) break
            end = weeks[cursor]
            cursor += 1
        }
        runs += WeekRun(
            startWeek = start,
            endWeek = end,
            weekParity = if (step == 2) {
                if (start % 2 == 0) LessonWeekParity.EVEN else LessonWeekParity.ODD
            } else {
                LessonWeekParity.ALL
            },
        )
        index = cursor
    }
    return runs
}

private fun ScheduleExceptionUi.toOccurrence(
    date: LocalDate,
    weekIndex: Int,
    origin: EffectiveLessonOrigin,
): EffectiveLessonOccurrence {
    val safeDay = dayOfWeek ?: date.dayOfWeek
    val safeStart = startTime ?: LocalTime.of(8, 0)
    val safeEnd = endTime?.takeIf { it.isAfter(safeStart) } ?: safeStart.plusMinutes(90)
    val safeTitle = title?.ifBlank { null } ?: "Untitled"
    val lessonId = lessonId ?: "exception-$id"
    return EffectiveLessonOccurrence(
        occurrenceId = "$id:$date",
        lesson = LessonUi(
            id = "$lessonId:$date",
            title = safeTitle,
            teacher = teacher,
            location = location,
            note = note,
            dayOfWeek = safeDay,
            startTime = safeStart,
            endTime = safeEnd,
            startWeek = weekIndex,
            endWeek = weekIndex,
            weekParity = LessonWeekParity.ALL,
        ),
        date = date,
        weekIndex = weekIndex,
        origin = origin,
        sourceLessonId = lessonId,
        exceptionId = id,
    )
}
