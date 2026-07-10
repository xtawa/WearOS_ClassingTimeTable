package com.classing.wear.timetable.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import com.classing.shared.sync.CloudSyncContracts
import com.classing.wear.timetable.core.AppContainer
import com.classing.wear.timetable.core.navigation.AppNavGraph
import com.classing.wear.timetable.domain.repository.UserPreferences
import com.classing.wear.timetable.ui.theme.ClassingTimetableTheme
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.delay

@Composable
fun ClassingTimetableApp(appContainer: AppContainer) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val preferences by appContainer.settingsRepository
        .observePreferences()
        .collectAsStateWithLifecycle(initialValue = UserPreferences())

    LaunchedEffect(Unit) {
        context.deleteSharedPreferences("wear_cloud_config")
        appContainer.wearCloudBridgeSender.publishWearSettingsSnapshot(CloudSyncContracts.TRIGGER_APP_START)
        appContainer.wearCloudBridgeSender.requestPhoneCloudSync(CloudSyncContracts.TRIGGER_APP_START)
    }

    LaunchedEffect(lifecycleOwner) {
        while (true) {
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                appContainer.wearCloudBridgeSender.publishWearSettingsSnapshot(CloudSyncContracts.TRIGGER_FOREGROUND_TICK)
                appContainer.wearCloudBridgeSender.requestPhoneCloudSync(CloudSyncContracts.TRIGGER_FOREGROUND_TICK)
            }
            delay(120_000L)
        }
    }

    ClassingTimetableTheme(useDynamicColor = preferences.dynamicColor) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AppNavGraph(appContainer)
        }
    }
}
