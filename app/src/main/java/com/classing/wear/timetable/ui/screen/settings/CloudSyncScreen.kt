package com.classing.wear.timetable.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.classing.shared.sync.CloudProvider
import com.classing.shared.sync.CloudSyncContracts
import com.classing.wear.timetable.R
import com.classing.wear.timetable.domain.repository.SettingsRepository
import com.classing.wear.timetable.sync.WearCloudBridgeSender
import com.classing.wear.timetable.sync.WearCloudConfig
import com.classing.wear.timetable.sync.WearCloudConfigStore
import com.classing.wear.timetable.sync.WearCloudSyncCoordinator
import com.classing.wear.timetable.ui.component.screenPadding
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun CloudSyncScreen(
    settingsRepository: SettingsRepository,
    onOpenEdit: () -> Unit,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()
    val bridgeSender = remember { WearCloudBridgeSender(context, settingsRepository) }

    var config by remember { mutableStateOf(WearCloudConfigStore.load(context)) }
    var status by remember { mutableStateOf(WearCloudConfigStore.loadSyncStatus(context).first) }
    var lastSyncedAt by remember { mutableStateOf(WearCloudConfigStore.loadSyncStatus(context).second) }
    var configUpdateStatus by remember { mutableStateOf(WearCloudConfigStore.loadConfigUpdateStatus(context).first) }
    var configUpdatedAt by remember { mutableStateOf(WearCloudConfigStore.loadConfigUpdateStatus(context).second) }
    var syncing by remember { mutableStateOf(false) }

    fun refreshUiState() {
        config = WearCloudConfigStore.load(context)
        val (s, at) = WearCloudConfigStore.loadSyncStatus(context)
        status = s
        lastSyncedAt = at
        val (updateMessage, updateAt) = WearCloudConfigStore.loadConfigUpdateStatus(context)
        configUpdateStatus = updateMessage
        configUpdatedAt = updateAt
    }

    LaunchedEffect(Unit) {
        refreshUiState()
    }

    val lastSyncText = if (lastSyncedAt > 0L) {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(lastSyncedAt), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
    } else {
        stringResource(R.string.settings_cloud_sync_never)
    }
    val configUpdatedText = if (configUpdatedAt > 0L) {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(configUpdatedAt), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
    } else {
        stringResource(R.string.settings_cloud_sync_never)
    }
    val driveExpireText = if (config.driveAccessTokenExpireAt > 0L) {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(config.driveAccessTokenExpireAt), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
    } else {
        stringResource(R.string.settings_cloud_sync_never)
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_cloud_sync_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Text(
                    text = if (config.isComplete()) {
                        stringResource(R.string.settings_cloud_sync_config_ready)
                    } else {
                        stringResource(R.string.settings_cloud_sync_config_missing)
                    },
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Text(
                    text = stringResource(
                        R.string.settings_cloud_sync_provider_wear,
                        if (config.provider == CloudProvider.GOOGLE_DRIVE) "Google Drive" else "WebDAV",
                    ),
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Text(
                    text = if (config.provider == CloudProvider.WEBDAV) {
                        stringResource(R.string.settings_cloud_sync_phone_only_hint)
                    } else {
                        stringResource(R.string.settings_cloud_sync_wear_drive_hint)
                    },
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (config.provider == CloudProvider.WEBDAV) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Text(
                        text = stringResource(
                            R.string.settings_cloud_sync_wear_config_label,
                            config.serverUrl.ifBlank { "-" },
                            config.remotePath.ifBlank { "-" },
                            config.username.ifBlank { "-" },
                        ),
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        } else {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Text(
                        text = stringResource(
                            R.string.settings_cloud_sync_wear_drive_status_label,
                            config.driveFileName.ifBlank { "-" },
                            driveExpireText,
                        ),
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Text(
                    text = stringResource(
                        R.string.settings_cloud_sync_wear_status_label,
                        status.ifBlank { "-" },
                        lastSyncText,
                    ),
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (configUpdateStatus.isNotBlank()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Text(
                        text = stringResource(
                            R.string.settings_cloud_sync_wear_config_update_label,
                            configUpdateStatus,
                            configUpdatedText,
                        ),
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (config.provider == CloudProvider.WEBDAV) {
            item {
                Button(
                    onClick = onOpenEdit,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(stringResource(R.string.settings_cloud_sync_edit_button))
                }
            }
        }
        item {
            Button(
                onClick = {
                    scope.launch {
                        syncing = true
                        try {
                            bridgeSender.publishWearSettingsSnapshot(CloudSyncContracts.TRIGGER_MANUAL)
                            bridgeSender.requestPhoneCloudSync(CloudSyncContracts.TRIGGER_MANUAL)
                            WearCloudSyncCoordinator.pullFromCloud(
                                context = context,
                                trigger = CloudSyncContracts.TRIGGER_MANUAL,
                                force = true,
                            )
                            refreshUiState()
                        } finally {
                            syncing = false
                        }
                    }
                },
                enabled = !syncing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    if (syncing) {
                        stringResource(R.string.settings_cloud_sync_syncing)
                    } else {
                        stringResource(R.string.settings_cloud_sync_sync_now)
                    },
                )
            }
        }
        item {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(stringResource(R.string.detail_back))
            }
        }
    }
}
