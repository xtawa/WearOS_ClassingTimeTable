package com.classing.wear.timetable.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classing.wear.timetable.core.i18n.WearI18n
import com.classing.wear.timetable.core.time.TimeFormatters
import com.classing.wear.timetable.core.time.TimeProvider
import com.classing.wear.timetable.core.time.WeekCalculator
import com.classing.wear.timetable.domain.model.SyncState
import com.classing.wear.timetable.domain.repository.ScheduleRepository
import com.classing.wear.timetable.sync.MobileSyncRequester
import com.classing.wear.timetable.ui.state.HomeUiState
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class HomeViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val mobileSyncRequester: MobileSyncRequester,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()
    private val syncState = MutableStateFlow<SyncState>(SyncState.Idle)

    init {
        val today = timeProvider.today()

        viewModelScope.launch {
            requestSyncFromPhone()
        }

        viewModelScope.launch {
            combine(
                scheduleRepository.observeActiveSemester(),
                scheduleRepository.observeTodayLessons(today),
                scheduleRepository.observeNextLesson(today),
                syncState,
            ) { semester, lessons, next, syncState ->
                val weekLabel = semester?.let {
                    WearI18n.weekLabel(WeekCalculator.weekIndex(it.startDate, today))
                } ?: WearI18n.semesterNotSet()

                HomeUiState(
                    isLoading = false,
                    hasSchedule = semester != null,
                    dateLabel = TimeFormatters.formatDate(today),
                    weekLabel = weekLabel,
                    syncState = syncState,
                    nextLesson = next,
                    todayLessons = lessons,
                    errorMessage = (syncState as? SyncState.Failed)?.message,
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
}

