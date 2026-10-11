package com.classing.shared.exam

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.*

class ExamTest {
    @org.junit.Test fun editingNotesPreservesSecondDstOccurrenceAndMilliseconds() {
        val zone = java.time.ZoneId.of("America/New_York")
        val original = java.time.Instant.parse("2026-11-01T06:30:15.123Z")
        val local = original.atZone(zone).toLocalDateTime()
        org.junit.Assert.assertEquals(original, Exam.editInstant(local, zone, original.toEpochMilli(), zone.id))
        org.junit.Assert.assertEquals(java.time.Instant.parse("2026-11-01T05:30:15.123Z"), Exam.editInstant(local, zone, null, null))
    }
    private val exam = Exam("exam-1", "Math", 1800000000000, 1800007200000, "Asia/Shanghai")
    @Test fun boundariesAndCompleted() {
        assertEquals(ExamPhase.UPCOMING, exam.phase(Instant.ofEpochMilli(exam.startAt - 1)))
        assertEquals(ExamPhase.IN_PROGRESS, exam.phase(Instant.ofEpochMilli(exam.startAt)))
        assertEquals(ExamPhase.FINISHED, exam.phase(Instant.ofEpochMilli(exam.endAt)))
        assertNull(nextExam(listOf(exam.copy(completed = true)), Instant.EPOCH))
    }
    @Test fun nextIncludesInProgressAndStableTies() {
        assertEquals(exam, nextExam(listOf(exam.copy(id="z"), exam), Instant.ofEpochMilli(exam.startAt)))
        assertNull(nextExam(listOf(exam), Instant.ofEpochMilli(exam.endAt)))
    }
    @Test fun sourceInstantSurvivesTravel() {
        val instant = Exam.localInstant(LocalDateTime.parse("2026-10-16T09:00"), ZoneId.of("Asia/Shanghai"))
        assertEquals("2026-10-16T01:00:00Z", instant.toString())
        assertEquals(18, instant.atZone(ZoneId.of("America/Los_Angeles")).hour)
    }
    @Test fun invalidDatesAndDstGapFail() {
        assertFailsWith<IllegalArgumentException> { exam.copy(endAt=exam.startAt) }
        assertFailsWith<IllegalArgumentException> { exam.copy(reminderMinutes=-1) }
        assertFails { Exam.localInstant(LocalDateTime.parse("2026-03-08T02:30"), ZoneId.of("America/New_York")) }
        assertFails { validateExams(listOf(exam, exam)) }
    }
}
