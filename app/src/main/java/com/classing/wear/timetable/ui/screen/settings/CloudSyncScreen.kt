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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.classing.shared.sync.CloudSyncContracts
import com.classing.wear.timetable.R
import com.classing.wear.timetable.domain.repository.SettingsRepository
import com.classing.wear.timetable.sync.MobileSyncPrefs
import com.classing.wear.timetable.sync.WearCloudBridgeSender
import com.classing.wear.timetable.ui.component.screenPadding
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun CloudSyncScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val bridgeSender = remember { WearCloudBridgeSender(context, settingsRepository) }
    val prefs = remember { context.getSharedPreferences(MobileSyncPrefs.PREF_NAME, android.content.Context.MODE_PRIVATE) }
    var status by remember { mutableStateOf("") }
    var syncing by remember { mutableStateOf(false) }
    val lastSyncAt = prefs.getLong(MobileSyncPrefs.KEY_LAST_SYNC_AT, 0L)
    val cloudSnapshot = remember(status, lastSyncAt) {
        parseWearCloudSnapshot(prefs.getString(MobileSyncPrefs.KEY_LAST_PHONE_CLOUD_SNAPSHOT, "").orEmpty())
    }
    val lastSyncText = if (lastSyncAt > 0L) {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(lastSyncAt), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
    } else {
        stringResource(R.string.settings_cloud_sync_never)
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberScalingLazyListState(),
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
                    text = stringResource(R.string.settings_cloud_sync_phone_managed),
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Text(
                    text = stringResource(R.string.settings_cloud_sync_last_phone_snapshot, lastSyncText) +
                        if (status.isBlank()) "" else "\n$status",
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Text(
                    text = buildString {
                        append("Login: ")
                        append(if (cloudSnapshot.loggedIn) "Yes" else "No")
                        append("\nMember: ")
                        append(if (cloudSnapshot.isMember) cloudSnapshot.membershipTier else "FREE")
                        append("\nProvider: ")
                        append(cloudSnapshot.provider.ifBlank { "-" })
                        append("\nOfficial cloud: ")
                        append(if (cloudSnapshot.officialAvailable) "Available" else "Locked")
                    },
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Button(
                onClick = {
                    scope.launch {
                        syncing = true
                        val settingsResult = bridgeSender.publishWearSettingsSnapshot(CloudSyncContracts.TRIGGER_MANUAL)
                        val requestResult = bridgeSender.requestPhoneCloudSync(CloudSyncContracts.TRIGGER_MANUAL)
                        status = if (settingsResult.isSuccess && requestResult.isSuccess) {
                            context.getString(R.string.settings_cloud_sync_request_queued)
                        } else {
                            context.getString(R.string.settings_cloud_sync_request_failed)
                        }
                        syncing = false
                    }
                },
                enabled = !syncing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(if (syncing) stringResource(R.string.settings_cloud_sync_syncing) else stringResource(R.string.settings_cloud_sync_sync_now))
            }
        }
        item {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(999.dp)) {
                Text(stringResource(R.string.detail_back))
            }
        }
    }
}

private data class WearCloudSnapshotUi(
    val loggedIn: Boolean = false,
    val isMember: Boolean = false,
    val membershipTier: String = "FREE",
    val provider: String = "",
    val officialAvailable: Boolean = false,
)

private fun parseWearCloudSnapshot(raw: String): WearCloudSnapshotUi {
    val json = runCatching { JSONObject(raw) }.getOrNull() ?: return WearCloudSnapshotUi()
    return WearCloudSnapshotUi(
        loggedIn = json.optBoolean("loggedIn", false),
        isMember = json.optBoolean("isMember", false),
        membershipTier = json.optString("membershipTier", "FREE"),
        provider = json.optString("cloudProvider"),
        officialAvailable = json.optBoolean("officialAvailable", false),
    )
}
