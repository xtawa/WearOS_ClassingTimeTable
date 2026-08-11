package com.classing.wear.timetable.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.classing.shared.sync.CloudSyncContracts
import com.classing.wear.timetable.core.AppContainer
import com.classing.wear.timetable.core.navigation.AppNavGraph
import com.classing.wear.timetable.domain.repository.UserPreferences
import com.classing.wear.timetable.sync.WearOfficialCloudEventSubscriber
import com.classing.wear.timetable.sync.WearSyncModeStore
import com.classing.wear.timetable.ui.theme.ClassingTimetableTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
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

    ClassingTimetableTheme(useDynamicColor = false) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppNavGraph(appContainer)
                WearFixedTimeText(
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

@Composable
private fun WearFixedTimeText(modifier: Modifier = Modifier) {
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val timeText by produceState(
        initialValue = LocalTime.now().format(formatter),
        key1 = formatter,
    ) {
        while (true) {
            value = LocalTime.now().format(formatter)
            delay(15_000L)
        }
    }

    Row(
        modifier = modifier
            .padding(top = 5.dp)
            .background(
                color = Color.Black.copy(alpha = 0.82f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 9.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
