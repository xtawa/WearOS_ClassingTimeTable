package com.classing.wear.timetable.ui.screen.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classing.wear.timetable.core.time.TimeProvider
import com.classing.wear.timetable.core.time.WeekCalculator
import com.classing.wear.timetable.domain.repository.ScheduleRepository
import com.classing.wear.timetable.ui.state.CourseDetailUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class CourseDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val scheduleRepository: ScheduleRepository,
    timeProvider: TimeProvider,
) : ViewModel() {

    private val courseId: Long = checkNotNull(savedStateHandle["courseId"])
    private val _uiState = MutableStateFlow(CourseDetailUiState())
    val uiState = _uiState.asStateFlow()

    init {
        val weekStart = WeekCalculator.weekStart(timeProvider.today())

        viewModelScope.launch {
            combine(
                scheduleRepository.observeCourseDetail(courseId),
                scheduleRepository.observeWeekSchedule(weekStart),
            ) { course, weekSchedule ->
                val lessons = weekSchedule.days.values.flatten()
                    .filter { it.course.localId == courseId }
                    .sortedBy { it.startAt }

                CourseDetailUiState(
                    isLoading = false,
                    course = course,
                    upcomingLessons = lessons,
                    errorMessage = if (course == null) "课程不存在" else null,
                )
            }.collect { _uiState.value = it }
        }
    }
}
