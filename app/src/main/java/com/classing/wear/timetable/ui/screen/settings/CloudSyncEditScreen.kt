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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.classing.shared.sync.CloudSyncContracts
import com.classing.wear.timetable.R
import com.classing.wear.timetable.domain.repository.SettingsRepository
import com.classing.wear.timetable.sync.WearCloudBridgeSender
import com.classing.wear.timetable.sync.WearCloudConfig
import com.classing.wear.timetable.sync.WearCloudConfigStore
import com.classing.wear.timetable.ui.component.screenPadding
import kotlinx.coroutines.launch

@Composable
fun CloudSyncEditScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()
    val bridgeSender = remember { WearCloudBridgeSender(context, settingsRepository) }
    val initial = remember { WearCloudConfigStore.load(context) }

    var serverUrl by remember { mutableStateOf(initial.serverUrl) }
    var remotePath by remember { mutableStateOf(initial.remotePath) }
    var username by remember { mutableStateOf(initial.username) }
    var password by remember { mutableStateOf(initial.password) }
    var saving by remember { mutableStateOf(false) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_cloud_sync_edit_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Text(
                    text = stringResource(R.string.settings_cloud_sync_edit_desc),
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.settings_cloud_sync_server_url)) },
            )
        }
        item {
            OutlinedTextField(
                value = remotePath,
                onValueChange = { remotePath = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.settings_cloud_sync_remote_path)) },
            )
        }
        item {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.settings_cloud_sync_username)) },
            )
        }
        item {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.settings_cloud_sync_password)) },
            )
        }
        item {
            Button(
                onClick = {
                    scope.launch {
                        saving = true
                        try {
                            val updatedConfig = WearCloudConfig(
                                enabled = true,
                                serverUrl = serverUrl.trim(),
                                remotePath = remotePath.trim().ifBlank { CloudSyncContracts.DEFAULT_REMOTE_PATH },
                                username = username.trim(),
                                password = password,
                                updatedAt = System.currentTimeMillis(),
                            )
                            WearCloudConfigStore.save(context, updatedConfig)
                            bridgeSender.requestPhoneCloudSync(
                                trigger = CloudSyncContracts.TRIGGER_SETTINGS_CHANGED,
                                wearWebDavSnapshot = updatedConfig,
                            )
                            onBack()
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(stringResource(R.string.settings_cloud_sync_save))
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
