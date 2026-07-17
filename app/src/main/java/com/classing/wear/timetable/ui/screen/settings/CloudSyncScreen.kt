package com.classing.wear.timetable.ui.screen.settings

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.classing.shared.sync.CloudSyncContracts
import com.classing.wear.timetable.R
import com.classing.wear.timetable.account.WearDeviceAuthorization
import com.classing.wear.timetable.account.WearDeviceAuthorizationPoll
import com.classing.wear.timetable.account.WearDirectAccountSession
import com.classing.wear.timetable.account.WearDirectAccountStore
import com.classing.wear.timetable.account.WearQrAuthApiClient
import com.classing.wear.timetable.account.WearQrAuthException
import com.classing.wear.timetable.account.createWearLoginQrBitmap
import com.classing.wear.timetable.domain.repository.SettingsRepository
import com.classing.wear.timetable.sync.MobileSyncPrefs
import com.classing.wear.timetable.sync.WearCloudBridgeSender
import com.classing.wear.timetable.ui.component.screenPadding
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable
fun CloudSyncScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val bridgeSender = remember { WearCloudBridgeSender(context, settingsRepository) }
    val qrAuthApi = remember { WearQrAuthApiClient() }
    val prefs = remember { context.getSharedPreferences(MobileSyncPrefs.PREF_NAME, android.content.Context.MODE_PRIVATE) }
    var status by remember { mutableStateOf("") }
    var syncing by remember { mutableStateOf(false) }
    var qrBusy by remember { mutableStateOf(false) }
    var authorization by remember { mutableStateOf<WearDeviceAuthorization?>(null) }
    var directSession by remember { mutableStateOf<WearDirectAccountSession?>(WearDirectAccountStore.load(context)) }
    var snapshotVersion by remember { mutableStateOf(0L) }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == MobileSyncPrefs.KEY_LAST_PHONE_CLOUD_SNAPSHOT || key == MobileSyncPrefs.KEY_LAST_PHONE_CLOUD_SNAPSHOT_AT) {
                snapshotVersion = System.nanoTime()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val lastSyncAt = remember(snapshotVersion) {
        prefs.getLong(MobileSyncPrefs.KEY_LAST_PHONE_CLOUD_SNAPSHOT_AT, 0L)
    }
    val cloudSnapshot = remember(snapshotVersion) {
        parseWearCloudSnapshot(prefs.getString(MobileSyncPrefs.KEY_LAST_PHONE_CLOUD_SNAPSHOT, "").orEmpty())
    }
    val effectiveLoggedIn = cloudSnapshot.loggedIn || directSession != null
    val effectiveMember = directSession?.isMember ?: cloudSnapshot.isMember
    val effectiveTier = directSession?.membershipTier ?: cloudSnapshot.membershipTier
    val effectiveProvider = if (directSession != null && cloudSnapshot.provider.isBlank()) "OFFICIAL" else cloudSnapshot.provider

    LaunchedEffect(authorization?.authorizationId) {
        val active = authorization ?: return@LaunchedEffect
        var intervalSeconds = active.intervalSeconds
        while (System.currentTimeMillis() < active.expiresAt) {
            delay(intervalSeconds * 1_000L)
            val result = qrAuthApi.poll(active)
            if (result.isFailure) {
                val error = result.exceptionOrNull()
                status = if (error is WearQrAuthException && error.errorCode == "DEVICE_AUTH_EXPIRED") {
                    context.getString(R.string.settings_qr_login_expired)
                } else {
                    context.getString(R.string.settings_qr_login_failed)
                }
                authorization = null
                qrBusy = false
                return@LaunchedEffect
            }
            when (val poll = result.getOrThrow()) {
                is WearDeviceAuthorizationPoll.Pending -> intervalSeconds = poll.intervalSeconds
                is WearDeviceAuthorizationPoll.Approved -> {
                    WearDirectAccountStore.save(context, poll.session)
                    directSession = poll.session
                    authorization = null
                    qrBusy = false
                    status = context.getString(R.string.settings_qr_login_success)
                    return@LaunchedEffect
                }
            }
        }
        authorization = null
        qrBusy = false
        status = context.getString(R.string.settings_qr_login_expired)
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
                        append(if (effectiveLoggedIn) "Yes" else "No")
                        append("\nMember: ")
                        append(if (effectiveMember) effectiveTier else "FREE")
                        append("\nProvider: ")
                        append(effectiveProvider.ifBlank { "-" })
                        append("\nOfficial cloud: ")
                        append(if (effectiveLoggedIn) "Available" else "Locked")
                    },
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (!effectiveLoggedIn && authorization == null) {
            item {
                Button(
                    onClick = {
                        scope.launch {
                            qrBusy = true
                            status = context.getString(R.string.settings_qr_login_creating)
                            val result = qrAuthApi.start("${Build.MANUFACTURER} ${Build.MODEL}")
                            if (result.isSuccess) {
                                authorization = result.getOrThrow()
                                status = context.getString(R.string.settings_qr_login_waiting)
                            } else {
                                qrBusy = false
                                status = context.getString(R.string.settings_qr_login_failed)
                            }
                        }
                    },
                    enabled = !qrBusy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(stringResource(R.string.settings_qr_login_button))
                }
            }
        }
        authorization?.let { active ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Image(
                        bitmap = remember(active.qrPayload) { createWearLoginQrBitmap(active.qrPayload) }.asImageBitmap(),
                        contentDescription = stringResource(R.string.settings_qr_login_qr_description),
                        modifier = Modifier
                            .size(168.dp)
                            .background(Color.White)
                            .padding(6.dp),
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.settings_qr_login_instruction),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                Button(
                    onClick = {
                        authorization = null
                        qrBusy = false
                        status = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(stringResource(R.string.settings_qr_login_cancel))
                }
            }
        }
        directSession?.let { session ->
            item {
                Button(
                    onClick = {
                        scope.launch {
                            qrBusy = true
                            qrAuthApi.logout(session)
                            WearDirectAccountStore.clear(context)
                            directSession = null
                            qrBusy = false
                            status = context.getString(R.string.settings_qr_logout_success)
                        }
                    },
                    enabled = !qrBusy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(stringResource(R.string.settings_qr_logout_button))
                }
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
