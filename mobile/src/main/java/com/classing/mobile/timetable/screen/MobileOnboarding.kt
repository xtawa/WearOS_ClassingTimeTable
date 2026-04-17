package com.xtawa.classingtime.screen

import android.app.DatePickerDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.EditDocument
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xtawa.classingtime.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal enum class OnboardingImportTarget {
    NONE,
    ICS,
    JSON,
    CLOUD_SYNC,
    BACKUP_RESTORE,
    MANUAL_ENTRY,
}

internal data class OnboardingCompletion(
    val importTarget: OnboardingImportTarget,
    val wearSyncMode: WearSyncMode,
    val openCloudSyncSettingsAfterFinish: Boolean,
    val reminderEnabled: Boolean,
    val showWeekend: Boolean,
    val semesterWeekStartDate: LocalDate,
    val openSettingsHomeAfterFinish: Boolean,
)

@Composable
internal fun MobileOnboardingFlow(
    initialShowWeekend: Boolean,
    initialReminderEnabled: Boolean,
    initialSemesterWeekStartDate: LocalDate,
    initialWearSyncMode: WearSyncMode,
    onComplete: (OnboardingCompletion) -> Unit,
) {
    val context = LocalContext.current
    var stepIndex by remember { mutableIntStateOf(0) }
    var importTarget by remember { mutableStateOf(OnboardingImportTarget.NONE) }
    var wearSyncMode by remember {
        mutableStateOf(
            if (initialWearSyncMode == WearSyncMode.AUTO) WearSyncMode.AUTO else initialWearSyncMode,
        )
    }
    var openCloudSyncSettingsAfterFinish by remember { mutableStateOf(false) }
    var reminderEnabled by remember { mutableStateOf(initialReminderEnabled) }
    var showWeekend by remember { mutableStateOf(initialShowWeekend) }
    var semesterWeekStartDate by remember { mutableStateOf(initialSemesterWeekStartDate) }

    val autoDetection = remember { detectWearAutoSyncPlan(findWearOsCompanionInfo(context)) }
    val stepCount = 6
    val nextEnabled = stepIndex < stepCount - 1
    val formattedSemesterDate = remember(semesterWeekStartDate) {
        semesterWeekStartDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }

    fun complete(openSettingsHomeAfterFinish: Boolean) {
        onComplete(
            OnboardingCompletion(
                importTarget = importTarget,
                wearSyncMode = wearSyncMode,
                openCloudSyncSettingsAfterFinish = openCloudSyncSettingsAfterFinish,
                reminderEnabled = reminderEnabled,
                showWeekend = showWeekend,
                semesterWeekStartDate = semesterWeekStartDate,
                openSettingsHomeAfterFinish = openSettingsHomeAfterFinish,
            ),
        )
    }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (stepIndex > 0) {
                        Button(
                            onClick = { stepIndex = (stepIndex - 1).coerceAtLeast(0) },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(52.dp))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.onboarding_step_of, stepIndex + 1, stepCount),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                if (stepIndex < stepCount - 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = { stepIndex = (stepIndex + 1).coerceAtMost(stepCount - 1) },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.onboarding_skip))
                        }
                        Button(
                            onClick = { if (nextEnabled) stepIndex += 1 },
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.onboarding_next))
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Filled.ArrowForward,
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
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = { complete(openSettingsHomeAfterFinish = false) },
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.onboarding_go_dashboard))
                        }
                        Button(
                            onClick = { complete(openSettingsHomeAfterFinish = true) },
                            shape = RoundedCornerShape(999.dp),
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
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (stepIndex) {
                0 -> {
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                shape = RoundedCornerShape(24.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                        )
                    }
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
                        text = stringResource(R.string.onboarding_import_title),
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
                        icon = Icons.Filled.EditDocument,
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
                        text = stringResource(R.string.onboarding_device_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_device_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_device_detecting),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                FilterChip(
                                    selected = wearSyncMode == WearSyncMode.WEAROS_APP,
                                    onClick = { wearSyncMode = WearSyncMode.WEAROS_APP },
                                    label = { Text(stringResource(R.string.settings_wear_sync_mode_wearos_app)) },
                                )
                            }
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                }

                3 -> {
                    Text(
                        text = stringResource(R.string.onboarding_cloud_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_cloud_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.onboarding_cloud_open_webdav),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = stringResource(R.string.onboarding_cloud_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = openCloudSyncSettingsAfterFinish,
                                    onCheckedChange = { openCloudSyncSettingsAfterFinish = it },
                                )
                            }
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
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                                Column(modifier = Modifier.padding(12.dp)) {
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
                }

                else -> {
                    Spacer(modifier = Modifier.height(36.dp))
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(999.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(54.dp),
                        )
                    }
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

@Composable
private fun OnboardingOptionCard(
    title: String,
    desc: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
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
