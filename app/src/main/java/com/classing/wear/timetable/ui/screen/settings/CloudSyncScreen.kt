package com.classing.wear.timetable.ui.screen.settings

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.classing.wear.timetable.account.isValidLoginIdentifier
import com.classing.wear.timetable.account.isValidLoginPassword
import com.classing.wear.timetable.domain.repository.SettingsRepository
import com.classing.wear.timetable.security.ClientSignatureException
import com.classing.wear.timetable.sync.MobileSyncPrefs
import com.classing.wear.timetable.sync.WearCloudBridgeSender
import com.classing.wear.timetable.sync.WearOfficialCloudSyncCoordinator
import com.classing.wear.timetable.sync.WearSyncModeStore
import com.classing.wear.timetable.ui.component.screenPadding
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Wear cloud sync. Account state, last sync and the live status message are separated into
 * labelled rows with their own state dot, so "signed out", "waiting", "syncing" and "failed" no
 * longer look like the same grey paragraph. All auth and sync logic is unchanged.
 */
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
    val qrAuthApi = remember { WearQrAuthApiClient(context = context) }
    val prefs = remember {
        context.getSharedPreferences(MobileSyncPrefs.PREF_NAME, android.content.Context.MODE_PRIVATE)
    }
    val directPrefs = remember {
        context.getSharedPreferences(
            WearOfficialCloudSyncCoordinator.PREF_NAME,
            android.content.Context.MODE_PRIVATE,
        )
    }
    var status by remember { mutableStateOf("") }
    var syncing by remember { mutableStateOf(false) }
    var qrBusy by remember { mutableStateOf(false) }
    var manualLoginVisible by remember { mutableStateOf(false) }
    var identifierInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var manualLoginBusy by remember { mutableStateOf(false) }
    var authorization by remember { mutableStateOf<WearDeviceAuthorization?>(null) }
    var directSession by remember {
        mutableStateOf<WearDirectAccountSession?>(WearDirectAccountStore.load(context))
    }
    var independentMode by remember {
        mutableStateOf(directSession != null && WearSyncModeStore.isIndependentModeEnabled(context))
    }
    var snapshotVersion by remember { mutableStateOf(0L) }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == MobileSyncPrefs.KEY_LAST_PHONE_CLOUD_SNAPSHOT ||
                key == MobileSyncPrefs.KEY_LAST_PHONE_CLOUD_SNAPSHOT_AT
            ) {
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
        parseWearCloudSnapshot(
            prefs.getString(MobileSyncPrefs.KEY_LAST_PHONE_CLOUD_SNAPSHOT, "").orEmpty(),
        )
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
                    independentMode = WearSyncModeStore.setIndependentMode(context, true)
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
    val busy = syncing || qrBusy || manualLoginBusy

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberScalingLazyListState(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_cloud_sync_title),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }

        // The live status message is the most volatile thing on this screen, so it leads and
        // carries a state colour instead of being buried in a grey paragraph.
        if (status.isNotBlank()) {
            item {
                CloudStateCard(
                    text = status,
                    accent = if (busy) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CloudStatusRow(
                        label = "Login",
                        value = if (effectiveLoggedIn) "Yes" else "No",
                        accent = if (effectiveLoggedIn) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                    CloudStatusRow(
                        label = "Member",
                        value = if (effectiveMember) effectiveTier else "FREE",
                        accent = if (effectiveMember) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                    CloudStatusRow(
                        label = "Provider",
                        value = effectiveProvider.ifBlank { "-" },
                        accent = MaterialTheme.colorScheme.outline,
                    )
                    CloudStatusRow(
                        label = "Official cloud",
                        value = if (effectiveLoggedIn) "Available" else "Locked",
                        accent = if (effectiveLoggedIn) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_cloud_sync_last_phone_snapshot, lastSyncText),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_cloud_sync_phone_managed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!effectiveLoggedIn && authorization == null) {
            item {
                CloudActionButton(
                    label = stringResource(R.string.settings_qr_login_button),
                    enabled = !qrBusy,
                    primary = true,
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
                )
            }
            item {
                CloudActionButton(
                    label = stringResource(R.string.settings_account_login_button),
                    enabled = !manualLoginBusy,
                    onClick = { manualLoginVisible = !manualLoginVisible },
                )
            }
            if (manualLoginVisible) {
                item {
                    OutlinedTextField(
                        value = identifierInput,
                        onValueChange = { identifierInput = it },
                        label = { Text(stringResource(R.string.settings_account_login_identifier)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                }
                item {
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(stringResource(R.string.settings_account_login_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                }
                item {
                    CloudActionButton(
                        label = if (manualLoginBusy) {
                            stringResource(R.string.settings_account_login_in_progress)
                        } else {
                            stringResource(R.string.settings_account_login_submit)
                        },
                        enabled = !manualLoginBusy &&
                            isValidLoginIdentifier(identifierInput) &&
                            isValidLoginPassword(passwordInput),
                        primary = true,
                        onClick = {
                            scope.launch {
                                manualLoginBusy = true
                                status = context.getString(R.string.settings_account_login_in_progress)
                                val result = qrAuthApi.login(identifierInput, passwordInput)
                                if (result.isSuccess) {
                                    val session = result.getOrThrow()
                                    WearDirectAccountStore.save(context, session)
                                    directSession = session
                                    independentMode = WearSyncModeStore.setIndependentMode(context, true)
                                    manualLoginVisible = false
                                    identifierInput = ""
                                    passwordInput = ""
                                    status = context.getString(R.string.settings_qr_login_syncing)
                                    val syncResult = directCloudSync.sync(CloudSyncContracts.TRIGGER_APP_START)
                                    directSession = WearDirectAccountStore.load(context)
                                    status = if (syncResult.isSuccess) {
                                        context.getString(R.string.settings_account_login_success)
                                    } else {
                                        context.getString(R.string.settings_qr_login_sync_failed)
                                    }
                                } else {
                                    val error = result.exceptionOrNull()
                                    status = when {
                                        error is WearQrAuthException && error.statusCode == 401 ->
                                            context.getString(R.string.settings_account_login_invalid_credentials)
                                        error is WearQrAuthException &&
                                            error.errorCode.contains("SIGNATURE", ignoreCase = true) ->
                                            context.getString(R.string.settings_account_login_signature_error)
                                        error is WearQrAuthException ->
                                            error.message?.ifBlank {
                                                context.getString(R.string.settings_account_login_network_error)
                                            } ?: context.getString(R.string.settings_account_login_network_error)
                                        error is ClientSignatureException ->
                                            context.getString(R.string.settings_account_login_signature_error)
                                        else -> context.getString(R.string.settings_account_login_network_error)
                                    }
                                }
                                manualLoginBusy = false
                            }
                        },
                    )
                }
            }
        }

        authorization?.let { active ->
            item {
                // The QR code keeps a pure white plate on purpose. Scanners need the contrast,
                // so this is the one surface that does not follow the dark theme.
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Image(
                        bitmap = remember(active.qrPayload) {
                            createWearLoginQrBitmap(active.qrPayload)
                        }.asImageBitmap(),
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
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            item {
                CloudActionButton(
                    label = stringResource(R.string.settings_qr_login_cancel),
                    onClick = {
                        authorization = null
                        qrBusy = false
                        status = ""
                    },
                )
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(0.78f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_independent_mode_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.settings_independent_mode_description),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = independentMode,
                        onCheckedChange = {
                            independentMode = WearSyncModeStore.setIndependentMode(context, it)
                        },
                        enabled = directSession != null,
                    )
                }
            }
        }

        directSession?.let { session ->
            item {
                CloudActionButton(
                    label = stringResource(R.string.settings_qr_logout_button),
                    enabled = !qrBusy,
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
                )
            }
        }

        item {
            CloudActionButton(
                label = if (syncing) {
                    stringResource(R.string.settings_cloud_sync_syncing)
                } else {
                    stringResource(R.string.settings_cloud_sync_sync_now)
                },
                enabled = !syncing,
                primary = true,
                onClick = {
                    scope.launch {
                        syncing = true
                        val directModeActive = independentMode && directSession != null
                        val directResult = if (directModeActive) {
                            directCloudSync.sync(CloudSyncContracts.TRIGGER_MANUAL)
                        } else {
                            null
                        }
                        val settingsResult = if (directModeActive) {
                            Result.success(0)
                        } else {
                            bridgeSender.publishWearSettingsSnapshot(CloudSyncContracts.TRIGGER_MANUAL)
                        }
                        val requestResult = if (directModeActive) {
                            Result.success(0)
                        } else {
                            bridgeSender.requestPhoneCloudSync(CloudSyncContracts.TRIGGER_MANUAL)
                        }
                        directSession = WearDirectAccountStore.load(context)
                        status = when {
                            directResult?.isSuccess == true ->
                                context.getString(R.string.settings_cloud_sync_direct_success)
                            directResult != null ->
                                context.getString(R.string.settings_cloud_sync_direct_failed)
                            settingsResult.isSuccess && requestResult.isSuccess ->
                                context.getString(R.string.settings_cloud_sync_request_queued)
                            else -> context.getString(R.string.settings_cloud_sync_request_failed)
                        }
                        syncing = false
                    }
                },
            )
        }
        item {
            CloudActionButton(
                label = stringResource(R.string.detail_back),
                onClick = onBack,
            )
        }
    }
}

@Composable
private fun CloudStateCard(
    text: String,
    accent: Color,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(6.dp)
                    .background(accent, CircleShape),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun CloudStatusRow(
    label: String,
    value: String,
    accent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(accent, CircleShape),
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CloudActionButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(999.dp),
        colors = if (primary) {
            ButtonDefaults.buttonColors()
        } else {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
