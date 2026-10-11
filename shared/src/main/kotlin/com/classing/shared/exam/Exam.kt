package com.classing.shared.exam

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

enum class AcademicMode { SCHEDULE, EXAM }

/** Exams are dated instants, not recurring lessons. The source zone survives travel. */
data class Exam(
    val id: String,
    val title: String,
    val startAt: Long,
    val endAt: Long,
    val timezone: String,
    val location: String = "",
    val note: String = "",
    val courseId: String? = null,
    val reminderMinutes: Int = 10,
    val completed: Boolean = false,
) {
    init {
        require(id.isNotBlank() && id.length <= 128)
        require(title.isNotBlank() && title.length <= 1000)
        require(startAt > 0 && endAt > startAt && endAt <= 253402300799000L)
        require(endAt - startAt <= 7L * 24 * 60 * 60 * 1000)
        ZoneId.of(timezone)
        require(location.length <= 1000 && note.length <= 4000)
        require(courseId == null || (courseId.isNotBlank() && courseId.length <= 128))
        require(reminderMinutes in 0..10080)
    }

    fun phase(now: Instant): ExamPhase = when {
        completed || now.toEpochMilli() >= endAt -> ExamPhase.FINISHED
        now.toEpochMilli() >= startAt -> ExamPhase.IN_PROGRESS
        else -> ExamPhase.UPCOMING
    }

    companion object {
        fun editInstant(local: LocalDateTime, zone: ZoneId, existingAt: Long?, existingZone: String?): Instant {
            if (existingAt != null && existingZone != null && ZoneId.of(existingZone) == zone) {
                val original = Instant.ofEpochMilli(existingAt)
                if (original.atZone(zone).toLocalDateTime() == local) return original
            }
            return localInstant(local, zone)
        }

        fun localInstant(local: LocalDateTime, zone: ZoneId): Instant {
            val offsets = zone.rules.getValidOffsets(local)
            require(offsets.isNotEmpty()) { "This local time does not exist due to daylight saving time." }
            // A repeated wall time defaults to its first occurrence; imported UTC instants remain exact.
            return local.toInstant(offsets.first())
        }
    }
}

enum class ExamPhase { UPCOMING, IN_PROGRESS, FINISHED }

fun nextExam(exams: List<Exam>, now: Instant): Exam? = exams
    .filter { !it.completed && it.endAt > now.toEpochMilli() }
    .minWithOrNull(compareBy<Exam> { it.startAt }.thenBy { it.id })

fun validateExams(exams: List<Exam>) {
    require(exams.size <= 500) { "Maximum 500 exams" }
    require(exams.map { it.id }.toSet().size == exams.size) { "Duplicate exam IDs" }
}
