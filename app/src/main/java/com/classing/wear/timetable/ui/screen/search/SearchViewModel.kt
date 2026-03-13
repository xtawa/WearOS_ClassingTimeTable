package com.classing.wear.timetable.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classing.wear.timetable.domain.repository.ScheduleRepository
import com.classing.wear.timetable.ui.state.SearchUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(
    private val scheduleRepository: ScheduleRepository,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            queryFlow
                .debounce(250)
                .distinctUntilChanged()
                .flatMapLatest { keyword ->
                    if (keyword.isBlank()) flowOf(emptyList()) else scheduleRepository.searchCourses(keyword)
                }
                .collect { list ->
                    _uiState.update { it.copy(isLoading = false, results = list) }
                }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query, isLoading = query.isNotBlank()) }
        queryFlow.value = query
    }
}
