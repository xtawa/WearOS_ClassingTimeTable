package com.classing.shared.exam

import org.junit.Assert.*
import org.junit.Test

class ExamReminderTest {
    private val now = 1_790_000_000_000L
    private fun exam(id: String = "a", start: Long = now + 3600000, minutes: Int = 10) =
        Exam(id, "Exam", start, start + 7200000, "Asia/Shanghai", reminderMinutes = minutes)
    @Test fun earlierReminderCanBelongToLaterExam() {
        val early = exam("early", now + 3600000, 10)
        val later = exam("later", now + 7200000, 90)
        assertEquals("later", nextExamReminder(listOf(early, later), now, emptySet())!!.exam.id)
    }
    @Test fun completedPastAndAlreadyDeliveredAreNotScheduled() {
        val delivered = exam()
        assertNull(nextExamReminder(listOf(delivered, exam("past", now-1), exam("done").copy(completed=true)), now, setOf(examReminderKey(delivered))))
    }
    @Test fun editingTimeInvalidatesOldAlarmButEditingTitleDoesNotDuplicateIt() {
        val original = exam()
        val key = examReminderKey(original)
        assertFalse(canDeliverExamReminder(original.copy(startAt=original.startAt+60000), key, original.startAt))
        assertNull(nextExamReminder(listOf(original.copy(title="Renamed")), now, setOf(key)))
        assertNotNull(nextExamReminder(listOf(original.copy(startAt=original.startAt+60000)), now, setOf(key)))
    }
    @Test fun startTimeReminderAllowsSmallDeliveryDelayButNotAnEndedExam() {
        val exam = exam(minutes=0)
        val key = examReminderKey(exam)
        assertFalse(canDeliverExamReminder(exam, key, exam.startAt-1))
        assertTrue(canDeliverExamReminder(exam, key, exam.startAt+1000))
        assertFalse(canDeliverExamReminder(exam, key, exam.startAt+15*60000+1))
        assertFalse(canDeliverExamReminder(exam, key, exam.endAt))
    }
}
