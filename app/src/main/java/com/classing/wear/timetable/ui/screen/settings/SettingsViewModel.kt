package com.classing.wear.timetable.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classing.wear.timetable.domain.model.SyncMode
import com.classing.wear.timetable.domain.repository.SettingsRepository
import com.classing.wear.timetable.domain.repository.SyncRepository
import com.classing.wear.timetable.ui.state.SettingsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.observePreferences(),
                syncRepository.observeSyncMetadata(),
            ) { pref, syncMeta ->
                SettingsUiState(
                    isLoading = false,
                    preferences = pref,
                    syncMessage = syncMeta.lastSuccessAt?.toString() ?: "尚未同步",
                )
            }.collect { _uiState.value = it }
        }
    }

    fun toggleDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun toggleReminder(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setReminderEnabled(enabled) }
    }

    fun toggleAutoSync(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoSync(enabled) }
    }

    fun toggleWeekend(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowWeekend(enabled) }
    }

    fun forceFullSync() {
        viewModelScope.launch {
            syncRepository.sync(SyncMode.FULL)
        }
    }
}
