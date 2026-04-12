package com.classing.wear.timetable.ui.screen.settings

import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.classing.wear.timetable.R
import com.classing.wear.timetable.domain.model.KeepAliveLevel
import com.classing.wear.timetable.domain.repository.UserPreferences
import com.classing.wear.timetable.ui.component.LoadingState
import com.classing.wear.timetable.ui.component.screenPadding
import com.classing.wear.timetable.ui.state.SettingsUiState
import com.classing.wear.timetable.ui.theme.ClassingTimetableTheme
import android.net.Uri

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
    onToggleExperimentalAccessibilityKeepAlive: (Boolean) -> Unit,
    onForceFullSync: () -> Unit,
    onOpenCloudSync: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

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
        item { SettingsSectionTag(title = "保活") }
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
            PreferenceSwitchCard(
                title = "实验无障碍保活",
                checked = state.preferences.experimentalAccessibilityKeepAliveEnabled,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleExperimentalAccessibilityKeepAlive(it)
                },
            )
        }
        item {
            KeepAliveStatusCard(
                context = context,
                onOpenAccessibilitySettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
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
    }
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
            Text(text = "提醒保活强度", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf(
                    KeepAliveLevel.ECO to "省电",
                    KeepAliveLevel.BALANCED to "均衡",
                    KeepAliveLevel.AGGRESSIVE to "增强",
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
    onOpenAccessibilitySettings: () -> Unit,
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
                text = "精确闹钟: ${if (status.canScheduleExactAlarm) "已授权" else "未授权"}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "电池优化白名单: ${if (status.ignoringBatteryOptimizations) "已加入" else "未加入"}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "无障碍服务: ${if (status.accessibilityEnabled) "已启用" else "未启用"}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Button(
                    onClick = onOpenAccessibilitySettings,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) { Text("无障碍") }
                Button(
                    onClick = onOpenBatteryOptimizationSettings,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) { Text("电池") }
                Button(
                    onClick = onOpenExactAlarmSettings,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) { Text("闹钟") }
            }
        }
    }
}

@Composable
private fun rememberKeepAliveStatus(context: Context): KeepAliveStatus {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager.canScheduleExactAlarms()
    } else {
        true
    }
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val ignoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    val serviceName = ComponentName(
        context.packageName,
        "com.classing.wear.timetable.accessibility.KeepAliveAccessibilityService",
    ).flattenToString()
    val accessibilityEnabled = enabledServices.split(':').any { it.equals(serviceName, ignoreCase = true) }
    return KeepAliveStatus(
        canScheduleExactAlarm = canExact,
        ignoringBatteryOptimizations = ignoringBattery,
        accessibilityEnabled = accessibilityEnabled,
    )
}

private data class KeepAliveStatus(
    val canScheduleExactAlarm: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val accessibilityEnabled: Boolean,
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
            onToggleExperimentalAccessibilityKeepAlive = {},
            onForceFullSync = {},
            onOpenCloudSync = {},
        )
    }
}
