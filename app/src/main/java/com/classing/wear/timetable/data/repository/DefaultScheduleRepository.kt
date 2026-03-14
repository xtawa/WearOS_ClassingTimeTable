package com.classing.wear.timetable.data.repository

import com.classing.wear.timetable.core.time.SystemTimeProvider
import com.classing.wear.timetable.core.time.TimeProvider
import com.classing.wear.timetable.data.local.dao.CourseDao
import com.classing.wear.timetable.data.local.dao.CourseSessionDao
import com.classing.wear.timetable.data.local.dao.ScheduleExceptionDao
import com.classing.wear.timetable.data.local.dao.SemesterDao
import com.classing.wear.timetable.data.local.dao.TimeSlotDao
import com.classing.wear.timetable.data.mapper.asDomain
import com.classing.wear.timetable.domain.model.Course
import com.classing.wear.timetable.domain.model.LessonOccurrence
import com.classing.wear.timetable.domain.model.NextLessonHint
import com.classing.wear.timetable.domain.model.Semester
import com.classing.wear.timetable.domain.model.WeekSchedule
import com.classing.wear.timetable.domain.repository.ScheduleRepository
import com.classing.wear.timetable.domain.usecase.ScheduleAssembler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class DefaultScheduleRepository(
    private val semesterDao: SemesterDao,
    private val courseDao: CourseDao,
    private val sessionDao: CourseSessionDao,
    private val slotDao: TimeSlotDao,
    private val exceptionDao: ScheduleExceptionDao,
    private val assembler: ScheduleAssembler = ScheduleAssembler(),
    private val timeProvider: TimeProvider = SystemTimeProvider(),
) : ScheduleRepository {

    override fun observeActiveSemester(): Flow<Semester?> {
        return semesterDao.observeActiveSemester().map { it?.asDomain() }
    }

    override fun observeTodayLessons(today: LocalDate): Flow<List<LessonOccurrence>> {
        return observeActiveSemester().flatMapLatest { semester ->
            if (semester == null) return@flatMapLatest flowOf(emptyList())
            observeScheduleContext(semester).map { ctx ->
                assembler.buildDayOccurrences(
                    date = today,
                    now = timeProvider.nowDateTime(),
                    semester = semester,
                    courses = ctx.courses,
                    sessions = ctx.sessions,
                    slots = ctx.slots,
                    exceptions = ctx.exceptions,
                )
            }
        }
    }

    override fun observeWeekSchedule(weekStart: LocalDate): Flow<WeekSchedule> {
        return observeActiveSemester().flatMapLatest { semester ->
            if (semester == null) {
                return@flatMapLatest flowOf(
                    WeekSchedule(
                        weekIndex = 1,
                        days = emptyMap(),
                    ),
                )
            }
            observeScheduleContext(semester).map { ctx ->
                assembler.buildWeekSchedule(
                    weekStart = weekStart,
                    now = timeProvider.nowDateTime(),
                    semester = semester,
                    courses = ctx.courses,
                    sessions = ctx.sessions,
                    slots = ctx.slots,
                    exceptions = ctx.exceptions,
                )
            }
        }
    }

    override fun observeNextLesson(nowDate: LocalDate): Flow<NextLessonHint> {
        return observeActiveSemester().flatMapLatest { semester ->
            if (semester == null) return@flatMapLatest flowOf(NextLessonHint(null, null))
            observeScheduleContext(semester).map { ctx ->
                assembler.findNextLessonHint(
                    now = timeProvider.nowDateTime(),
                    semester = semester,
                    courses = ctx.courses,
                    sessions = ctx.sessions,
                    slots = ctx.slots,
                    exceptions = ctx.exceptions,
                )
            }
        }
    }

    override fun searchCourses(keyword: String): Flow<List<Course>> {
        return observeActiveSemester().flatMapLatest { semester ->
            if (semester == null) return@flatMapLatest flowOf(emptyList())
            courseDao.search(semester.localId, keyword).map { list -> list.map { it.asDomain() } }
        }
    }

    override fun observeCourseDetail(courseId: Long): Flow<Course?> {
        return courseDao.observeById(courseId).map { it?.asDomain() }
    }

    private fun observeScheduleContext(semester: Semester): Flow<ScheduleContext> {
        return combine(
            courseDao.observeBySemester(semester.localId).map { it.map { e -> e.asDomain() } },
            sessionDao.observeBySemester(semester.localId).map { it.map { e -> e.asDomain() } },
            slotDao.observeBySemester(semester.localId).map { it.map { e -> e.asDomain() } },
            exceptionDao.observeBySemester(semester.localId).map { it.map { e -> e.asDomain() } },
        ) { courses, sessions, slots, exceptions ->
            ScheduleContext(courses, sessions, slots, exceptions)
        }
    }

    private data class ScheduleContext(
        val courses: List<com.classing.wear.timetable.domain.model.Course>,
        val sessions: List<com.classing.wear.timetable.domain.model.CourseSession>,
        val slots: List<com.classing.wear.timetable.domain.model.TimeSlot>,
        val exceptions: List<com.classing.wear.timetable.domain.model.ScheduleException>,
    )
}
