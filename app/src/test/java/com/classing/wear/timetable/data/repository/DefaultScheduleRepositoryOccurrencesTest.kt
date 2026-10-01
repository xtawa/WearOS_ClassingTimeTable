package com.classing.wear.timetable.data.repository

import com.classing.wear.timetable.data.local.dao.CourseDao
import com.classing.wear.timetable.data.local.dao.CourseSessionDao
import com.classing.wear.timetable.data.local.dao.ScheduleExceptionDao
import com.classing.wear.timetable.data.local.dao.SemesterDao
import com.classing.wear.timetable.data.local.dao.TimeSlotDao
import com.classing.wear.timetable.data.local.entity.CourseEntity
import com.classing.wear.timetable.data.local.entity.CourseSessionEntity
import com.classing.wear.timetable.data.local.entity.ScheduleExceptionEntity
import com.classing.wear.timetable.data.local.entity.SemesterEntity
import com.classing.wear.timetable.data.local.entity.TimeSlotEntity
import com.classing.wear.timetable.worker.ReminderCheckLogic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Regression coverage for the Wear reminder source: reminders must come from the projected
 * database (week rules and exceptions applied), not from the raw weekly payload.
 */
class DefaultScheduleRepositoryOccurrencesTest {
    // Semester starts Monday 2026-02-23. Week 1 = Feb 23..Mar 1, week 3 = Mar 9..15, week 12 = May 11..17.
    private val semester = SemesterEntity(
        localId = 1,
        remoteId = "semester",
        name = "2026 Spring",
        startDate = LocalDate.of(2026, 2, 23),
        endDate = LocalDate.of(2026, 7, 3),
        totalWeeks = 18,
        isActive = true,
        version = 1,
    )
    private val course = CourseEntity(
        localId = 10,
        remoteId = "mobile-course-a",
        semesterId = 1,
        name = "Algorithms",
        teacher = "T",
        classroom = "A101",
        note = "",
        colorLabel = "teal",
        isFavorite = false,
        version = 1,
    )
    private val slot = TimeSlotEntity(
        localId = 5,
        remoteId = "slot_5",
        semesterId = 1,
        indexInDay = 4,
        label = "5-6",
        startTime = LocalTime.of(14, 0),
        endTime = LocalTime.of(15, 35),
        version = 1,
    )
    private val thursdayWeeks12To16 = CourseSessionEntity(
        localId = 7,
        remoteId = "mobile-session-a",
        semesterId = 1,
        courseId = 10,
        dayOfWeek = DayOfWeek.THURSDAY.value,
        timeSlotId = 5,
        startWeek = 12,
        endWeek = 16,
        weekParity = "ALL",
        version = 1,
    )

    private fun repository(
        activeSemester: SemesterEntity? = semester,
        exceptions: List<ScheduleExceptionEntity> = emptyList(),
    ) = DefaultScheduleRepository(
        semesterDao = FakeSemesterDao(activeSemester),
        courseDao = FakeCourseDao(listOf(course)),
        sessionDao = FakeCourseSessionDao(listOf(thursdayWeeks12To16)),
        slotDao = FakeTimeSlotDao(listOf(slot)),
        exceptionDao = FakeScheduleExceptionDao(exceptions),
    )

    @Test
    fun loadOccurrences_respectsWeekRuleInsteadOfWeekdayTemplate() = runTest {
        val repo = repository()
        val week3Monday = LocalDate.of(2026, 3, 9)
        val now = LocalDateTime.of(week3Monday, LocalTime.of(7, 0))

        // Thursday of week 3 exists in the weekly template but is outside startWeek..endWeek.
        val week3 = repo.loadOccurrences(week3Monday, week3Monday.plusDays(7), now)
        assertTrue(week3.isEmpty())

        val week12Monday = LocalDate.of(2026, 5, 11)
        val week12 = repo.loadOccurrences(week12Monday, week12Monday.plusDays(7), now)
        assertEquals(1, week12.size)
        assertEquals(LocalDate.of(2026, 5, 14), week12.single().date)
        assertEquals(LocalDateTime.of(2026, 5, 14, 14, 0), week12.single().startAt)

        val reminders = ReminderCheckLogic.fromOccurrences(week12)
        assertEquals("mobile-session-a", reminders.single().id)
        assertEquals("2026-05-14:mobile-session-a:840", ReminderCheckLogic.reminderKey(reminders.single()))
    }

    @Test
    fun loadOccurrences_appliesCancelException() = runTest {
        val cancel = ScheduleExceptionEntity(
            localId = 3,
            remoteId = "mobile-exception-x",
            semesterId = 1,
            sessionId = 7,
            exceptionType = "CANCEL",
            date = LocalDate.of(2026, 5, 14),
            reason = "holiday",
            courseId = null,
            timeSlotId = null,
            dayOfWeek = null,
            newCourseId = null,
            newTimeSlotId = null,
            version = 1,
        )
        val repo = repository(exceptions = listOf(cancel))
        val now = LocalDateTime.of(2026, 5, 11, 7, 0)

        val occurrences = repo.loadOccurrences(LocalDate.of(2026, 5, 11), LocalDate.of(2026, 5, 24), now)

        // Week 12 Thursday cancelled, week 13 Thursday still present.
        assertEquals(listOf(LocalDate.of(2026, 5, 21)), occurrences.map { it.date })
    }

    @Test
    fun loadOccurrences_returnsEmptyWithoutActiveSemesterOrInvalidRange() = runTest {
        val now = LocalDateTime.of(2026, 5, 11, 7, 0)
        assertTrue(repository(activeSemester = null).loadOccurrences(now.toLocalDate(), now.toLocalDate().plusDays(7), now).isEmpty())
        assertTrue(repository().loadOccurrences(LocalDate.of(2026, 5, 18), LocalDate.of(2026, 5, 11), now).isEmpty())
    }

    private class FakeSemesterDao(private val activeSemester: SemesterEntity?) : SemesterDao {
        override fun observeActiveSemester(): Flow<SemesterEntity?> = flowOf(activeSemester)
        override suspend fun getById(semesterId: Long): SemesterEntity? = activeSemester
        override suspend fun getByRemoteId(remoteId: String): SemesterEntity? = activeSemester
        override suspend fun getAll(): List<SemesterEntity> = listOfNotNull(activeSemester)
        override suspend fun upsert(semester: SemesterEntity): Long = semester.localId
        override suspend fun upsertAll(semesters: List<SemesterEntity>) = Unit
        override suspend fun setActiveSemester(semesterId: Long) = Unit
        override suspend fun deleteMissingRemoteIds(remoteIds: List<String>) = Unit
        override suspend fun deleteAll() = Unit
    }

    private class FakeCourseDao(private val courses: List<CourseEntity>) : CourseDao {
        override fun observeBySemester(semesterId: Long): Flow<List<CourseEntity>> = flowOf(courses)
        override fun observeById(courseId: Long): Flow<CourseEntity?> = flowOf(courses.firstOrNull { it.localId == courseId })
        override suspend fun getByRemoteId(remoteId: String): CourseEntity? = courses.firstOrNull { it.remoteId == remoteId }
        override fun search(semesterId: Long, keyword: String): Flow<List<CourseEntity>> = flowOf(emptyList())
        override suspend fun upsert(course: CourseEntity): Long = course.localId
        override suspend fun upsertAll(courses: List<CourseEntity>) = Unit
        override suspend fun deleteBySemester(semesterId: Long) = Unit
        override suspend fun deleteMissingRemoteIds(semesterId: Long, remoteIds: List<String>) = Unit
        override suspend fun deleteByRemoteIds(remoteIds: List<String>) = Unit
    }

    private class FakeCourseSessionDao(private val sessions: List<CourseSessionEntity>) : CourseSessionDao {
        override fun observeBySemester(semesterId: Long): Flow<List<CourseSessionEntity>> = flowOf(sessions)
        override suspend fun getById(sessionId: Long): CourseSessionEntity? = sessions.firstOrNull { it.localId == sessionId }
        override suspend fun getByRemoteId(remoteId: String): CourseSessionEntity? = sessions.firstOrNull { it.remoteId == remoteId }
        override suspend fun upsert(session: CourseSessionEntity): Long = session.localId
        override suspend fun upsertAll(sessions: List<CourseSessionEntity>) = Unit
        override suspend fun deleteBySemester(semesterId: Long) = Unit
        override suspend fun deleteMissingRemoteIds(semesterId: Long, remoteIds: List<String>) = Unit
        override suspend fun deleteByRemoteIds(remoteIds: List<String>) = Unit
    }

    private class FakeTimeSlotDao(private val slots: List<TimeSlotEntity>) : TimeSlotDao {
        override fun observeBySemester(semesterId: Long): Flow<List<TimeSlotEntity>> = flowOf(slots)
        override suspend fun getById(slotId: Long): TimeSlotEntity? = slots.firstOrNull { it.localId == slotId }
        override suspend fun getByRemoteId(remoteId: String): TimeSlotEntity? = slots.firstOrNull { it.remoteId == remoteId }
        override suspend fun upsert(slot: TimeSlotEntity): Long = slot.localId
        override suspend fun upsertAll(slots: List<TimeSlotEntity>) = Unit
        override suspend fun deleteBySemester(semesterId: Long) = Unit
        override suspend fun deleteMissingRemoteIds(semesterId: Long, remoteIds: List<String>) = Unit
    }

    private class FakeScheduleExceptionDao(private val exceptions: List<ScheduleExceptionEntity>) : ScheduleExceptionDao {
        override fun observeBySemester(semesterId: Long): Flow<List<ScheduleExceptionEntity>> = flowOf(exceptions)
        override suspend fun getByDateRange(
            semesterId: Long,
            startDate: LocalDate,
            endDate: LocalDate,
        ): List<ScheduleExceptionEntity> = exceptions.filter { !it.date.isBefore(startDate) && !it.date.isAfter(endDate) }

        override suspend fun getByRemoteId(remoteId: String): ScheduleExceptionEntity? = exceptions.firstOrNull { it.remoteId == remoteId }
        override suspend fun upsert(exception: ScheduleExceptionEntity): Long = exception.localId
        override suspend fun upsertAll(exceptions: List<ScheduleExceptionEntity>) = Unit
        override suspend fun deleteBySemester(semesterId: Long) = Unit
        override suspend fun deleteMissingRemoteIds(semesterId: Long, remoteIds: List<String>) = Unit
        override suspend fun deleteByRemoteIds(remoteIds: List<String>) = Unit
    }
}
