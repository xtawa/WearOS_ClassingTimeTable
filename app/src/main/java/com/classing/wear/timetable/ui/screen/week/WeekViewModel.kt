package com.classing.wear.timetable.ui.screen.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classing.wear.timetable.core.i18n.WearI18n
import com.classing.wear.timetable.core.time.TimeProvider
import com.classing.wear.timetable.core.time.WeekCalculator
import com.classing.wear.timetable.domain.repository.ScheduleRepository
import com.classing.wear.timetable.ui.state.WeekUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WeekViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val selectedWeekStart = MutableStateFlow(WeekCalculator.weekStart(timeProvider.today()))

    private val _uiState = MutableStateFlow(WeekUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            selectedWeekStart
                .flatMapLatest { weekStart ->
                    scheduleRepository.observeWeekSchedule(weekStart)
                }
                .collect { schedule ->
                    _uiState.value = WeekUiState(
                        isLoading = false,
                        weekLabel = WearI18n.weekLabel(schedule.weekIndex),
                        schedule = schedule,
                        errorMessage = null,
                    )
                }
        }
    }

    fun previousWeek() {
        selectedWeekStart.update { it.minusDays(7) }
    }

    fun nextWeek() {
        selectedWeekStart.update { it.plusDays(7) }
    }

    fun jumpToCurrentWeek() {
        selectedWeekStart.value = WeekCalculator.weekStart(timeProvider.today())
    }
}

