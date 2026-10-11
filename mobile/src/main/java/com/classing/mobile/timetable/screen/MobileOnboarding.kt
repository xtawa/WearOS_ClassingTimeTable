package com.xtawa.classingtime.screen

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import com.xtawa.classingtime.ui.components.ClassingAmbientBackdrop
import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.xtawa.classingtime.ui.components.ClassingCard as Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.classing.shared.sync.CloudSyncContracts
import com.xtawa.classingtime.R
import com.xtawa.classingtime.ui.components.ClassingInformationIsland
import com.xtawa.classingtime.ui.components.ClassingOobeHero
import com.xtawa.classingtime.ui.theme.ClassingBitsTransitions
import com.xtawa.classingtime.ui.theme.ClassingMotion
import com.xtawa.classingtime.ui.theme.ClassingRadii
import com.xtawa.classingtime.ui.theme.ClassingSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal enum class OnboardingImportTarget {
    NONE,
    ICS,
    JSON,
    CLOUD_SYNC,
    BACKUP_RESTORE,
    MANUAL_ENTRY,
    AI_DOCUMENT,
    AI_TEXT,
    CANVAS,
}

internal data class OnboardingCompletion(
    val importTarget: OnboardingImportTarget,
    val wearSyncMode: WearSyncMode,
    val openCloudSyncSettingsAfterFinish: Boolean,
    val cloudProvider: CloudProviderUi,
    val cloudSyncEnabled: Boolean,
    val cloudServerUrl: String,
    val cloudRemotePath: String,
    val cloudUsername: String,
    val cloudPassword: String,
    val cloudDriveFileName: String,
    val reminderEnabled: Boolean,
    val showWeekend: Boolean,
    val semesterWeekStartDate: LocalDate,
    val openSettingsHomeAfterFinish: Boolean,
    val importedData: BackupRestorePayload? = null,
    val creationElapsedMs: Long = 0,
    val creationSessionId: String = "",
)

@Composable
internal fun MobileOnboardingFlow(
    initialShowWeekend: Boolean,
    initialReminderEnabled: Boolean,
    initialSemesterWeekStartDate: LocalDate,
    initialWearSyncMode: WearSyncMode,
    initialCloudProvider: CloudProviderUi,
    initialCloudSyncEnabled: Boolean,
    initialCloudServerUrl: String,
    initialCloudRemotePath: String,
    initialCloudUsername: String,
    initialCloudPassword: String,
    initialCloudDriveFileName: String,
    onComplete: (OnboardingCompletion) -> Unit,
    onParseFile: suspend (android.net.Uri, OnboardingImportTarget) -> Result<BackupRestorePayload>,
) {
    var stagedImport by remember { mutableStateOf<BackupRestorePayload?>(null) }
    val context = LocalContext.current
    val startedAt = remember { android.os.SystemClock.elapsedRealtime() }
    val sessionId = remember { java.util.UUID.randomUUID().toString() }
    androidx.compose.runtime.LaunchedEffect(Unit) { com.xtawa.classingtime.metrics.ProductMetrics.record(context, com.xtawa.classingtime.metrics.ProductEvent.CREATION_STARTED, sessionId = sessionId) }
    var stepIndex by remember { mutableIntStateOf(0) }
    val contentScroll = rememberScrollState()
    androidx.compose.runtime.LaunchedEffect(stepIndex) { contentScroll.scrollTo(0) }
    var importTarget by remember { mutableStateOf(OnboardingImportTarget.NONE) }
    var wearSyncMode by remember {
        mutableStateOf(
            if (initialWearSyncMode == WearSyncMode.AUTO) WearSyncMode.AUTO else initialWearSyncMode,
        )
    }
    var openCloudSyncSettingsAfterFinish by remember { mutableStateOf(false) }
    var cloudProvider by remember { mutableStateOf(initialCloudProvider) }
    var cloudSyncEnabled by remember { mutableStateOf(initialCloudSyncEnabled) }
    var cloudServerUrl by remember { mutableStateOf(initialCloudServerUrl) }
    var cloudRemotePath by remember {
        mutableStateOf(initialCloudRemotePath.ifBlank { CloudSyncContracts.DEFAULT_REMOTE_PATH })
    }
    var cloudUsername by remember { mutableStateOf(initialCloudUsername) }
    var cloudPassword by remember { mutableStateOf(initialCloudPassword) }
    var cloudDriveFileName by remember {
        mutableStateOf(initialCloudDriveFileName.ifBlank { CloudSyncContracts.DEFAULT_DRIVE_FILE_NAME })
    }
    var reminderEnabled by remember { mutableStateOf(initialReminderEnabled) }
    var showWeekend by remember { mutableStateOf(initialShowWeekend) }
    var semesterWeekStartDate by remember { mutableStateOf(initialSemesterWeekStartDate) }

    val autoDetection = remember { detectWearAutoSyncPlan(findWearOsCompanionInfo(context)) }
    val stepCount = 6
    val animateOobe = ValueAnimator.areAnimatorsEnabled()
    val animatedStepProgress by animateFloatAsState(
        targetValue = (stepIndex + 1).toFloat() / stepCount,
        animationSpec = tween(360),
        label = "onboarding_step_progress",
    )
    val nextEnabled = stepIndex < stepCount - 1
    val formattedSemesterDate = remember(semesterWeekStartDate) {
        semesterWeekStartDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }

    fun complete(openSettingsHomeAfterFinish: Boolean) {
        onComplete(
            OnboardingCompletion(
                importTarget = if (stagedImport != null) OnboardingImportTarget.NONE else importTarget,
                importedData = stagedImport,
                creationElapsedMs = android.os.SystemClock.elapsedRealtime() - startedAt,
                creationSessionId = sessionId,
                wearSyncMode = wearSyncMode,
                openCloudSyncSettingsAfterFinish = openCloudSyncSettingsAfterFinish,
                cloudProvider = cloudProvider,
                cloudSyncEnabled = cloudSyncEnabled,
                cloudServerUrl = cloudServerUrl.trim(),
                cloudRemotePath = cloudRemotePath.trim(),
                cloudUsername = cloudUsername.trim(),
                cloudPassword = cloudPassword,
                cloudDriveFileName = cloudDriveFileName.trim(),
                reminderEnabled = reminderEnabled,
                showWeekend = showWeekend,
                semesterWeekStartDate = semesterWeekStartDate,
                openSettingsHomeAfterFinish = openSettingsHomeAfterFinish,
            ),
        )
    }

    BackHandler(enabled = stepIndex > 0) {
        stepIndex = previousOnboardingStep(stepIndex)
    }

    Box(Modifier.fillMaxSize()) {
    ClassingAmbientBackdrop()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Surface(color = Color.Transparent) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(
                            horizontal = ClassingSpacing.sm,
                            vertical = ClassingSpacing.xs,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (stepIndex > 0) {
                        Button(
                            onClick = { stepIndex = previousOnboardingStep(stepIndex) },
                            shape = RoundedCornerShape(ClassingRadii.pill),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            contentPadding = PaddingValues(
                                horizontal = ClassingSpacing.sm,
                                vertical = ClassingSpacing.xs,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(52.dp))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.onboarding_step_of, stepIndex + 1, stepCount),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { animatedStepProgress },
                            modifier = Modifier.width(96.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                    }
                    com.xtawa.classingtime.metrics.MetricsSettingsCard(onEnabled = {
                        com.xtawa.classingtime.metrics.ProductMetrics.record(context,
                            com.xtawa.classingtime.metrics.ProductEvent.CREATION_STARTED, sessionId = sessionId)
                    })
                }
            }
        },
        bottomBar = {
            Surface(color = Color.Transparent) {
                if (stepIndex < stepCount - 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(
                                horizontal = ClassingSpacing.md,
                                vertical = ClassingSpacing.sm,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.sm),
                    ) {
                        Button(
                            onClick = { stepIndex = (stepIndex + 1).coerceAtMost(stepCount - 1) },
                            shape = RoundedCornerShape(ClassingRadii.pill),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.onboarding_skip))
                        }
                        Button(
                            onClick = {
                                if (nextEnabled) stepIndex += 1
                            },
                            shape = RoundedCornerShape(ClassingRadii.pill),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.onboarding_next))
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(
                                horizontal = ClassingSpacing.md,
                                vertical = ClassingSpacing.sm,
                            ),
                        verticalArrangement = Arrangement.spacedBy(ClassingSpacing.sm),
                    ) {
                        Button(
                            onClick = { complete(openSettingsHomeAfterFinish = false) },
                            shape = RoundedCornerShape(ClassingRadii.pill),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.onboarding_go_dashboard))
                        }
                        Button(
                            onClick = { complete(openSettingsHomeAfterFinish = true) },
                            shape = RoundedCornerShape(ClassingRadii.pill),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.onboarding_view_settings))
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(contentScroll)
                .padding(horizontal = ClassingSpacing.referenceScreenInset)
                .padding(top = ClassingSpacing.xs, bottom = ClassingSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(ClassingSpacing.sm),
        ) {
            AnimatedContent(
                targetState = stepIndex,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    ClassingBitsTransitions.horizontal(
                        direction = if (targetState > initialState) 1 else -1,
                        enabled = animateOobe,
                    )
                },
                label = "oobe_page_motion",
            ) { visibleStep ->
            Column(verticalArrangement = Arrangement.spacedBy(ClassingSpacing.sm)) {
            when (visibleStep) {
                0 -> {
                    Spacer(modifier = Modifier.height(20.dp))
                    ClassingOobeHero(completed = false)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.onboarding_welcome_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_welcome_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                1 -> {
                    Text(
                        text = stringResource(R.string.onboarding_import_configure_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_import_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OnboardingOptionCard(
                        title = stringResource(R.string.onboarding_import_option_ics),
                        desc = stringResource(R.string.onboarding_import_option_ics_desc),
                        selected = importTarget == OnboardingImportTarget.ICS,
                        icon = Icons.Filled.Event,
                        onClick = { importTarget = OnboardingImportTarget.ICS },
                    )
                    OnboardingOptionCard(
                        title = stringResource(R.string.ai_import_document),
                        desc = stringResource(R.string.ai_import_notice),
                        selected = importTarget == OnboardingImportTarget.AI_DOCUMENT,
                        icon = Icons.Filled.Event,
                        onClick = { importTarget = OnboardingImportTarget.AI_DOCUMENT },
                    )
                    OnboardingOptionCard(title = stringResource(R.string.ai_text_title), desc = stringResource(R.string.ai_text_notice),
                        selected = importTarget == OnboardingImportTarget.AI_TEXT, icon = Icons.Filled.Edit, onClick = { importTarget = OnboardingImportTarget.AI_TEXT })
                    OnboardingOptionCard(title = stringResource(R.string.lms_title), desc = stringResource(R.string.lms_notice),
                        selected = importTarget == OnboardingImportTarget.CANVAS, icon = Icons.Filled.CloudSync, onClick = { importTarget = OnboardingImportTarget.CANVAS })
                    OnboardingOptionCard(
                        title = stringResource(R.string.onboarding_import_option_json),
                        desc = stringResource(R.string.onboarding_import_option_json_desc),
                        selected = importTarget == OnboardingImportTarget.JSON,
                        icon = Icons.Filled.DataObject,
                        onClick = { importTarget = OnboardingImportTarget.JSON },
                    )
                    OnboardingOptionCard(
                        title = stringResource(R.string.onboarding_import_option_cloud),
                        desc = stringResource(R.string.onboarding_import_option_cloud_desc),
                        selected = importTarget == OnboardingImportTarget.CLOUD_SYNC,
                        icon = Icons.Filled.CloudSync,
                        onClick = { importTarget = OnboardingImportTarget.CLOUD_SYNC },
                    )
                    OnboardingOptionCard(
                        title = stringResource(R.string.onboarding_import_option_backup),
                        desc = stringResource(R.string.onboarding_import_option_backup_desc),
                        selected = importTarget == OnboardingImportTarget.BACKUP_RESTORE,
                        icon = Icons.Filled.SettingsBackupRestore,
                        onClick = { importTarget = OnboardingImportTarget.BACKUP_RESTORE },
                    )
                    OnboardingOptionCard(
                        title = stringResource(R.string.onboarding_import_option_manual),
                        desc = stringResource(R.string.onboarding_import_option_manual_desc),
                        selected = importTarget == OnboardingImportTarget.MANUAL_ENTRY,
                        icon = Icons.Filled.Edit,
                        onClick = { importTarget = OnboardingImportTarget.MANUAL_ENTRY },
                    )
                    OnboardingOptionCard(
                        title = stringResource(R.string.onboarding_import_option_later),
                        desc = stringResource(R.string.onboarding_import_option_later_desc),
                        selected = importTarget == OnboardingImportTarget.NONE,
                        icon = Icons.Filled.CheckCircle,
                        onClick = { importTarget = OnboardingImportTarget.NONE },
                    )
                }

                2 -> {
                    Text(
                        text = stringResource(R.string.onboarding_import_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_import_configure_subtitle) + "\n" + when (importTarget) {
                            OnboardingImportTarget.CLOUD_SYNC -> stringResource(R.string.onboarding_import_option_cloud_desc)
                            OnboardingImportTarget.AI_DOCUMENT -> stringResource(R.string.ai_import_notice)
                            OnboardingImportTarget.ICS -> stringResource(R.string.onboarding_import_option_ics_desc)
                            OnboardingImportTarget.JSON -> stringResource(R.string.onboarding_import_option_json_desc)
                            OnboardingImportTarget.BACKUP_RESTORE -> stringResource(R.string.onboarding_import_option_backup_desc)
                            OnboardingImportTarget.MANUAL_ENTRY -> stringResource(R.string.onboarding_import_option_manual_desc)
                            OnboardingImportTarget.AI_TEXT -> stringResource(R.string.ai_text_notice)
                            OnboardingImportTarget.CANVAS -> stringResource(R.string.lms_notice)
                            OnboardingImportTarget.NONE -> stringResource(R.string.onboarding_import_option_later_desc)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ClassingInformationIsland(
                        contentPadding = PaddingValues(ClassingSpacing.md),
                    ) {
                            when (importTarget) {
                                OnboardingImportTarget.CLOUD_SYNC -> {
                                    Text(
                                        text = stringResource(R.string.settings_cloud_sync_title),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_cloud_sync_enable_title),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Switch(
                                            checked = cloudSyncEnabled,
                                            onCheckedChange = { cloudSyncEnabled = it },
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.xs)) {
                                        FilterChip(
                                            selected = cloudProvider == CloudProviderUi.WEBDAV,
                                            onClick = { cloudProvider = CloudProviderUi.WEBDAV },
                    label = { Text(stringResource(R.string.cloud_provider_webdav)) },
                                        )
                                        FilterChip(
                                            selected = cloudProvider == CloudProviderUi.GOOGLE_DRIVE,
                                            onClick = { cloudProvider = CloudProviderUi.GOOGLE_DRIVE },
                    label = { Text(stringResource(R.string.cloud_provider_google_drive)) },
                                        )
                                        FilterChip(
                                            selected = cloudProvider == CloudProviderUi.OFFICIAL,
                                            onClick = { cloudProvider = CloudProviderUi.OFFICIAL },
                    label = { Text(stringResource(R.string.cloud_provider_official)) },
                                        )
                                    }
                                    if (cloudProvider == CloudProviderUi.WEBDAV) {
                                        OutlinedTextField(
                                            value = cloudServerUrl,
                                            onValueChange = { cloudServerUrl = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text(stringResource(R.string.settings_cloud_sync_server_url)) },
                                            singleLine = true,
                                        )
                                        OutlinedTextField(
                                            value = cloudRemotePath,
                                            onValueChange = { cloudRemotePath = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text(stringResource(R.string.settings_cloud_sync_remote_path)) },
                                            singleLine = true,
                                        )
                                        OutlinedTextField(
                                            value = cloudUsername,
                                            onValueChange = { cloudUsername = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text(stringResource(R.string.settings_cloud_sync_username)) },
                                            singleLine = true,
                                        )
                                        OutlinedTextField(
                                            value = cloudPassword,
                                            onValueChange = { cloudPassword = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text(stringResource(R.string.settings_cloud_sync_password)) },
                                            singleLine = true,
                                        )
                                    } else if (cloudProvider == CloudProviderUi.GOOGLE_DRIVE) {
                                        OutlinedTextField(
                                            value = cloudDriveFileName,
                                            onValueChange = { cloudDriveFileName = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text(stringResource(R.string.settings_cloud_sync_drive_file_name)) },
                                            singleLine = true,
                                        )
                                    }
                                }

                                OnboardingImportTarget.NONE -> {
                                    Text(
                                        text = stringResource(R.string.onboarding_import_option_later_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                else -> {
                            OnboardingImportEditor(importTarget, stagedImport, { stagedImport = it; it.semesterWeekStartDate?.let { date -> semesterWeekStartDate = date } }, onParseFile, sessionId, semesterWeekStartDate)
                                }
                            }
                    }
                }

                3 -> {
                    Text(
                        text = stringResource(R.string.onboarding_device_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_device_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ClassingInformationIsland(
                        contentPadding = PaddingValues(ClassingSpacing.md),
                    ) {
                            Text(
                                text = stringResource(R.string.onboarding_device_detecting),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.xs)) {
                                FilterChip(
                                    selected = wearSyncMode == WearSyncMode.AUTO,
                                    onClick = { wearSyncMode = WearSyncMode.AUTO },
                                    label = { Text(stringResource(R.string.settings_wear_sync_mode_auto)) },
                                )
                                FilterChip(
                                    selected = wearSyncMode == WearSyncMode.WEARABLE_API,
                                    onClick = { wearSyncMode = WearSyncMode.WEARABLE_API },
                                    label = { Text(stringResource(R.string.settings_wear_sync_mode_wearable_api)) },
                                )
                            }
                    }
                    ClassingInformationIsland(
                        contentPadding = PaddingValues(ClassingSpacing.md),
                    ) {
                            val variant = wearAutoVariantLabel(context, autoDetection.variant)
                            val effectiveMode = wearSyncModeLabel(context, autoDetection.effectiveMode)
                            Text(
                                text = stringResource(R.string.settings_wear_auto_detected_label, variant),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = if (wearSyncMode == WearSyncMode.AUTO) {
                                    stringResource(R.string.settings_wear_auto_effective_label, effectiveMode)
                                } else {
                                    stringResource(
                                        R.string.settings_wear_auto_manual_selected_label,
                                        wearSyncModeLabel(context, wearSyncMode),
                                    )
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (autoDetection.variant == WearAutoVariant.UNKNOWN) {
                                Text(
                                    text = stringResource(R.string.settings_wear_auto_unknown_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.xs),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Watch,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = autoDetection.companionInfo?.toDisplayLabel()
                                        ?: stringResource(R.string.wearos_app_unavailable),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                    }
                }

                4 -> {
                    Text(
                        text = stringResource(R.string.onboarding_personalize_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_personalize_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ClassingInformationIsland(
                        contentPadding = PaddingValues(ClassingSpacing.md),
                    ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.onboarding_personalize_reminder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Switch(
                                    checked = reminderEnabled,
                                    onCheckedChange = { reminderEnabled = it },
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.onboarding_personalize_weekend),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Switch(
                                    checked = showWeekend,
                                    onCheckedChange = { showWeekend = it },
                                )
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        DatePickerDialog(
                                            context,
                                            { _, year, month, day ->
                                                semesterWeekStartDate = LocalDate.of(year, month + 1, day)
                                            },
                                            semesterWeekStartDate.year,
                                            semesterWeekStartDate.monthValue - 1,
                                            semesterWeekStartDate.dayOfMonth,
                                        ).show()
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            ) {
                                Column(modifier = Modifier.padding(ClassingSpacing.sm)) {
                                    Text(
                                        text = stringResource(R.string.onboarding_personalize_semester),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = formattedSemesterDate,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                    }
                }

                else -> {
                    Spacer(modifier = Modifier.height(36.dp))
                    ClassingOobeHero(completed = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.onboarding_complete_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_complete_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                    )
                }
            }
            }
            }
        }
    }
}

}

internal fun previousOnboardingStep(stepIndex: Int): Int = (stepIndex - 1).coerceAtLeast(0)

@Composable
private fun OnboardingOptionCard(
    title: String,
    desc: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val selectedColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .16f)
        else MaterialTheme.colorScheme.surface.copy(alpha = .90f),
        animationSpec = tween(if (ValueAnimator.areAnimatorsEnabled()) ClassingMotion.ContentReveal else 0),
        label = "oobe_option_background",
    )
    val selectedScale by animateFloatAsState(
        targetValue = if (selected) 1f else .985f,
        animationSpec = if (ValueAnimator.areAnimatorsEnabled()) ClassingMotion.responsiveSpring()
            else tween(0),
        label = "oobe_option_selection",
    )
    ClassingInformationIsland(
        modifier = Modifier.graphicsLayer { scaleX = selectedScale; scaleY = selectedScale }
            .semantics { this.selected = selected },
        onClick = onClick,
        containerColor = selectedColor,
        contentPadding = PaddingValues(ClassingSpacing.md),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = ClassingSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.sm),
        ) {
            Surface(
                shape = RoundedCornerShape(ClassingRadii.pill),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(ClassingSpacing.minimumTouchTarget),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(ClassingSpacing.lg),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
