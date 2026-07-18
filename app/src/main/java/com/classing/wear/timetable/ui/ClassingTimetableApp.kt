package com.classing.wear.timetable.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.classing.shared.sync.CloudSyncContracts
import com.classing.wear.timetable.core.AppContainer
import com.classing.wear.timetable.core.navigation.AppNavGraph
import com.classing.wear.timetable.sync.WearOfficialCloudEventSubscriber
import com.classing.wear.timetable.sync.WearSyncModeStore
import com.classing.wear.timetable.domain.repository.UserPreferences
import com.classing.wear.timetable.ui.theme.ClassingTimetableTheme
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.delay

@Composable
fun ClassingTimetableApp(appContainer: AppContainer) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val eventSubscriber = remember(appContainer) {
        WearOfficialCloudEventSubscriber(context, appContainer.wearOfficialCloudSyncCoordinator)
    }
    val preferences by appContainer.settingsRepository
        .observePreferences()
        .collectAsStateWithLifecycle(initialValue = UserPreferences())

    LaunchedEffect(Unit) {
        context.deleteSharedPreferences("wear_cloud_config")
        if (WearSyncModeStore.isIndependentModeEnabled(context)) {
            appContainer.wearOfficialCloudSyncCoordinator.sync(CloudSyncContracts.TRIGGER_APP_START)
        } else {
            appContainer.wearCloudBridgeSender.publishWearSettingsSnapshot(CloudSyncContracts.TRIGGER_APP_START)
            appContainer.wearCloudBridgeSender.requestPhoneCloudSync(CloudSyncContracts.TRIGGER_APP_START)
        }
    }

    LaunchedEffect(lifecycleOwner, eventSubscriber) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                if (WearSyncModeStore.isIndependentModeEnabled(context)) {
                    eventSubscriber.runWhileIndependent()
                } else {
                    delay(1_000L)
                }
            }
        }
    }

    LaunchedEffect(lifecycleOwner, preferences.autoSync) {
        while (true) {
            // The app-start effect already performs the initial dispatch. Waiting first avoids
            // duplicate DataItems and duplicate cloud requests during composition.
            delay(120_000L)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                if (WearSyncModeStore.isIndependentModeEnabled(context)) {
                    if (preferences.autoSync) {
                        appContainer.wearOfficialCloudSyncCoordinator.sync(CloudSyncContracts.TRIGGER_FOREGROUND_TICK)
                    }
                } else {
                    appContainer.wearCloudBridgeSender.publishWearSettingsSnapshot(CloudSyncContracts.TRIGGER_FOREGROUND_TICK)
                    appContainer.wearCloudBridgeSender.requestPhoneCloudSync(CloudSyncContracts.TRIGGER_FOREGROUND_TICK)
                }
            }
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
