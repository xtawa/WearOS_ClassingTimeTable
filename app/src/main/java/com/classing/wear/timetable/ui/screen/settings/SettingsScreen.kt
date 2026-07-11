package com.classing.wear.timetable.ui.screen.settings

import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.widget.ConfirmationOverlay
import com.classing.wear.timetable.R
import com.classing.wear.timetable.domain.model.KeepAliveLevel
import com.classing.wear.timetable.domain.repository.UserPreferences
import com.classing.wear.timetable.ui.component.LoadingState
import com.classing.wear.timetable.ui.component.screenPadding
import com.classing.wear.timetable.ui.state.SettingsUiState
import com.classing.wear.timetable.ui.state.SyncFeedback
import com.classing.wear.timetable.ui.theme.ClassingTimetableTheme
import org.json.JSONObject

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onToggleDynamicColor: (Boolean) -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    onToggleWeekend: (Boolean) -> Unit,
    onToggleShowCompletedToday: (Boolean) -> Unit,
    onToggleTileShowTeacher: (Boolean) -> Unit,
    onToggleTileShowLocation: (Boolean) -> Unit,
    onToggleTileShowCountdown: (Boolean) -> Unit,
    onToggleTileShowCourseName: (Boolean) -> Unit,
    onToggleTileShowCurrentWeek: (Boolean) -> Unit,
    onToggleTileShowTimeRange: (Boolean) -> Unit,
    onSetKeepAliveLevel: (KeepAliveLevel) -> Unit,
    onForceFullSync: () -> Unit,
    onConsumeSyncFeedback: () -> Unit,
    onOpenCloudSync: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val syncSuccessText = stringResource(R.string.settings_sync_feedback_success)
    val syncCheckPhoneText = stringResource(R.string.settings_sync_feedback_check_phone)
    val cloudSnapshot = remember(context) {
        loadWearCloudSummary(context)
    }

    LaunchedEffect(state.syncFeedback) {
        val feedback = state.syncFeedback ?: return@LaunchedEffect
        showSyncFeedbackOverlay(
            context = context,
            feedback = feedback,
            successText = syncSuccessText,
            checkPhoneText = syncCheckPhoneText,
        )
        onConsumeSyncFeedback()
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (state.isLoading) {
            item { LoadingState(message = stringResource(R.string.common_loading)) }
            return@ScalingLazyColumn
        }

        item { SettingsSectionTag(title = stringResource(R.string.settings_section_preference)) }
        item {
            PreferenceSwitchCard(
                title = stringResource(R.string.settings_dynamic_color),
                checked = state.preferences.dynamicColor,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleDynamicColor(it)
                },
            )
        }
        item {
            PreferenceSwitchCard(
                title = stringResource(R.string.settings_reminder),
                checked = state.preferences.remindersEnabled,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleReminder(it)
                },
            )
        }
        item {
            PreferenceSwitchCard(
                title = stringResource(R.string.settings_auto_sync),
                checked = state.preferences.autoSync,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleAutoSync(it)
                },
            )
        }
        item {
            PreferenceSwitchCard(
                title = stringResource(R.string.settings_show_weekend),
                checked = state.preferences.showWeekend,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleWeekend(it)
                },
            )
        }
        item {
            PreferenceSwitchCard(
                title = stringResource(R.string.settings_show_completed_today),
                checked = state.preferences.showCompletedToday,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleShowCompletedToday(it)
                },
            )
        }

        item { SettingsSectionTag(title = stringResource(R.string.settings_section_keep_alive)) }
        item {
            KeepAliveLevelCard(
                level = state.preferences.keepAliveLevel,
                onSetKeepAliveLevel = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSetKeepAliveLevel(it)
                },
            )
        }
        item {
            KeepAliveStatusCard(
                context = context,
                onOpenBatteryOptimizationSettings = {
                    val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(requestIntent) }
                        .onFailure { context.startActivity(fallback) }
                },
                onOpenExactAlarmSettings = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }
                    }
                },
            )
        }

        item { SettingsSectionTag(title = stringResource(R.string.settings_section_tile)) }
        item {
            PreferenceSwitchCard(
                title = stringResource(R.string.settings_tile_show_course_name),
                checked = state.preferences.tileShowCourseName,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleTileShowCourseName(it)
                },
            )
        }
        item {
            PreferenceSwitchCard(
                title = stringResource(R.string.settings_tile_show_current_week),
                checked = state.preferences.tileShowCurrentWeek,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleTileShowCurrentWeek(it)
                },
            )
        }
        item {
            PreferenceSwitchCard(
                title = stringResource(R.string.settings_tile_show_time_range),
                checked = state.preferences.tileShowTimeRange,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleTileShowTimeRange(it)
                },
            )
        }
        item {
            PreferenceSwitchCard(
                title = stringResource(R.string.settings_tile_show_teacher),
                checked = state.preferences.tileShowTeacher,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleTileShowTeacher(it)
                },
            )
        }
        item {
            PreferenceSwitchCard(
                title = stringResource(R.string.settings_tile_show_location),
                checked = state.preferences.tileShowLocation,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleTileShowLocation(it)
                },
            )
        }
        item {
            PreferenceSwitchCard(
                title = stringResource(R.string.settings_tile_show_countdown),
                checked = state.preferences.tileShowCountdown,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleTileShowCountdown(it)
                },
            )
        }

        item {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onForceFullSync()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(stringResource(R.string.settings_force_full_sync))
            }
        }
        item {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenCloudSync()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(stringResource(R.string.settings_cloud_sync_title))
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(R.string.settings_phone_account), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(
                            R.string.settings_phone_account_summary,
                            if (cloudSnapshot.loggedIn) stringResource(R.string.common_yes) else stringResource(R.string.common_no),
                            if (cloudSnapshot.isMember) cloudSnapshot.membershipTier else "FREE",
                            cloudSnapshot.provider.ifBlank { "-" },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenAbout()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(stringResource(R.string.settings_about))
            }
        }
    }
}

private data class WearCloudSummary(
    val loggedIn: Boolean = false,
    val isMember: Boolean = false,
    val membershipTier: String = "FREE",
    val provider: String = "",
)

private fun loadWearCloudSummary(context: Context): WearCloudSummary {
    val prefs = context.getSharedPreferences("wear_mobile_sync", Context.MODE_PRIVATE)
    val json = runCatching {
        JSONObject(prefs.getString("last_phone_cloud_snapshot", "").orEmpty())
    }.getOrNull() ?: return WearCloudSummary()
    return WearCloudSummary(
        loggedIn = json.optBoolean("loggedIn", false),
        isMember = json.optBoolean("isMember", false),
        membershipTier = json.optString("membershipTier", "FREE"),
        provider = json.optString("cloudProvider"),
    )
}

@Composable
private fun SettingsSectionTag(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun PreferenceSwitchCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun KeepAliveLevelCard(
    level: KeepAliveLevel,
    onSetKeepAliveLevel: (KeepAliveLevel) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = stringResource(R.string.settings_keepalive_level), style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf(
                    KeepAliveLevel.ECO to stringResource(R.string.settings_keepalive_eco),
                    KeepAliveLevel.BALANCED to stringResource(R.string.settings_keepalive_balanced),
                    KeepAliveLevel.AGGRESSIVE to stringResource(R.string.settings_keepalive_aggressive),
                ).forEach { (itemLevel, label) ->
                    Button(
                        onClick = { onSetKeepAliveLevel(itemLevel) },
                        shape = RoundedCornerShape(999.dp),
                        colors = if (itemLevel == level) {
                            ButtonDefaults.buttonColors()
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            )
                        },
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}

@Composable
private fun KeepAliveStatusCard(
    context: Context,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
) {
    val status = rememberKeepAliveStatus(context)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.settings_exact_alarm_status,
                    if (status.canScheduleExactAlarm) stringResource(R.string.settings_authorized) else stringResource(R.string.settings_not_authorized),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(
                    R.string.settings_battery_optimization_status,
                    if (status.ignoringBatteryOptimizations) stringResource(R.string.settings_enabled) else stringResource(R.string.settings_disabled),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Button(
                    onClick = onOpenBatteryOptimizationSettings,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) { Text(stringResource(R.string.settings_battery_short)) }
                Button(
                    onClick = onOpenExactAlarmSettings,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) { Text(stringResource(R.string.settings_alarm_short)) }
            }
        }
    }
}

@Composable
private fun rememberKeepAliveStatus(context: Context): KeepAliveStatus {
    return remember(context) { loadKeepAliveStatus(context) }
}

private fun loadKeepAliveStatus(context: Context): KeepAliveStatus {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager.canScheduleExactAlarms()
    } else {
        true
    }
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val ignoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    return KeepAliveStatus(
        canScheduleExactAlarm = canExact,
        ignoringBatteryOptimizations = ignoringBattery,
    )
}

private data class KeepAliveStatus(
    val canScheduleExactAlarm: Boolean,
    val ignoringBatteryOptimizations: Boolean,
)

@Preview(showBackground = true, widthDp = 220, heightDp = 220)
@Composable
private fun SettingsScreenPreview() {
    ClassingTimetableTheme(useDynamicColor = false) {
        SettingsScreen(
            state = SettingsUiState(
                isLoading = false,
                preferences = UserPreferences(),
                syncMessage = "Never synced",
            ),
            onToggleDynamicColor = {},
            onToggleReminder = {},
            onToggleAutoSync = {},
            onToggleWeekend = {},
            onToggleShowCompletedToday = {},
            onToggleTileShowTeacher = {},
            onToggleTileShowLocation = {},
            onToggleTileShowCountdown = {},
            onToggleTileShowCourseName = {},
            onToggleTileShowCurrentWeek = {},
            onToggleTileShowTimeRange = {},
            onSetKeepAliveLevel = {},
            onForceFullSync = {},
            onConsumeSyncFeedback = {},
            onOpenCloudSync = {},
            onOpenAbout = {},
        )
    }
}

private fun showSyncFeedbackOverlay(
    context: Context,
    feedback: SyncFeedback,
    successText: String,
    checkPhoneText: String,
) {
    val activity = context.findActivity() ?: return
    val overlay = ConfirmationOverlay()
    when (feedback) {
        SyncFeedback.SUCCESS -> {
            overlay
                .setType(ConfirmationOverlay.SUCCESS_ANIMATION)
                .setMessage(successText)
        }

        SyncFeedback.CHECK_PHONE_CONNECTION -> {
            overlay
                .setType(ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION)
                .setMessage(checkPhoneText)
        }
    }
    overlay.showOn(activity)
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
