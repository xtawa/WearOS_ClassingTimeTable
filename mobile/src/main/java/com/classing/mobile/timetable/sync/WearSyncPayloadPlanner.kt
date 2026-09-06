package com.xtawa.classingtime.sync

import com.xtawa.classingtime.data.PersistedLesson
import com.xtawa.classingtime.data.PersistedScheduleException

internal data class WearSyncBaseline(
    val lessonFingerprints: Map<String, String>,
    val exceptionFingerprints: Map<String, String>,
)

internal data class WearSyncBaselineRecord(
    val nodeId: String,
    val baseline: WearSyncBaseline,
)

internal data class WearSyncPayloadPlan(
    val mode: String,
    val lessons: List<PersistedLesson>,
    val exceptions: List<PersistedScheduleException>,
    val deletedLessonIds: Set<String>,
    val deletedExceptionIds: Set<String>,
    val nextBaseline: WearSyncBaseline,
)

internal fun selectWearSyncBaseline(
    connectedNodeIds: Set<String>,
    stored: WearSyncBaselineRecord?,
    forceFull: Boolean,
): WearSyncBaseline? {
    if (forceFull || connectedNodeIds.size != 1 || stored == null) return null
    return stored.baseline.takeIf { stored.nodeId == connectedNodeIds.single() }
}

internal fun planWearSyncPayload(
    lessons: List<PersistedLesson>,
    exceptions: List<PersistedScheduleException>,
    previous: WearSyncBaseline?,
    forceFull: Boolean,
): WearSyncPayloadPlan {
    val lessonFingerprints = lessons.associate { it.id to it.syncFingerprint() }
    val exceptionFingerprints = exceptions.associate { it.id to it.syncFingerprint() }
    val nextBaseline = WearSyncBaseline(lessonFingerprints, exceptionFingerprints)
    if (forceFull || previous == null) {
        return WearSyncPayloadPlan(
            mode = "FULL",
            lessons = lessons,
            exceptions = exceptions,
            deletedLessonIds = emptySet(),
            deletedExceptionIds = emptySet(),
            nextBaseline = nextBaseline,
        )
    }

    return WearSyncPayloadPlan(
        mode = "INCREMENTAL",
        lessons = lessons.filter { previous.lessonFingerprints[it.id] != lessonFingerprints[it.id] },
        exceptions = exceptions.filter { previous.exceptionFingerprints[it.id] != exceptionFingerprints[it.id] },
        deletedLessonIds = previous.lessonFingerprints.keys - lessonFingerprints.keys,
        deletedExceptionIds = previous.exceptionFingerprints.keys - exceptionFingerprints.keys,
        nextBaseline = nextBaseline,
    )
}

private fun PersistedLesson.syncFingerprint(): String = listOf(
    title,
    teacher.orEmpty(),
    location.orEmpty(),
    note.orEmpty(),
    dayOfWeek,
    startMinute,
    endMinute,
    startWeek,
    endWeek,
    weekParity,
).joinToString("\u001f")

private fun PersistedScheduleException.syncFingerprint(): String = listOf(
    lessonId.orEmpty(),
    type,
    date,
    title.orEmpty(),
    teacher.orEmpty(),
    location.orEmpty(),
    note.orEmpty(),
    dayOfWeek ?: 0,
    startMinute ?: -1,
    endMinute ?: -1,
).joinToString("\u001f")
