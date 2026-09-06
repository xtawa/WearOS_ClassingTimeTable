package com.classing.wear.timetable.data.repository

import com.classing.shared.ui.heatmap.HeatmapLessonInput
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class DefaultScheduleRepositoryHeatmapTest {
    @Test
    fun observeHeatmapLessons_keepsSessionTimingEvenWhenCurrentWeekWouldBeEmpty() = runTest {
        val semester = SemesterEntity(
            localId = 1,
            remoteId = "semester",
            name = "2026 Spring",
            startDate = LocalDate.of(2026, 2, 23),
            endDate = LocalDate.of(2026, 7, 3),
            totalWeeks = 18,
            isActive = true,
            version = 1,
        )
        val repository = DefaultScheduleRepository(
            semesterDao = FakeSemesterDao(semester),
            courseDao = FakeCourseDao(),
            sessionDao = FakeCourseSessionDao(
                listOf(
                    CourseSessionEntity(
                        localId = 7,
                        remoteId = "session_future",
                        semesterId = 1,
                        courseId = 10,
                        dayOfWeek = DayOfWeek.THURSDAY.value,
                        timeSlotId = 5,
                        startWeek = 12,
                        endWeek = 16,
                        weekParity = "ALL",
                        version = 1,
                    ),
                ),
            ),
            slotDao = FakeTimeSlotDao(
                listOf(
                    TimeSlotEntity(
                        localId = 5,
                        remoteId = "slot_5",
                        semesterId = 1,
                        indexInDay = 4,
                        label = "5-6",
                        startTime = LocalTime.of(14, 0),
                        endTime = LocalTime.of(15, 35),
                        version = 1,
                    ),
                ),
            ),
            exceptionDao = FakeScheduleExceptionDao(),
        )

        val heatmapLessons = repository.observeHeatmapLessons().first()

        assertEquals(
            listOf(
                HeatmapLessonInput(
                    dayOfWeek = DayOfWeek.THURSDAY,
                    startTime = LocalTime.of(14, 0),
                    endTime = LocalTime.of(15, 35),
                ),
            ),
            heatmapLessons,
        )
    }

    @Test
    fun observeHeatmapLessons_returnsEmptyWithoutAnActiveSemester() = runTest {
        val repository = DefaultScheduleRepository(
            semesterDao = FakeSemesterDao(null),
            courseDao = FakeCourseDao(),
            sessionDao = FakeCourseSessionDao(emptyList()),
            slotDao = FakeTimeSlotDao(emptyList()),
            exceptionDao = FakeScheduleExceptionDao(),
        )

        assertTrue(repository.observeHeatmapLessons().first().isEmpty())
    }

    private class FakeSemesterDao(activeSemester: SemesterEntity?) : SemesterDao {
        private val flow = MutableStateFlow(activeSemester)

        override fun observeActiveSemester(): Flow<SemesterEntity?> = flow
        override suspend fun getById(semesterId: Long): SemesterEntity? = flow.value
        override suspend fun getByRemoteId(remoteId: String): SemesterEntity? = flow.value
        override suspend fun getAll(): List<SemesterEntity> = listOfNotNull(flow.value)
        override suspend fun upsert(semester: SemesterEntity): Long = semester.localId
        override suspend fun upsertAll(semesters: List<SemesterEntity>) = Unit
        override suspend fun setActiveSemester(semesterId: Long) = Unit
        override suspend fun deleteMissingRemoteIds(remoteIds: List<String>) = Unit
        override suspend fun deleteAll() = Unit
    }

    private class FakeCourseDao : CourseDao {
        override fun observeBySemester(semesterId: Long): Flow<List<CourseEntity>> = flowOf(emptyList())
        override fun observeById(courseId: Long): Flow<CourseEntity?> = flowOf(null)
        override suspend fun getByRemoteId(remoteId: String): CourseEntity? = null
        override fun search(semesterId: Long, keyword: String): Flow<List<CourseEntity>> = flowOf(emptyList())
        override suspend fun upsert(course: CourseEntity): Long = course.localId
        override suspend fun upsertAll(courses: List<CourseEntity>) = Unit
        override suspend fun deleteBySemester(semesterId: Long) = Unit
        override suspend fun deleteMissingRemoteIds(semesterId: Long, remoteIds: List<String>) = Unit
        override suspend fun deleteByRemoteIds(remoteIds: List<String>) = Unit
    }

    private class FakeCourseSessionDao(sessions: List<CourseSessionEntity>) : CourseSessionDao {
        private val flow = MutableStateFlow(sessions)

        override fun observeBySemester(semesterId: Long): Flow<List<CourseSessionEntity>> = flow
        override suspend fun getById(sessionId: Long): CourseSessionEntity? = flow.value.firstOrNull { it.localId == sessionId }
        override suspend fun getByRemoteId(remoteId: String): CourseSessionEntity? = flow.value.firstOrNull { it.remoteId == remoteId }
        override suspend fun countAll(): Int = flow.value.size
        override suspend fun upsert(session: CourseSessionEntity): Long = session.localId
        override suspend fun upsertAll(sessions: List<CourseSessionEntity>) = Unit
        override suspend fun deleteBySemester(semesterId: Long) = Unit
        override suspend fun deleteMissingRemoteIds(semesterId: Long, remoteIds: List<String>) = Unit
        override suspend fun deleteByRemoteIds(remoteIds: List<String>) = Unit
    }

    private class FakeTimeSlotDao(slots: List<TimeSlotEntity>) : TimeSlotDao {
        private val flow = MutableStateFlow(slots)

        override fun observeBySemester(semesterId: Long): Flow<List<TimeSlotEntity>> = flow
        override suspend fun getById(slotId: Long): TimeSlotEntity? = flow.value.firstOrNull { it.localId == slotId }
        override suspend fun getByRemoteId(remoteId: String): TimeSlotEntity? = flow.value.firstOrNull { it.remoteId == remoteId }
        override suspend fun upsert(slot: TimeSlotEntity): Long = slot.localId
        override suspend fun upsertAll(slots: List<TimeSlotEntity>) = Unit
        override suspend fun deleteBySemester(semesterId: Long) = Unit
        override suspend fun deleteMissingRemoteIds(semesterId: Long, remoteIds: List<String>) = Unit
    }

    private class FakeScheduleExceptionDao : ScheduleExceptionDao {
        override fun observeBySemester(semesterId: Long): Flow<List<ScheduleExceptionEntity>> = flowOf(emptyList())
        override suspend fun getByDateRange(
            semesterId: Long,
            startDate: LocalDate,
            endDate: LocalDate,
        ): List<ScheduleExceptionEntity> = emptyList()

        override suspend fun getByRemoteId(remoteId: String): ScheduleExceptionEntity? = null
        override suspend fun upsert(exception: ScheduleExceptionEntity): Long = exception.localId
        override suspend fun upsertAll(exceptions: List<ScheduleExceptionEntity>) = Unit
        override suspend fun deleteBySemester(semesterId: Long) = Unit
        override suspend fun deleteMissingRemoteIds(semesterId: Long, remoteIds: List<String>) = Unit
        override suspend fun deleteByRemoteIds(remoteIds: List<String>) = Unit
    }
}
