package com.classing.wear.timetable.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classing.wear.timetable.core.i18n.WearI18n
import com.classing.wear.timetable.core.time.TimeFormatters
import com.classing.wear.timetable.core.time.TimeProvider
import com.classing.wear.timetable.core.time.WeekCalculator
import com.classing.wear.timetable.domain.model.LessonStatus
import com.classing.wear.timetable.domain.model.SyncState
import com.classing.wear.timetable.domain.repository.ScheduleRepository
import com.classing.wear.timetable.domain.repository.SettingsRepository
import com.classing.shared.ui.heatmap.buildHeatmapCells
import com.classing.wear.timetable.ui.state.HomeUiState
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private data class HomeInputs(
    val semester: com.classing.wear.timetable.domain.model.Semester? = null,
    val lessons: List<com.classing.wear.timetable.domain.model.LessonOccurrence> = emptyList(),
    val next: com.classing.wear.timetable.domain.model.NextLessonHint = com.classing.wear.timetable.domain.model.NextLessonHint(null, null),
    val preferences: com.classing.wear.timetable.domain.repository.UserPreferences = com.classing.wear.timetable.domain.repository.UserPreferences(),
    val syncState: SyncState = SyncState.Idle,
    val heatmapCells: List<com.classing.shared.ui.heatmap.HeatmapCell> = emptyList(),
)

class HomeViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
    private val requestScheduleSync: suspend () -> Result<Unit>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()
    private val syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    private var syncJob: Job? = null

    init {
        retrySync()

        viewModelScope.launch {
            val heatmapFlow = scheduleRepository.observeHeatmapLessons()
                .map { inputs ->
                    buildHeatmapCells(inputs)
                }

            combine(
                scheduleRepository.observeActiveSemester(),
                scheduleRepository.observeTodayLessons(),
            ) { semester, lessons ->
                HomeInputs(semester = semester, lessons = lessons)
            }.combine(scheduleRepository.observeNextLesson()) { inputs, next ->
                inputs.copy(next = next)
            }.combine(settingsRepository.observePreferences()) { inputs, preferences ->
                inputs.copy(preferences = preferences)
            }.combine(syncState) { inputs, currentSyncState ->
                inputs.copy(syncState = currentSyncState)
            }.combine(heatmapFlow) { inputs, heatmapCells ->
                val today = timeProvider.today()
                val weekLabel = inputs.semester?.let {
                    WearI18n.weekLabel(WeekCalculator.weekIndex(it.startDate, today))
                } ?: WearI18n.semesterNotSet()
                val visibleLessons = if (inputs.preferences.showCompletedToday) {
                    inputs.lessons
                } else {
                    inputs.lessons.filter { it.status != LessonStatus.FINISHED }
                }

                HomeUiState(
                    isLoading = false,
                    hasSchedule = inputs.semester != null,
                    dateLabel = TimeFormatters.formatDate(today),
                    weekLabel = weekLabel,
                    syncState = inputs.syncState,
                    nextLesson = inputs.next,
                    todayLessons = visibleLessons,
                    heatmapCells = heatmapCells,
                    showAiOnHome = inputs.preferences.showAiOnHome,
                    // The database is the course source. Network failures belong
                    // to syncState and must never replace the saved timetable.
                )
            }.catch { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun retrySync() {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            refreshSchedule()
        }
    }

    private suspend fun refreshSchedule() {
        syncState.value = SyncState.Syncing
        val result = try {
            requestScheduleSync()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
        syncState.value = if (result.isSuccess) {
            SyncState.Success(Instant.now())
        } else {
            SyncState.Failed(result.exceptionOrNull()?.message ?: WearI18n.syncCheckPhoneConnection())
        }
    }
}
