package com.classing.wear.timetable.ui.screen.home

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.SavedStateHandle
import com.classing.shared.ui.heatmap.HeatmapLessonInput
import com.classing.wear.timetable.core.time.TimeProvider
import com.classing.wear.timetable.domain.model.*
import com.classing.wear.timetable.domain.repository.*
import com.classing.wear.timetable.ui.PreviewSamples
import com.classing.wear.timetable.ui.screen.week.WeekViewModel
import com.classing.wear.timetable.ui.screen.search.SearchViewModel
import com.classing.wear.timetable.ui.screen.detail.CourseDetailViewModel
import java.lang.reflect.Proxy
import java.net.UnknownHostException
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeOfflineTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val saved = PreviewSamples.sampleLesson()
    private val local = LocalSchedule(saved)
    private val clock = object : TimeProvider { override fun nowDateTime() = saved.startAt.minusHours(1) }
    private val settings = Proxy.newProxyInstance(SettingsRepository::class.java.classLoader, arrayOf(SettingsRepository::class.java)) { _, method, _ ->
        check(method.name == "observePreferences") { "Offline viewing must not require settings writes" }
        flowOf(UserPreferences())
    } as SettingsRepository

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }
    private fun model(key: String, sync: suspend () -> Result<Unit>) = HomeViewModel(local, settings, clock, sync).also { store.put(key, it) }

    @Test fun cachedCoursesStayVisibleDuringFailureAndEveryRetry() = runTest(dispatcher) {
        val first = CompletableDeferred<Result<Unit>>()
        val retry = CompletableDeferred<Result<Unit>>()
        var attempts = 0
        val vm = model("home") { if (++attempts == 1) first.await() else retry.await() }
        runCurrent()
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(listOf(saved), vm.uiState.value.todayLessons)
        assertTrue(vm.uiState.value.syncState is SyncState.Syncing)
        first.complete(Result.failure(UnknownHostException("No address associated with hostname")))
        runCurrent()
        assertTrue(vm.uiState.value.syncState is SyncState.Failed)
        assertNull(vm.uiState.value.errorMessage)
        assertEquals(listOf(saved), vm.uiState.value.todayLessons)
        assertEquals(saved, vm.uiState.value.nextLesson.lesson)
        vm.retrySync(); runCurrent()
        vm.retrySync(); runCurrent()
        assertEquals(2, attempts)
        assertEquals(listOf(saved), vm.uiState.value.todayLessons)
        retry.complete(Result.failure(UnknownHostException("offline")))
        runCurrent()
        assertEquals(listOf(saved), vm.uiState.value.todayLessons)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test fun reopeningOfflineStillReadsSavedCoursesEvenIfSyncThrows() = runTest(dispatcher) {
        val first = model("first") { Result.failure(UnknownHostException("offline")) }
        runCurrent()
        assertTrue(first.uiState.value.hasSchedule)
        val reopened = model("reopened") { throw UnknownHostException("offline on cold start") }
        runCurrent()
        assertFalse(reopened.uiState.value.isLoading)
        assertTrue(reopened.uiState.value.hasSchedule)
        assertNull(reopened.uiState.value.errorMessage)
        assertEquals(listOf(saved), reopened.uiState.value.todayLessons)
        assertTrue(reopened.uiState.value.syncState is SyncState.Failed)
    }

    @Test fun weekSearchAndCourseDetailsReadTheSameSavedTimetableWithoutNetworking() = runTest(dispatcher) {
        val week = WeekViewModel(local, settings, clock).also { store.put("week", it) }
        val search = SearchViewModel(local).also { store.put("search", it) }
        val detail = CourseDetailViewModel(SavedStateHandle(mapOf("courseId" to saved.course.localId)), local, clock).also { store.put("detail", it) }
        search.onQueryChange("数学")
        runCurrent(); advanceTimeBy(251); runCurrent()
        assertFalse(week.uiState.value.isLoading)
        assertEquals(listOf(saved), week.uiState.value.schedule.days.values.flatten())
        assertEquals(listOf(saved.course), search.uiState.value.results)
        assertEquals(saved.course, detail.uiState.value.course)
        assertEquals(listOf(saved), detail.uiState.value.upcomingLessons)
        assertNull(detail.uiState.value.errorMessage)
    }

    private class LocalSchedule(private val saved: LessonOccurrence) : ScheduleRepository {
        private val lessons = MutableStateFlow(listOf(saved))
        override fun observeActiveSemester() = flowOf(Semester(1, "semester", "Saved semester", saved.date.minusWeeks(2), saved.date.plusWeeks(15), 18, true, 1))
        override fun observeTodayLessons(): Flow<List<LessonOccurrence>> = lessons
        override fun observeNextLesson() = flowOf(NextLessonHint(saved, null))
        override fun observeHeatmapLessons() = flowOf(listOf(HeatmapLessonInput(saved.session.dayOfWeek, saved.timeSlot.startTime, saved.timeSlot.endTime)))
        override fun observeWeekSchedule(weekStart: LocalDate) = flowOf(WeekSchedule(3, mapOf(saved.session.dayOfWeek to listOf(saved))))
        override fun searchCourses(keyword: String) = flowOf(listOf(saved.course))
        override fun observeCourseDetail(courseId: Long) = flowOf(saved.course)
        override suspend fun loadOccurrences(startDate: LocalDate, endDate: LocalDate, now: LocalDateTime) = lessons.value
    }
}
