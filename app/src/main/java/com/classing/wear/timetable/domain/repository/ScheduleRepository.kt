package com.classing.wear.timetable.domain.repository

import com.classing.wear.timetable.domain.model.Course
import com.classing.wear.timetable.domain.model.LessonOccurrence
import com.classing.wear.timetable.domain.model.NextLessonHint
import com.classing.wear.timetable.domain.model.Semester
import com.classing.wear.timetable.domain.model.WeekSchedule
import com.classing.shared.ui.heatmap.HeatmapLessonInput
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

interface ScheduleRepository {
    fun observeActiveSemester(): Flow<Semester?>
    fun observeTodayLessons(): Flow<List<LessonOccurrence>>
    fun observeWeekSchedule(weekStart: LocalDate): Flow<WeekSchedule>
    fun observeHeatmapLessons(): Flow<List<HeatmapLessonInput>>
    fun observeNextLesson(): Flow<NextLessonHint>
    fun searchCourses(keyword: String): Flow<List<Course>>
    fun observeCourseDetail(courseId: Long): Flow<Course?>

    /**
     * One-shot projection of every lesson occurrence in [startDate]..[endDate] (inclusive) for the
     * active semester, with week rules and schedule exceptions applied. Returns an empty list when
     * no semester is active.
     */
    suspend fun loadOccurrences(startDate: LocalDate, endDate: LocalDate, now: LocalDateTime): List<LessonOccurrence>
}
