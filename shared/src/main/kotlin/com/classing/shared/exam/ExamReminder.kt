package com.classing.shared.exam

data class ExamReminder(val exam: Exam, val fireAt: Long, val key: String)

fun examReminderKey(exam: Exam): String = "${exam.id}:${exam.startAt}:${exam.endAt}:${exam.reminderMinutes}"

/** One alarm at a time avoids Android's per-application alarm limit for large exam lists. */
fun nextExamReminder(exams: List<Exam>, now: Long, fired: Set<String>): ExamReminder? = exams
    .filter { !it.completed && it.startAt >= now && examReminderKey(it) !in fired }
    .map { ExamReminder(it, (it.startAt - it.reminderMinutes * 60_000L).coerceAtLeast(now + 1000), examReminderKey(it)) }
    .minWithOrNull(compareBy<ExamReminder> { it.fireAt }.thenBy { it.exam.startAt }.thenBy { it.exam.id })

fun canDeliverExamReminder(exam: Exam, key: String, now: Long): Boolean = !exam.completed &&
    examReminderKey(exam) == key && now >= exam.startAt - exam.reminderMinutes * 60_000L &&
    now < exam.endAt && now <= exam.startAt + 15 * 60_000L
