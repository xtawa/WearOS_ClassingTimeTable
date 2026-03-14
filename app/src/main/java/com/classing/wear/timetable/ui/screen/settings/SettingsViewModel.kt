package com.classing.wear.timetable.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classing.wear.timetable.core.i18n.WearI18n
import com.classing.wear.timetable.domain.repository.SettingsRepository
import com.classing.wear.timetable.sync.MobileSyncRequester
import com.classing.wear.timetable.ui.state.SettingsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val mobileSyncRequester: MobileSyncRequester,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()
    private val syncMessage = MutableStateFlow(WearI18n.syncNever())

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.observePreferences(),
                syncMessage,
            ) { pref, syncText ->
                SettingsUiState(
                    isLoading = false,
                    preferences = pref,
                    syncMessage = syncText,
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
            _uiState.update { it.copy(syncMessage = WearI18n.syncRequesting()) }
            val result = mobileSyncRequester.requestSyncFromPhone()
            syncMessage.value = if (result.isSuccess) {
                WearI18n.syncRequestSent(result.getOrDefault(0))
            } else {
                WearI18n.syncRequestFailed(result.exceptionOrNull()?.message ?: "unknown")
            }
        }
    }
}

