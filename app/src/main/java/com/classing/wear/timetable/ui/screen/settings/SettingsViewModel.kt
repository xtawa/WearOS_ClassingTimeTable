package com.classing.wear.timetable.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classing.shared.sync.CloudSyncContracts
import com.classing.wear.timetable.core.i18n.WearI18n
import com.classing.wear.timetable.domain.model.KeepAliveLevel
import com.classing.wear.timetable.domain.repository.SettingsRepository
import com.classing.wear.timetable.sync.MobileSyncRequester
import com.classing.wear.timetable.sync.WearCloudBridgeSender
import com.classing.wear.timetable.ui.state.SettingsUiState
import com.classing.wear.timetable.ui.state.SyncFeedback
import com.classing.wear.timetable.worker.AutoSyncController
import com.classing.wear.timetable.worker.ReminderWorkController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val mobileSyncRequester: MobileSyncRequester,
    private val wearCloudBridgeSender: WearCloudBridgeSender,
    private val autoSyncController: AutoSyncController,
    private val reminderWorkController: ReminderWorkController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()
    private val syncMessage = MutableStateFlow(WearI18n.syncNever())
    private val syncFeedback = MutableStateFlow<SyncFeedback?>(null)

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.observePreferences(),
                syncMessage,
                syncFeedback,
            ) { pref, syncText, feedback ->
                SettingsUiState(
                    isLoading = false,
                    preferences = pref,
                    syncMessage = syncText,
                    syncFeedback = feedback,
                )
            }.collect { _uiState.value = it }
        }
    }

    fun toggleDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDynamicColor(enabled)
            notifyCloudSettingsChanged()
        }
    }

    fun toggleReminder(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setReminderEnabled(enabled)
            val level = _uiState.value.preferences.keepAliveLevel
            reminderWorkController.setPolicy(enabled = enabled, level = level)
            notifyCloudSettingsChanged()
        }
    }

    fun setKeepAliveLevel(level: KeepAliveLevel) {
        viewModelScope.launch {
            settingsRepository.setKeepAliveLevel(level)
            val remindersEnabled = _uiState.value.preferences.remindersEnabled
            reminderWorkController.setPolicy(enabled = remindersEnabled, level = level)
            notifyCloudSettingsChanged()
        }
    }

    fun setExperimentalAccessibilityKeepAliveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setExperimentalAccessibilityKeepAliveEnabled(enabled)
            notifyCloudSettingsChanged()
        }
    }

    fun toggleAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoSync(enabled)
            autoSyncController.setEnabled(enabled)
            notifyCloudSettingsChanged()
        }
    }

    fun toggleWeekend(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowWeekend(enabled)
            notifyCloudSettingsChanged()
        }
    }

    fun toggleShowCompletedToday(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowCompletedToday(enabled)
            notifyCloudSettingsChanged()
        }
    }

    fun toggleTileShowTeacher(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setTileShowTeacher(enabled)
            notifyCloudSettingsChanged()
        }
    }

    fun toggleTileShowLocation(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setTileShowLocation(enabled)
            notifyCloudSettingsChanged()
        }
    }

    fun toggleTileShowCountdown(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setTileShowCountdown(enabled)
            notifyCloudSettingsChanged()
        }
    }

    fun toggleTileShowCourseName(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setTileShowCourseName(enabled)
            notifyCloudSettingsChanged()
        }
    }

    fun toggleTileShowCurrentWeek(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setTileShowCurrentWeek(enabled)
            notifyCloudSettingsChanged()
        }
    }

    fun toggleTileShowTimeRange(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setTileShowTimeRange(enabled)
            notifyCloudSettingsChanged()
        }
    }

    fun forceFullSync() {
        viewModelScope.launch {
            _uiState.update { it.copy(syncMessage = WearI18n.syncRequesting()) }
            val result = mobileSyncRequester.requestSyncFromPhone()
            val feedback = resolveSyncFeedback(result)
            if (feedback == SyncFeedback.SUCCESS) {
                syncMessage.value = WearI18n.syncRequestSent(result.getOrDefault(0))
                syncFeedback.value = SyncFeedback.SUCCESS
            } else {
                syncMessage.value = WearI18n.syncRequestFailed(WearI18n.syncCheckPhoneConnection())
                syncFeedback.value = SyncFeedback.CHECK_PHONE_CONNECTION
            }
        }
    }

    fun consumeSyncFeedback() {
        syncFeedback.value = null
    }

    private suspend fun notifyCloudSettingsChanged() {
        wearCloudBridgeSender.publishWearSettingsSnapshot(CloudSyncContracts.TRIGGER_SETTINGS_CHANGED)
        wearCloudBridgeSender.requestPhoneCloudSync(CloudSyncContracts.TRIGGER_SETTINGS_CHANGED)
    }
}

internal fun resolveSyncFeedback(result: Result<Int>): SyncFeedback {
    return if (result.isSuccess && result.getOrDefault(0) > 0) {
        SyncFeedback.SUCCESS
    } else {
        SyncFeedback.CHECK_PHONE_CONNECTION
    }
}

