package com.classing.wear.timetable.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classing.shared.sync.CloudSyncContracts
import com.classing.wear.timetable.core.i18n.WearI18n
import com.classing.wear.timetable.domain.model.KeepAliveLevel
import com.classing.wear.timetable.domain.repository.SettingsRepository
import com.classing.wear.timetable.sync.MobileSyncRequester
import com.classing.wear.timetable.sync.WearCloudBridgeSender
import com.classing.wear.timetable.sync.WearOfficialCloudSyncCoordinator
import com.classing.wear.timetable.ui.state.SettingsUiState
import com.classing.wear.timetable.ui.state.SyncFeedback
import com.classing.wear.timetable.worker.AutoSyncController
import com.classing.wear.timetable.worker.ReminderWorkController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val mobileSyncRequester: MobileSyncRequester,
    private val wearCloudBridgeSender: WearCloudBridgeSender,
    private val wearOfficialCloudSyncCoordinator: WearOfficialCloudSyncCoordinator,
    private val isIndependentModeEnabled: () -> Boolean,
    private val autoSyncController: AutoSyncController,
    private val reminderWorkController: ReminderWorkController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()
    private val syncMessage = MutableStateFlow(WearI18n.syncNever())
    private val syncFeedback = MutableStateFlow<SyncFeedback?>(null)
    private var directCloudSyncJob: Job? = null

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

    fun toggleShowAiOnHome(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowAiOnHome(enabled)
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
            val directMode = isIndependentModeEnabled()
            val directResult = if (directMode) {
                wearOfficialCloudSyncCoordinator.sync(CloudSyncContracts.TRIGGER_MANUAL)
            } else {
                null
            }
            val phoneResult = if (directMode) null else mobileSyncRequester.requestSyncFromPhone()
            val feedback = when {
                directResult != null && directResult.getOrNull()?.canSyncTimetable == true -> SyncFeedback.SUCCESS
                phoneResult != null -> resolveSyncFeedback(phoneResult)
                else -> SyncFeedback.CHECK_PHONE_CONNECTION
            }
            if (feedback == SyncFeedback.SUCCESS) {
                syncMessage.value = if (directMode) {
                    WearI18n.directCloudSyncSuccess()
                } else {
                    WearI18n.syncRequestSent(phoneResult?.getOrDefault(0) ?: 0)
                }
                syncFeedback.value = SyncFeedback.SUCCESS
            } else {
                val failureMessage = if (directMode) {
                    when {
                        directResult?.getOrNull()?.canSyncTimetable == false ->
                            WearI18n.timetableMembershipRequired()
                        else -> directResult?.exceptionOrNull()?.message
                            ?.takeIf(String::isNotBlank)
                            ?: WearI18n.syncCheckPhoneConnection()
                    }
                } else {
                    WearI18n.syncCheckPhoneConnection()
                }
                syncMessage.value = WearI18n.syncRequestFailed(failureMessage)
                syncFeedback.value = SyncFeedback.CHECK_PHONE_CONNECTION
            }
        }
    }

    fun consumeSyncFeedback() {
        syncFeedback.value = null
    }

    private suspend fun notifyCloudSettingsChanged() {
        wearCloudBridgeSender.publishWearSettingsSnapshot(CloudSyncContracts.TRIGGER_SETTINGS_CHANGED)
        directCloudSyncJob?.cancel()
        directCloudSyncJob = viewModelScope.launch {
            delay(DIRECT_SYNC_DEBOUNCE_MS)
            wearOfficialCloudSyncCoordinator.sync(CloudSyncContracts.TRIGGER_SETTINGS_CHANGED)
        }
    }

    private companion object {
        const val DIRECT_SYNC_DEBOUNCE_MS = 750L
    }
}

internal fun resolveSyncFeedback(result: Result<Int>): SyncFeedback {
    return if (result.isSuccess && result.getOrDefault(0) > 0) {
        SyncFeedback.SUCCESS
    } else {
        SyncFeedback.CHECK_PHONE_CONNECTION
    }
}
