package com.classing.wear.timetable.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classing.wear.timetable.core.i18n.WearI18n
import com.classing.wear.timetable.core.time.TimeFormatters
import com.classing.wear.timetable.core.time.TimeProvider
import com.classing.wear.timetable.core.time.WeekCalculator
import com.classing.wear.timetable.domain.model.LessonOccurrence
import com.classing.wear.timetable.domain.model.LessonStatus
import com.classing.wear.timetable.domain.model.NextLessonHint
import com.classing.wear.timetable.domain.model.Semester
import com.classing.wear.timetable.domain.model.SyncState
import com.classing.wear.timetable.domain.repository.ScheduleRepository
import com.classing.wear.timetable.domain.repository.SettingsRepository
import com.classing.wear.timetable.domain.repository.UserPreferences
import com.classing.wear.timetable.sync.MobileSyncRequester
import com.classing.wear.timetable.ui.state.HomeUiState
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val settingsRepository: SettingsRepository,
    private val mobileSyncRequester: MobileSyncRequester,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()
    private val syncState = MutableStateFlow<SyncState>(SyncState.Idle)

    init {
        viewModelScope.launch {
            requestSyncFromPhone()
        }

        viewModelScope.launch {
            combine(
                scheduleRepository.observeActiveSemester(),
                scheduleRepository.observeTodayLessons(timeProvider.today()),
                scheduleRepository.observeNextLesson(timeProvider.today()),
                settingsRepository.observePreferences(),
                syncState,
            ) { semester, lessons, next, preferences, syncState ->
                HomeSnapshot(
                    semester = semester,
                    lessons = lessons,
                    next = next,
                    preferences = preferences,
                    syncState = syncState,
                )
            }.combine(minuteTicker()) { snapshot, _ ->
                val today = timeProvider.today()
                val weekLabel = snapshot.semester?.let {
                    WearI18n.weekLabel(WeekCalculator.weekIndex(it.startDate, today))
                } ?: WearI18n.semesterNotSet()
                val visibleLessons = if (snapshot.preferences.showCompletedToday) {
                    snapshot.lessons
                } else {
                    snapshot.lessons.filter { it.status != LessonStatus.FINISHED }
                }

                HomeUiState(
                    isLoading = false,
                    hasSchedule = snapshot.semester != null,
                    dateLabel = TimeFormatters.formatDate(today),
                    weekLabel = weekLabel,
                    syncState = snapshot.syncState,
                    nextLesson = snapshot.next,
                    todayLessons = visibleLessons,
                    errorMessage = (snapshot.syncState as? SyncState.Failed)?.message,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun retrySync() {
        viewModelScope.launch {
            requestSyncFromPhone()
        }
    }

    private suspend fun requestSyncFromPhone() {
        syncState.value = SyncState.Syncing
        val result = mobileSyncRequester.requestSyncFromPhone()
        syncState.value = if (result.isSuccess) {
            SyncState.Success(Instant.now())
        } else {
            SyncState.Failed(result.exceptionOrNull()?.message ?: "sync request failed")
        }
    }

    private fun minuteTicker(): Flow<Long> = flow {
        while (true) {
            val now = timeProvider.nowDateTime()
            emit(now.toEpochSecond(java.time.ZoneOffset.UTC))
            val delayMillis = ((60 - now.second) * 1_000L) - (now.nano / 1_000_000L)
            delay(delayMillis.coerceAtLeast(1L))
        }
    }

    private data class HomeSnapshot(
        val semester: Semester?,
        val lessons: List<LessonOccurrence>,
        val next: NextLessonHint,
        val preferences: UserPreferences,
        val syncState: SyncState,
    )
}

