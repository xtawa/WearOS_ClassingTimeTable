package com.xtawa.classingtime.sync

import com.xtawa.classingtime.data.PersistedLesson
import com.xtawa.classingtime.data.PersistedScheduleException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WearSyncPayloadPlannerTest {
    private val lesson = PersistedLesson("math", "Math", null, "A1", null, 1, 480, 540, 1, 16, "ALL")
    private val exception = PersistedScheduleException(
        "cancel-math", "math", "CANCEL", "2026-07-13", null, null, null, null, null, null, null,
    )

    @Test
    fun firstPublishIsFullAndCarriesExceptions() {
        val plan = planWearSyncPayload(listOf(lesson), listOf(exception), previous = null, forceFull = false)

        assertEquals("FULL", plan.mode)
        assertEquals(listOf(lesson), plan.lessons)
        assertEquals(listOf(exception), plan.exceptions)
    }

    @Test
    fun laterPublishContainsOnlyChangesAndDeletes() {
        val first = planWearSyncPayload(listOf(lesson), listOf(exception), previous = null, forceFull = false)
        val changed = lesson.copy(title = "Advanced Math")
        val delta = planWearSyncPayload(listOf(changed), emptyList(), first.nextBaseline, forceFull = false)

        assertEquals("INCREMENTAL", delta.mode)
        assertEquals(listOf(changed), delta.lessons)
        assertTrue(delta.exceptions.isEmpty())
        assertEquals(setOf("cancel-math"), delta.deletedExceptionIds)
    }

    @Test
    fun forceFullRecoversEvenWhenBaselineExists() {
        val first = planWearSyncPayload(listOf(lesson), emptyList(), previous = null, forceFull = false)
        val recovered = planWearSyncPayload(listOf(lesson), emptyList(), first.nextBaseline, forceFull = true)

        assertEquals("FULL", recovered.mode)
        assertEquals(listOf(lesson), recovered.lessons)
        assertTrue(recovered.deletedLessonIds.isEmpty())
    }

    @Test
    fun baselineIsUsedOnlyForTheSameSingleWearNode() {
        val baseline = planWearSyncPayload(listOf(lesson), emptyList(), previous = null, forceFull = false).nextBaseline
        val stored = WearSyncBaselineRecord(nodeId = "watch-a", baseline = baseline)

        assertSame(baseline, selectWearSyncBaseline(setOf("watch-a"), stored, forceFull = false))
        assertNull(selectWearSyncBaseline(setOf("watch-b"), stored, forceFull = false))
        assertNull(selectWearSyncBaseline(setOf("watch-a", "watch-b"), stored, forceFull = false))
        assertNull(selectWearSyncBaseline(emptySet(), stored, forceFull = false))
    }

    @Test
    fun forceFullAlwaysIgnoresStoredBaseline() {
        val baseline = planWearSyncPayload(listOf(lesson), emptyList(), previous = null, forceFull = false).nextBaseline
        val stored = WearSyncBaselineRecord(nodeId = "watch-a", baseline = baseline)

        assertNull(selectWearSyncBaseline(setOf("watch-a"), stored, forceFull = true))
    }
}
