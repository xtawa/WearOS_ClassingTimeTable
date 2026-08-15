package com.xtawa.classingtime.ui.home

import com.xtawa.classingtime.screen.LessonUi
import com.xtawa.classingtime.ui.theme.classingCourseAccent
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

internal fun resolveHomeUiState(
    now: LocalDateTime,
    lessonsByDay: Map<DayOfWeek, List<LessonUi>>,
    hasImportedSchedule: Boolean,
): HomeUiState = resolveHomeUiState(
    now = now,
    lessonsForDate = { date -> lessonsByDay[date.dayOfWeek].orEmpty() },
    hasImportedSchedule = hasImportedSchedule,
)

internal fun resolveHomeUiState(
    now: LocalDateTime,
    lessonsForDate: (LocalDate) -> List<LessonUi>,
    hasImportedSchedule: Boolean,
): HomeUiState {
    val today = now.toLocalDate()
    val todayCourses = lessonsForDate(today)
        .sortedBy { it.startTime }
        .map { it.toHomeCourse(today) }

    val currentCourse = todayCourses.firstOrNull { course ->
        val start = LocalDateTime.of(today, course.startTime)
        val end = LocalDateTime.of(today, course.endTime)
        !now.isBefore(start) && now.isBefore(end)
    }
    val nextToday = todayCourses.firstOrNull { course ->
        now.isBefore(LocalDateTime.of(today, course.startTime))
    }
    val previousToday = todayCourses.lastOrNull { course ->
        !now.isBefore(LocalDateTime.of(today, course.endTime))
    }
    val nextAcademicCourse = findNextAcademicCourse(
        now = now,
        lessonsForDate = lessonsForDate,
    )

    if (currentCourse != null) {
        val start = LocalDateTime.of(today, currentCourse.startTime)
        val end = LocalDateTime.of(today, currentCourse.endTime)
        val durationMillis = Duration.between(start, end).toMillis().coerceAtLeast(1L)
        val elapsedMillis = Duration.between(start, now).toMillis().coerceIn(0L, durationMillis)
        return HomeUiState(
            phase = HomePhase.InClass,
            now = now.toLocalTime(),
            date = today,
            primaryCourse = currentCourse,
            nextCourse = nextToday ?: nextAcademicCourse,
            futureCourses = todayCourses.filter { it.startTime.isAfter(currentCourse.startTime) }.take(2),
            todayCourseCount = todayCourses.size,
            remainingMinutes = minutesUntil(now, end),
            classProgress = elapsedMillis.toFloat() / durationMillis.toFloat(),
            hasImportedSchedule = hasImportedSchedule,
        )
    }

    if (nextToday != null) {
        val start = LocalDateTime.of(today, nextToday.startTime)
        val future = todayCourses.filter { it.startTime.isAfter(nextToday.startTime) }.take(2)
        if (previousToday != null) {
            return HomeUiState(
                phase = HomePhase.Break,
                now = now.toLocalTime(),
                date = today,
                primaryCourse = nextToday,
                nextCourse = nextToday,
                futureCourses = listOf(nextToday) + future.take(1),
                todayCourseCount = todayCourses.size,
                breakMinutes = minutesUntil(now, start),
                hasImportedSchedule = hasImportedSchedule,
            )
        }
        return HomeUiState(
            phase = HomePhase.Upcoming,
            now = now.toLocalTime(),
            date = today,
            primaryCourse = nextToday,
            nextCourse = future.firstOrNull(),
            futureCourses = listOf(nextToday) + future,
            todayCourseCount = todayCourses.size,
            countdownMinutes = minutesUntil(now, start),
            hasImportedSchedule = hasImportedSchedule,
        )
    }

    if (todayCourses.isNotEmpty()) {
        return HomeUiState(
            phase = HomePhase.Finished,
            now = now.toLocalTime(),
            date = today,
            primaryCourse = null,
            nextCourse = nextAcademicCourse,
            futureCourses = emptyList(),
            todayCourseCount = todayCourses.size,
            hasImportedSchedule = hasImportedSchedule,
        )
    }

    return HomeUiState(
        phase = HomePhase.NoClasses,
        now = now.toLocalTime(),
        date = today,
        primaryCourse = null,
        nextCourse = nextAcademicCourse,
        futureCourses = emptyList(),
        todayCourseCount = 0,
        hasImportedSchedule = hasImportedSchedule,
    )
}

private fun findNextAcademicCourse(
    now: LocalDateTime,
    lessonsForDate: (LocalDate) -> List<LessonUi>,
): HomeCourseUiModel? {
    return (0L..7L).asSequence()
        .flatMap { offset ->
            val date = now.toLocalDate().plusDays(offset)
            lessonsForDate(date)
                .asSequence()
                .map { it.toHomeCourse(date) }
        }
        .filter { course -> now.isBefore(LocalDateTime.of(course.date, course.startTime)) }
        .minByOrNull { LocalDateTime.of(it.date, it.startTime) }
}

private fun LessonUi.toHomeCourse(date: LocalDate): HomeCourseUiModel = HomeCourseUiModel(
    id = "$id@$date",
    sourceLessonId = id,
    title = title,
    teacher = teacher,
    location = location,
    date = date,
    startTime = startTime,
    endTime = endTime,
    accent = classingCourseAccent(title),
)

private fun minutesUntil(from: LocalDateTime, to: LocalDateTime): Long {
    val seconds = Duration.between(from, to).seconds.coerceAtLeast(0L)
    return ((seconds + 59L) / 60L).coerceAtLeast(1L)
}
