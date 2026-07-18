package com.classing.wear.timetable.ui.screen.settings

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Switch
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
import com.classing.wear.timetable.account.WearDirectAccountSessionManager
import com.classing.wear.timetable.account.WearDirectAccountStore
import com.classing.wear.timetable.account.WearQrAuthApiClient
import com.classing.wear.timetable.account.WearQrAuthException
import com.classing.wear.timetable.account.createWearLoginQrBitmap
import com.classing.wear.timetable.domain.repository.SettingsRepository
import com.classing.wear.timetable.sync.MobileSyncPrefs
import com.classing.wear.timetable.sync.WearCloudBridgeSender
import com.classing.wear.timetable.sync.WearOfficialCloudSyncCoordinator
import com.classing.wear.timetable.sync.WearSyncModeStore
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
    wearOfficialCloudSyncCoordinator: WearOfficialCloudSyncCoordinator,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val bridgeSender = remember { WearCloudBridgeSender(context, settingsRepository) }
    val directCloudSync = wearOfficialCloudSyncCoordinator
    val qrAuthApi = remember { WearQrAuthApiClient() }
    val prefs = remember { context.getSharedPreferences(MobileSyncPrefs.PREF_NAME, android.content.Context.MODE_PRIVATE) }
    val directPrefs = remember {
        context.getSharedPreferences(WearOfficialCloudSyncCoordinator.PREF_NAME, android.content.Context.MODE_PRIVATE)
    }
    var status by remember { mutableStateOf("") }
    var syncing by remember { mutableStateOf(false) }
    var qrBusy by remember { mutableStateOf(false) }
    var authorization by remember { mutableStateOf<WearDeviceAuthorization?>(null) }
    var directSession by remember { mutableStateOf<WearDirectAccountSession?>(WearDirectAccountStore.load(context)) }
    var independentMode by remember {
        mutableStateOf(directSession != null && WearSyncModeStore.isIndependentModeEnabled(context))
    }
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
    DisposableEffect(directPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == WearOfficialCloudSyncCoordinator.KEY_LAST_SYNC_AT ||
                key == WearOfficialCloudSyncCoordinator.KEY_LAST_ERROR
            ) {
                snapshotVersion = System.nanoTime()
            }
        }
        directPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { directPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val lastSyncAt = remember(snapshotVersion) {
        maxOf(
            prefs.getLong(MobileSyncPrefs.KEY_LAST_PHONE_CLOUD_SNAPSHOT_AT, 0L),
            directPrefs.getLong(WearOfficialCloudSyncCoordinator.KEY_LAST_SYNC_AT, 0L),
        )
    }
    val cloudSnapshot = remember(snapshotVersion) {
        parseWearCloudSnapshot(prefs.getString(MobileSyncPrefs.KEY_LAST_PHONE_CLOUD_SNAPSHOT, "").orEmpty())
    }
    val effectiveLoggedIn = cloudSnapshot.loggedIn || directSession != null
    val effectiveMember = directSession?.isMember ?: cloudSnapshot.isMember
    val effectiveTier = directSession?.membershipTier ?: cloudSnapshot.membershipTier
    val effectiveProvider = if (directSession != null) "OFFICIAL" else cloudSnapshot.provider

    LaunchedEffect(authorization?.authorizationId) {
        val active = authorization ?: return@LaunchedEffect
        var intervalSeconds = active.intervalSeconds
        while (System.currentTimeMillis() < active.expiresAt) {
            delay(intervalSeconds * 1_000L)
            val result = qrAuthApi.poll(active)
            if (result.isFailure) {
                val error = result.exceptionOrNull()
                val fatal = error is WearQrAuthException &&
                    error.statusCode in 400..499 && error.statusCode != 429
                if (!fatal) {
                    status = context.getString(R.string.settings_qr_login_waiting)
                    continue
                }
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
                    status = context.getString(R.string.settings_qr_login_syncing)
                    val syncResult = directCloudSync.sync(CloudSyncContracts.TRIGGER_APP_START)
                    directSession = WearDirectAccountStore.load(context)
                    qrBusy = false
                    status = if (syncResult.isSuccess) {
                        context.getString(R.string.settings_qr_login_sync_success)
                    } else {
                        context.getString(R.string.settings_qr_login_sync_failed)
                    }
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
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth(0.78f)) {
                            Text(stringResource(R.string.settings_independent_mode_title), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.settings_independent_mode_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = independentMode,
                            onCheckedChange = { independentMode = WearSyncModeStore.setIndependentMode(context, it) },
                            enabled = directSession != null,
                        )
                    }
            }
        }
        directSession?.let { session ->
            item {
                Button(
                    onClick = {
                        scope.launch {
                            qrBusy = true
                            val latestSession = WearDirectAccountSessionManager.ensureSession(
                                context = context,
                                apiClient = qrAuthApi,
                            ) ?: WearDirectAccountStore.load(context) ?: session
                            qrAuthApi.logout(latestSession)
                            WearSyncModeStore.setIndependentMode(context, false)
                            WearDirectAccountStore.clear(context)
                            directSession = null
                            independentMode = false
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
                        val directResult = if (independentMode && directSession != null) {
                            directCloudSync.sync(CloudSyncContracts.TRIGGER_MANUAL)
                        } else {
                            null
                        }
                        val settingsResult = bridgeSender.publishWearSettingsSnapshot(CloudSyncContracts.TRIGGER_MANUAL)
                        val requestResult = bridgeSender.requestPhoneCloudSync(CloudSyncContracts.TRIGGER_MANUAL)
                        directSession = WearDirectAccountStore.load(context)
                        status = when {
                            directResult?.isSuccess == true -> context.getString(R.string.settings_cloud_sync_direct_success)
                            directResult != null -> context.getString(R.string.settings_cloud_sync_direct_failed)
                            settingsResult.isSuccess && requestResult.isSuccess -> context.getString(R.string.settings_cloud_sync_request_queued)
                            else -> context.getString(R.string.settings_cloud_sync_request_failed)
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
