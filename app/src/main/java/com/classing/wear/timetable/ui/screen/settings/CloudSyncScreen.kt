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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.classing.wear.timetable.sync.WearOfficialCloudHttpException
import com.classing.wear.timetable.sync.WearOfficialCloudLoginRequiredException
import com.classing.wear.timetable.sync.WearSyncModeStore
import com.classing.wear.timetable.ui.component.ClassingWearBackground
import com.classing.wear.timetable.ui.component.ClassingIsland
import com.classing.wear.timetable.ui.component.WearPageHeader
import com.classing.wear.timetable.ui.theme.ClassingWearRadii
import com.classing.wear.timetable.ui.theme.ClassingWearSpacing
import com.classing.wear.timetable.ui.component.screenPadding
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val directCanSyncTimetable = remember(snapshotVersion) {
        directCloudSync.lastCanSyncTimetable()
    }
    val storedDirectError = remember(snapshotVersion) { directCloudSync.lastError() }
    val displayedStatus = status.ifBlank { storedDirectError }
    val effectiveMember = directCanSyncTimetable
        ?: directSession?.isMember
        ?: cloudSnapshot.isMember
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
                    independentMode = WearSyncModeStore.setIndependentMode(context, false)
                    authorization = null
                    status = context.getString(R.string.settings_qr_login_syncing)
                    val syncResult = directCloudSync.sync(CloudSyncContracts.TRIGGER_APP_START)
                    directSession = WearDirectAccountStore.load(context)
                    qrBusy = false
                    val outcome = syncResult.getOrNull()
                    independentMode = WearSyncModeStore.setIndependentMode(
                        context,
                        outcome?.canSyncTimetable == true,
                    )
                    status = directSyncStatusText(
                        context = context,
                        result = syncResult,
                        loginCompleted = true,
                    )
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

    ClassingWearBackground {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberScalingLazyListState(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(ClassingWearSpacing.md),
    ) {
        item {
            WearPageHeader(
                title = stringResource(R.string.settings_cloud_sync_title),
                eyebrow = stringResource(R.string.home_brand_wordmark),
            )
        }
        item {
            ClassingIsland {
                Text(
                    text = stringResource(R.string.settings_cloud_sync_phone_managed),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            ClassingIsland(emphasized = displayedStatus.isNotBlank()) {
                Text(
                    text = stringResource(R.string.settings_cloud_sync_last_phone_snapshot, lastSyncText) +
                        if (displayedStatus.isBlank()) "" else "\n$displayedStatus",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            ClassingIsland {
                Text(
                    text = stringResource(
                        R.string.settings_cloud_sync_account_summary,
                        if (effectiveLoggedIn) stringResource(R.string.common_yes) else stringResource(R.string.common_no),
                        if (effectiveMember) effectiveTier else "FREE",
                        effectiveProvider.ifBlank { "-" },
                        when {
                            !effectiveLoggedIn -> stringResource(R.string.settings_cloud_sync_locked)
                            effectiveMember -> stringResource(R.string.settings_cloud_sync_timetable_available)
                            else -> stringResource(R.string.settings_cloud_sync_settings_only)
                        },
                    ),
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
                    shape = RoundedCornerShape(ClassingWearRadii.pill),
                ) {
                    Text(stringResource(R.string.settings_qr_login_button))
                }
            }
            item {
                Button(
                    onClick = { manualLoginVisible = !manualLoginVisible },
                    enabled = !manualLoginBusy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(ClassingWearRadii.pill),
                ) {
                    Text(stringResource(R.string.settings_account_login_button))
                }
            }
            if (manualLoginVisible) {
                item {
                    OutlinedTextField(
                        value = identifierInput,
                        onValueChange = { identifierInput = it },
                        label = { Text(stringResource(R.string.settings_account_login_identifier)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
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
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                }
                item {
                    Button(
                        onClick = {
                            scope.launch {
                                manualLoginBusy = true
                                status = context.getString(R.string.settings_account_login_in_progress)
                                val result = qrAuthApi.login(identifierInput, passwordInput)
                                if (result.isSuccess) {
                                    val session = result.getOrThrow()
                                    WearDirectAccountStore.save(context, session)
                                    directSession = session
                                    independentMode = WearSyncModeStore.setIndependentMode(context, false)
                                    manualLoginVisible = false
                                    identifierInput = ""
                                    passwordInput = ""
                                    status = context.getString(R.string.settings_qr_login_syncing)
                                    val syncResult = directCloudSync.sync(CloudSyncContracts.TRIGGER_APP_START)
                                    directSession = WearDirectAccountStore.load(context)
                                    val outcome = syncResult.getOrNull()
                                    independentMode = WearSyncModeStore.setIndependentMode(
                                        context,
                                        outcome?.canSyncTimetable == true,
                                    )
                                    status = directSyncStatusText(
                                        context = context,
                                        result = syncResult,
                                        loginCompleted = true,
                                    )
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
                        enabled = !manualLoginBusy &&
                            isValidLoginIdentifier(identifierInput) &&
                            isValidLoginPassword(passwordInput),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(ClassingWearRadii.pill),
                    ) {
                        Text(
                            if (manualLoginBusy) {
                                stringResource(R.string.settings_account_login_in_progress)
                            } else {
                                stringResource(R.string.settings_account_login_submit)
                            },
                        )
                    }
                }
            }
        }
        authorization?.let { active ->
            item {
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
                    shape = RoundedCornerShape(ClassingWearRadii.pill),
                ) {
                    Text(stringResource(R.string.settings_qr_login_cancel))
                }
            }
        }
        item {
            ClassingIsland {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.fillMaxWidth(0.78f)) {
                        Text(
                            stringResource(R.string.settings_independent_mode_title),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.settings_independent_mode_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = independentMode,
                        onCheckedChange = {
                            if (!it) {
                                independentMode = WearSyncModeStore.setIndependentMode(context, false)
                            } else {
                                scope.launch {
                                    syncing = true
                                    val result = directCloudSync.sync(CloudSyncContracts.TRIGGER_MANUAL)
                                    val allowed = result.getOrNull()?.canSyncTimetable == true
                                    independentMode = WearSyncModeStore.setIndependentMode(context, allowed)
                                    status = directSyncStatusText(context, result)
                                    syncing = false
                                }
                            }
                        },
                        enabled = directSession != null && !syncing,
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
                    shape = RoundedCornerShape(ClassingWearRadii.pill),
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
                            directResult != null -> directSyncStatusText(context, directResult)
                            settingsResult.isSuccess && requestResult.isSuccess ->
                                context.getString(R.string.settings_cloud_sync_request_queued)
                            else -> context.getString(R.string.settings_cloud_sync_request_failed)
                        }
                        syncing = false
                    }
                },
                enabled = !syncing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(ClassingWearRadii.pill),
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
                shape = RoundedCornerShape(ClassingWearRadii.pill),
            ) {
                Text(stringResource(R.string.detail_back))
            }
        }
    }
    }
}

private fun directSyncStatusText(
    context: android.content.Context,
    result: Result<com.classing.wear.timetable.sync.WearOfficialCloudSyncOutcome>,
    loginCompleted: Boolean = false,
): String {
    val outcome = result.getOrNull()
    if (outcome != null) {
        return when {
            !outcome.canSyncTimetable -> context.getString(R.string.settings_cloud_sync_membership_required)
            loginCompleted -> context.getString(R.string.settings_qr_login_sync_success)
            else -> context.getString(
                R.string.settings_cloud_sync_direct_success_detail,
                outcome.appliedRemoteLessons,
            )
        }
    }
    val error = result.exceptionOrNull()
    return when {
        error is ClientSignatureException ->
            context.getString(R.string.settings_account_login_signature_error)
        error is WearOfficialCloudHttpException &&
            error.errorCode.contains("SIGNATURE", ignoreCase = true) ->
            context.getString(R.string.settings_account_login_signature_error)
        error is WearOfficialCloudLoginRequiredException ->
            context.getString(R.string.settings_cloud_sync_login_required)
        error is WearOfficialCloudHttpException -> context.getString(
            R.string.settings_cloud_sync_error_detail,
            error.errorCode.ifBlank { error.statusCode.toString() },
        )
        else -> error?.message?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.settings_cloud_sync_direct_failed)
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
