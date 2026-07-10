package com.xtawa.classingtime.screen

import android.app.DatePickerDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.classing.shared.sync.SyncChangeLogEntry
import com.xtawa.classingtime.R
import com.xtawa.classingtime.data.AccountSummary
import com.xtawa.classingtime.data.DailyBriefingChannel
import com.xtawa.classingtime.data.MembershipSummary
import com.xtawa.classingtime.data.OfficialSyncFrequency
import com.xtawa.classingtime.data.SyncScope
import com.xtawa.classingtime.reminder.KeepAliveLevel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SettingsLayer(
    contentPadding: PaddingValues,
    showWeekend: Boolean,
    onOpenAccountPage: () -> Unit,
    onOpenDailyBriefingPage: () -> Unit,
    onOpenImportPage: () -> Unit,
    onOpenBackupRestorePage: () -> Unit,
    onOpenWeekModePage: () -> Unit,
    onOpenReminderKeepAlivePage: () -> Unit,
    onOpenSyncCommunicationPage: () -> Unit,
    onOpenAboutPage: () -> Unit,
    onToggleWeekend: (Boolean) -> Unit,
    onClearAllSchedules: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .padding(vertical = 8.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.ghost_title_settings),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }

        SettingsSwitchCard(
            icon = Icons.Filled.CalendarMonth,
            title = stringResource(R.string.settings_show_weekend_title),
            desc = stringResource(R.string.settings_show_weekend_desc),
            checked = showWeekend,
            onCheckedChange = onToggleWeekend,
        )

        SettingsEntryCard(
            icon = Icons.Filled.CheckCircle,
            title = stringResource(R.string.settings_reminder_keepalive_title),
            desc = stringResource(R.string.settings_reminder_keepalive_desc),
            onClick = onOpenReminderKeepAlivePage,
        )

        SettingsEntryCard(
            icon = Icons.Filled.Person,
            title = "Account",
            desc = "Login, register, redeem membership code, and reset password.",
            onClick = onOpenAccountPage,
        )

        SettingsEntryCard(
            icon = Icons.Filled.MailOutline,
            title = "Daily Briefing",
            desc = "Configure app notifications and email briefing schedule.",
            onClick = onOpenDailyBriefingPage,
        )

        SettingsEntryCard(
            icon = Icons.Filled.DataObject,
            title = stringResource(R.string.settings_import_entry_title),
            desc = stringResource(R.string.settings_import_entry_desc),
            onClick = onOpenImportPage,
        )

        SettingsEntryCard(
            icon = Icons.Filled.SettingsBackupRestore,
            title = stringResource(R.string.settings_backup_title),
            desc = stringResource(R.string.settings_backup_desc),
            onClick = onOpenBackupRestorePage,
        )

        SettingsEntryCard(
            icon = Icons.Filled.CalendarMonth,
            title = stringResource(R.string.settings_week_mode_title),
            desc = stringResource(R.string.settings_week_mode_desc),
            onClick = onOpenWeekModePage,
        )

        SettingsEntryCard(
            icon = Icons.Filled.Sync,
            title = stringResource(R.string.settings_sync_comm_title),
            desc = stringResource(R.string.settings_sync_comm_desc),
            onClick = onOpenSyncCommunicationPage,
        )

        SettingsEntryCard(
            icon = Icons.Filled.HelpOutline,
            title = stringResource(R.string.settings_about_entry_title),
            desc = stringResource(R.string.settings_about_entry_desc),
            onClick = onOpenAboutPage,
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.settings_danger_title), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_danger_desc), style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = onClearAllSchedules,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.settings_danger_clear_button))
                }
            }
        }
    }
}

@Composable
internal fun SecondaryPageHeader(
    title: String,
    onBack: () -> Unit,
    backLabel: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onBack,
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = backLabel,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
    }
}

@Composable
internal fun BackupRestoreSettingsPage(
    contentPadding: PaddingValues,
    snapshots: List<ScheduleStateSnapshot>,
    onBack: () -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onUndoLatest: () -> Unit,
    onRestoreSnapshot: (String) -> Unit,
) {
    val latestSnapshot = snapshots.maxByOrNull { it.createdAt }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SecondaryPageHeader(
            title = stringResource(R.string.settings_backup_title),
            onBack = onBack,
            backLabel = stringResource(R.string.settings_about_back_button),
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.ghost_title_backup),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = stringResource(R.string.settings_backup_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.settings_backup_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onExportBackup,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(stringResource(R.string.settings_backup_button))
                    }
                    Button(
                        onClick = onRestoreBackup,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text(stringResource(R.string.settings_restore_button))
                    }
                }
                Text(
                    text = stringResource(R.string.settings_restore_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_snapshot_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = latestSnapshot?.let {
                        val createdAt = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it.createdAt), java.time.ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
                        stringResource(R.string.settings_snapshot_latest, createdAt, it.reason)
                    } ?: stringResource(R.string.settings_snapshot_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onUndoLatest,
                    enabled = latestSnapshot != null,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(stringResource(R.string.settings_snapshot_undo_button))
                }
                if (snapshots.isNotEmpty()) {
                    snapshots.sortedByDescending { it.createdAt }.take(5).forEach { snapshot ->
                        val createdAt = LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(snapshot.createdAt),
                            java.time.ZoneId.systemDefault(),
                        ).format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = snapshot.reason,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = createdAt,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { onRestoreSnapshot(snapshot.id) }) {
                                    Text(stringResource(R.string.settings_snapshot_restore_button))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WeekModeSettingsPage(
    contentPadding: PaddingValues,
    weekNumberMode: WeekNumberMode,
    semesterWeekStartDate: LocalDate,
    weekStartDay: DayOfWeek,
    onBack: () -> Unit,
    onWeekNumberModeChange: (WeekNumberMode) -> Unit,
    onWeekStartDayChange: (DayOfWeek) -> Unit,
    onSemesterWeekStartDateChange: (LocalDate) -> Unit,
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val currentSemesterWeek = (ChronoUnit.DAYS.between(semesterWeekStartDate, today) / 7L + 1L)
        .toInt()
        .coerceAtLeast(1)
    val localeWeekFields = WeekFields.of(Locale.getDefault())
    val currentNaturalWeek = today.get(
        WeekFields.of(weekStartDay, localeWeekFields.minimalDaysInFirstWeek).weekOfWeekBasedYear(),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SecondaryPageHeader(
            title = stringResource(R.string.settings_week_mode_title),
            onBack = onBack,
            backLabel = stringResource(R.string.settings_about_back_button),
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.ghost_title_week),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = stringResource(R.string.settings_week_mode_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.settings_week_mode_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = weekNumberMode == WeekNumberMode.NATURAL,
                onClick = { onWeekNumberModeChange(WeekNumberMode.NATURAL) },
                label = { Text(stringResource(R.string.settings_week_mode_natural)) },
            )
            FilterChip(
                selected = weekNumberMode == WeekNumberMode.SEMESTER,
                onClick = { onWeekNumberModeChange(WeekNumberMode.SEMESTER) },
                label = { Text(stringResource(R.string.settings_week_mode_semester)) },
            )
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_week_start_day_title),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY).forEach { day ->
                        FilterChip(
                            selected = weekStartDay == day,
                            onClick = { onWeekStartDayChange(day) },
                            label = { Text(dayLabel(day, context)) },
                        )
                    }
                }
            }
        }

        if (weekNumberMode == WeekNumberMode.SEMESTER) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_current_week_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = currentSemesterWeek.toString(),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = stringResource(R.string.settings_semester_current_week_value, currentSemesterWeek),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_semester_start_date_value, semesterWeekStartDate.format(DateTimeFormatter.ISO_LOCAL_DATE)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    onSemesterWeekStartDateChange(LocalDate.of(year, month + 1, dayOfMonth))
                                },
                                semesterWeekStartDate.year,
                                semesterWeekStartDate.monthValue - 1,
                                semesterWeekStartDate.dayOfMonth,
                            ).show()
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_semester_start_date_pick_button),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_week_mode_natural),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.settings_natural_current_week_value, currentNaturalWeek),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ReminderKeepAliveSettingsPage(
    contentPadding: PaddingValues,
    reminderEnabled: Boolean,
    reminderMinutes: Int,
    keepAliveLevel: KeepAliveLevel,
    experimentalAccessibilityKeepAliveEnabled: Boolean,
    keepAliveStatus: String,
    onBack: () -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onReminderMinutesChange: (Int) -> Unit,
    onKeepAliveLevelChange: (KeepAliveLevel) -> Unit,
    onToggleExperimentalAccessibilityKeepAlive: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onRefreshKeepAliveStatus: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SecondaryPageHeader(
            title = stringResource(R.string.settings_reminder_keepalive_title),
            onBack = onBack,
            backLabel = stringResource(R.string.settings_about_back_button),
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.ghost_title_reminder),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = stringResource(R.string.settings_reminder_keepalive_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.settings_reminder_keepalive_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSwitchCard(
            icon = Icons.Filled.CheckCircle,
            title = stringResource(R.string.settings_reminder_toggle_title),
            desc = stringResource(R.string.settings_reminder_toggle_desc),
            checked = reminderEnabled,
            onCheckedChange = onToggleReminder,
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_reminder_lead_title, reminderMinutes),
                    fontWeight = FontWeight.SemiBold,
                )
                androidx.compose.material3.Slider(
                    value = reminderMinutes.toFloat(),
                    onValueChange = { onReminderMinutesChange(it.toInt().coerceIn(5, 60)) },
                    valueRange = 5f..60f,
                )
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "提醒保活强度",
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = keepAliveLevel == KeepAliveLevel.ECO,
                        onClick = { onKeepAliveLevelChange(KeepAliveLevel.ECO) },
                        label = { Text("省电") },
                    )
                    FilterChip(
                        selected = keepAliveLevel == KeepAliveLevel.BALANCED,
                        onClick = { onKeepAliveLevelChange(KeepAliveLevel.BALANCED) },
                        label = { Text("均衡") },
                    )
                    FilterChip(
                        selected = keepAliveLevel == KeepAliveLevel.AGGRESSIVE,
                        onClick = { onKeepAliveLevelChange(KeepAliveLevel.AGGRESSIVE) },
                        label = { Text("增强") },
                    )
                }
                Text(
                    text = keepAliveStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "实验功能：无障碍保活仅用于内测/侧载，请勿用于 Play 上架版本。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("启用实验无障碍保活", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = experimentalAccessibilityKeepAliveEnabled,
                        onCheckedChange = onToggleExperimentalAccessibilityKeepAlive,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpenAccessibilitySettings,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) { Text("无障碍设置") }
                    Button(
                        onClick = onRefreshKeepAliveStatus,
                        shape = RoundedCornerShape(999.dp),
                    ) { Text("刷新状态") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpenBatteryOptimizationSettings,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) { Text("电池白名单") }
                    Button(
                        onClick = onOpenExactAlarmSettings,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) { Text("精确闹钟") }
                }
            }
        }
    }
}

@Composable
internal fun SyncCommunicationSettingsPage(
    contentPadding: PaddingValues,
    localScheduleUpdatedAt: Long,
    lastSnapshotAt: Long,
    wearConnectionMessage: String,
    wearPushStatus: String,
    wearAckStatus: String,
    cloudSummary: String,
    cloudSyncStatus: String,
    configPushStatus: String,
    onBack: () -> Unit,
    onOpenWearCommunicationPage: () -> Unit,
    onOpenCloudSyncPage: () -> Unit,
    onRefreshWearStatus: () -> Unit,
    onSyncWearNow: () -> Unit,
    onTestCloudConnection: () -> Unit,
    onSyncCloudNow: () -> Unit,
) {
    val localUpdatedText = if (localScheduleUpdatedAt > 0L) {
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(localScheduleUpdatedAt), java.time.ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
    } else {
        stringResource(R.string.settings_cloud_sync_never)
    }
    val snapshotText = if (lastSnapshotAt > 0L) {
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(lastSnapshotAt), java.time.ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
    } else {
        stringResource(R.string.settings_snapshot_empty)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SecondaryPageHeader(
            title = stringResource(R.string.settings_sync_comm_title),
            onBack = onBack,
            backLabel = stringResource(R.string.settings_about_back_button),
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.ghost_title_sync),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = stringResource(R.string.settings_sync_comm_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.settings_sync_comm_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsEntryCard(
            icon = Icons.Filled.Watch,
            title = stringResource(R.string.settings_wear_comm_title),
            desc = stringResource(R.string.settings_wear_comm_desc),
            onClick = onOpenWearCommunicationPage,
        )

        SettingsEntryCard(
            icon = Icons.Filled.CloudSync,
            title = stringResource(R.string.settings_cloud_sync_title),
            desc = stringResource(R.string.settings_cloud_sync_desc),
            onClick = onOpenCloudSyncPage,
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_sync_diag_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.settings_sync_diag_local_updated, localUpdatedText),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.settings_sync_diag_snapshot_updated, snapshotText),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.settings_sync_diag_wear_connection, wearConnectionMessage),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.settings_sync_diag_wear_push, wearPushStatus.ifBlank { "-" }),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.settings_sync_diag_wear_ack, wearAckStatus.ifBlank { "-" }),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.settings_sync_diag_cloud_summary, cloudSummary),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.settings_sync_diag_cloud_result, cloudSyncStatus.ifBlank { "-" }),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.settings_sync_diag_config_push, configPushStatus.ifBlank { "-" }),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRefreshWearStatus, shape = RoundedCornerShape(999.dp)) {
                        Text(stringResource(R.string.settings_sync_diag_refresh_wear))
                    }
                    Button(onClick = onSyncWearNow, shape = RoundedCornerShape(999.dp)) {
                        Text(stringResource(R.string.settings_sync_diag_sync_wear))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onTestCloudConnection, shape = RoundedCornerShape(999.dp)) {
                        Text(stringResource(R.string.settings_sync_diag_test_cloud))
                    }
                    Button(onClick = onSyncCloudNow, shape = RoundedCornerShape(999.dp)) {
                        Text(stringResource(R.string.settings_sync_diag_sync_cloud))
                    }
                }
            }
        }
    }
}

@Composable
internal fun WearCommunicationSettingsPage(
    contentPadding: PaddingValues,
    wearSyncMode: WearSyncMode,
    autoDetectedLabel: String,
    autoEffectiveLabel: String,
    autoFallbackHint: String,
    wearConnectionMessage: String,
    wearSyncMessage: String,
    wearSyncInProgress: Boolean,
    onBack: () -> Unit,
    onWearSyncModeChange: (WearSyncMode) -> Unit,
    onRefreshWearStatus: () -> Unit,
    onManualWearSync: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SecondaryPageHeader(
            title = stringResource(R.string.settings_wear_comm_title),
            onBack = onBack,
            backLabel = stringResource(R.string.settings_about_back_button),
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.ghost_title_wear),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = stringResource(R.string.settings_wear_comm_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.settings_wear_comm_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_wear_sync_mode_label),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = wearSyncMode == WearSyncMode.AUTO,
                        onClick = { onWearSyncModeChange(WearSyncMode.AUTO) },
                        label = { Text(stringResource(R.string.settings_wear_sync_mode_auto)) },
                    )
                    FilterChip(
                        selected = wearSyncMode == WearSyncMode.WEARABLE_API,
                        onClick = { onWearSyncModeChange(WearSyncMode.WEARABLE_API) },
                        label = { Text(stringResource(R.string.settings_wear_sync_mode_wearable_api)) },
                    )
                    FilterChip(
                        selected = wearSyncMode == WearSyncMode.WEAROS_APP,
                        onClick = { onWearSyncModeChange(WearSyncMode.WEAROS_APP) },
                        label = { Text(stringResource(R.string.settings_wear_sync_mode_wearos_app)) },
                    )
                }
                if (wearSyncMode == WearSyncMode.AUTO) {
                    Text(
                        text = autoDetectedLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = autoEffectiveLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (autoFallbackHint.isNotBlank()) {
                        Text(
                            text = autoFallbackHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Text(
                        text = stringResource(R.string.settings_wear_connection_label, wearConnectionMessage),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Text(
                        text = stringResource(R.string.settings_wear_sync_label, wearSyncMessage),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onRefreshWearStatus,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(stringResource(R.string.settings_wear_refresh_button))
                    }
                    Button(
                        onClick = onManualWearSync,
                        enabled = !wearSyncInProgress,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            if (wearSyncInProgress) {
                                stringResource(R.string.settings_wear_syncing_button)
                            } else {
                                stringResource(R.string.settings_wear_sync_button)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CloudSyncSettingsPage(
    contentPadding: PaddingValues,
    provider: CloudProviderUi,
    enabled: Boolean,
    serverUrl: String,
    remotePath: String,
    username: String,
    password: String,
    driveFileName: String,
    driveConnected: Boolean,
    driveTokenExpireText: String,
    showDriveCnWarning: Boolean,
    accountSummary: AccountSummary,
    membershipSummary: MembershipSummary,
    officialSyncFrequency: OfficialSyncFrequency,
    syncScopes: Set<SyncScope>,
    syncStatus: String,
    configPushStatus: String,
    lastSyncedAt: Long,
    syncInProgress: Boolean,
    recentChanges: List<SyncChangeLogEntry>,
    onBack: () -> Unit,
    onProviderChange: (CloudProviderUi) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onRemotePathChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDriveFileNameChange: (String) -> Unit,
    onOfficialSyncFrequencyChange: (OfficialSyncFrequency) -> Unit,
    onSyncScopeToggle: (SyncScope, Boolean) -> Unit,
    onOpenAccountPage: () -> Unit,
    onConnectDrive: () -> Unit,
    onDisconnectDrive: () -> Unit,
    onSave: () -> Unit,
    onTestConnection: () -> Unit,
    onSyncNow: () -> Unit,
    onRestoreChange: (String, String) -> Unit,
    canRestoreChange: (String, String) -> Boolean,
) {
    val lastSyncedText = if (lastSyncedAt > 0L) {
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(lastSyncedAt), java.time.ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
    } else {
        stringResource(R.string.settings_cloud_sync_never)
    }
    val officialLocked = provider == CloudProviderUi.OFFICIAL && !membershipSummary.isMember
    val officialBaseUrl = "https://api-classing.underflo.ink"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SecondaryPageHeader(
            title = stringResource(R.string.settings_cloud_sync_title),
            onBack = onBack,
            backLabel = stringResource(R.string.settings_about_back_button),
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.ghost_title_cloud),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = stringResource(R.string.settings_cloud_sync_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.settings_cloud_sync_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSwitchCard(
            icon = Icons.Filled.CloudSync,
            title = stringResource(R.string.settings_cloud_sync_enable_title),
            desc = if (officialLocked) {
                "Official cloud requires an active membership."
            } else {
                stringResource(R.string.settings_cloud_sync_enable_desc)
            },
            checked = enabled,
            onCheckedChange = { if (!officialLocked) onEnabledChange(it) },
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_cloud_sync_provider),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = provider == CloudProviderUi.WEBDAV,
                        onClick = { onProviderChange(CloudProviderUi.WEBDAV) },
                        label = { Text("WebDAV") },
                    )
                    FilterChip(
                        selected = provider == CloudProviderUi.GOOGLE_DRIVE,
                        onClick = { onProviderChange(CloudProviderUi.GOOGLE_DRIVE) },
                        label = { Text("Google Drive") },
                    )
                    FilterChip(
                        selected = provider == CloudProviderUi.OFFICIAL,
                        onClick = { onProviderChange(CloudProviderUi.OFFICIAL) },
                        label = { Text("Official") },
                    )
                }
                if (provider == CloudProviderUi.WEBDAV) {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = onServerUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_cloud_sync_server_url)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = remotePath,
                        onValueChange = onRemotePathChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_cloud_sync_remote_path)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_cloud_sync_username)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_cloud_sync_password)) },
                        singleLine = true,
                    )
                } else if (provider == CloudProviderUi.GOOGLE_DRIVE) {
                    OutlinedTextField(
                        value = driveFileName,
                        onValueChange = onDriveFileNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_cloud_sync_drive_file_name)) },
                        singleLine = true,
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_cloud_sync_drive_status,
                            if (driveConnected) {
                                stringResource(R.string.settings_cloud_sync_drive_connected)
                            } else {
                                stringResource(R.string.settings_cloud_sync_drive_not_connected)
                            },
                            driveTokenExpireText,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onConnectDrive,
                            enabled = !syncInProgress,
                            shape = RoundedCornerShape(999.dp),
                        ) {
                            Text(stringResource(R.string.settings_cloud_sync_drive_connect))
                        }
                        Button(
                            onClick = onDisconnectDrive,
                            enabled = !syncInProgress && driveConnected,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            Text(stringResource(R.string.settings_cloud_sync_drive_disconnect))
                        }
                    }
                    if (showDriveCnWarning) {
                        Text(
                            text = stringResource(R.string.settings_cloud_sync_drive_cn_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = officialBaseUrl,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Official API") },
                        singleLine = true,
                        readOnly = true,
                    )
                    Text(
                        text = if (accountSummary.userId.isBlank()) {
                            "Login on this phone to use official cloud sync."
                        } else if (membershipSummary.isMember) {
                            "Membership active. Official cloud is available."
                        } else {
                            "Membership inactive. Upgrade to unlock official cloud sync."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (officialLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (officialLocked) {
                        Button(
                            onClick = onOpenAccountPage,
                            shape = RoundedCornerShape(999.dp),
                        ) {
                            Text("Open account")
                        }
                    }
                }
                Text(
                    text = "Sync scopes",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                SyncScope.entries.forEach { scope ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = when (scope) {
                                SyncScope.TIMETABLE -> "Timetable"
                                SyncScope.MOBILE_SETTINGS -> "Mobile settings"
                                SyncScope.WEAR_SETTINGS -> "Wear settings"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(
                            checked = syncScopes.contains(scope),
                            onCheckedChange = { onSyncScopeToggle(scope, it) },
                        )
                    }
                }
                if (provider == CloudProviderUi.OFFICIAL) {
                    Text(
                        text = "Auto sync frequency",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OfficialSyncFrequency.entries.forEach { frequency ->
                            FilterChip(
                                selected = officialSyncFrequency == frequency,
                                onClick = { if (!officialLocked) onOfficialSyncFrequencyChange(frequency) },
                                enabled = !officialLocked,
                                label = {
                                    Text(
                                        when (frequency) {
                                            OfficialSyncFrequency.MANUAL_ONLY -> "Manual"
                                            OfficialSyncFrequency.EVERY_15_MIN -> "15m"
                                            OfficialSyncFrequency.EVERY_30_MIN -> "30m"
                                            OfficialSyncFrequency.EVERY_1_HOUR -> "1h"
                                            OfficialSyncFrequency.EVERY_3_HOURS -> "3h"
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onSave,
                        enabled = !syncInProgress && !officialLocked,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(stringResource(R.string.settings_cloud_sync_save))
                    }
                    Button(
                        onClick = onTestConnection,
                        enabled = !syncInProgress && !officialLocked,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text(stringResource(R.string.settings_cloud_sync_test))
                    }
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
                Text(
                    text = stringResource(R.string.settings_cloud_sync_last_sync, lastSyncedText),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.settings_cloud_sync_status, syncStatus.ifBlank { "-" }),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (configPushStatus.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.settings_cloud_sync_push_status, configPushStatus),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onSyncNow,
                    enabled = !syncInProgress && !officialLocked,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        if (syncInProgress) {
                            stringResource(R.string.settings_cloud_sync_syncing)
                        } else {
                            stringResource(R.string.settings_cloud_sync_sync_now)
                        },
                    )
                }
            }
        }
        if (recentChanges.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.settings_cloud_sync_recent_changes), style = MaterialTheme.typography.titleSmall)
                    recentChanges.take(20).forEach { change ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "${change.action}: ${change.recordId}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "${change.domain} · ${change.version.deviceId.take(8)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (change.action == "deleted" && canRestoreChange(change.domain, change.recordId)) {
                                TextButton(onClick = { onRestoreChange(change.domain, change.recordId) }) {
                                    Text(stringResource(R.string.settings_cloud_sync_restore))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AccountSettingsPage(
    contentPadding: PaddingValues,
    accountSummary: AccountSummary,
    membershipSummary: MembershipSummary,
    statusMessage: String,
    busy: Boolean,
    onBack: () -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onLogout: () -> Unit,
    onRefresh: () -> Unit,
    onRedeem: (String) -> Unit,
    onRequestPasswordReset: (String) -> Unit,
    onConfirmPasswordReset: (String, String) -> Unit,
) {
    var identifier by remember { mutableStateOf(accountSummary.identifier) }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf(accountSummary.username) }
    var email by remember { mutableStateOf(accountSummary.email) }
    var registerPassword by remember { mutableStateOf("") }
    var redeemCode by remember { mutableStateOf("") }
    var resetEmail by remember { mutableStateOf(accountSummary.email) }
    var resetToken by remember { mutableStateOf("") }
    var resetNewPassword by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SecondaryPageHeader(
            title = "Account",
            onBack = onBack,
            backLabel = stringResource(R.string.settings_about_back_button),
            modifier = Modifier.fillMaxWidth(),
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Account status", fontWeight = FontWeight.SemiBold)
                Text(
                    if (accountSummary.userId.isBlank()) {
                        "Not logged in"
                    } else {
                        "Signed in as ${accountSummary.username.ifBlank { accountSummary.identifier }}"
                    },
                )
                Text(
                    "Membership: ${if (membershipSummary.isMember) membershipSummary.tier else "FREE"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (membershipSummary.expiresAt > 0L) {
                    Text(
                        "Expires at: ${
                            LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(membershipSummary.expiresAt),
                                java.time.ZoneId.systemDefault(),
                            ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (statusMessage.isNotBlank()) {
                    Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRefresh, enabled = !busy, shape = RoundedCornerShape(999.dp)) {
                        Text("Refresh")
                    }
                    Button(onClick = onLogout, enabled = !busy && accountSummary.userId.isNotBlank(), shape = RoundedCornerShape(999.dp)) {
                        Text("Logout")
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Login", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = identifier, onValueChange = { identifier = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Email or username") }, singleLine = true)
                OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true)
                Button(onClick = { onLogin(identifier, password) }, enabled = !busy, shape = RoundedCornerShape(999.dp)) {
                    Text("Login")
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Register", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = username, onValueChange = { username = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
                OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true)
                OutlinedTextField(value = registerPassword, onValueChange = { registerPassword = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true)
                Button(onClick = { onRegister(username, email, registerPassword) }, enabled = !busy, shape = RoundedCornerShape(999.dp)) {
                    Text("Register")
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Membership code", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = redeemCode, onValueChange = { redeemCode = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Redeem code") }, singleLine = true)
                Button(
                    onClick = { onRedeem(redeemCode) },
                    enabled = !busy && accountSummary.userId.isNotBlank(),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text("Redeem")
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Password reset", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = resetEmail, onValueChange = { resetEmail = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Reset email") }, singleLine = true)
                Button(onClick = { onRequestPasswordReset(resetEmail) }, enabled = !busy, shape = RoundedCornerShape(999.dp)) {
                    Text("Send reset email")
                }
                OutlinedTextField(value = resetToken, onValueChange = { resetToken = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Reset token") }, singleLine = true)
                OutlinedTextField(value = resetNewPassword, onValueChange = { resetNewPassword = it }, modifier = Modifier.fillMaxWidth(), label = { Text("New password") }, singleLine = true)
                Button(onClick = { onConfirmPasswordReset(resetToken, resetNewPassword) }, enabled = !busy, shape = RoundedCornerShape(999.dp)) {
                    Text("Confirm reset")
                }
            }
        }
    }
}

@Composable
internal fun DailyBriefingSettingsPage(
    contentPadding: PaddingValues,
    enabled: Boolean,
    channel: DailyBriefingChannel,
    time: String,
    loggedIn: Boolean,
    statusMessage: String,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onChannelChange: (DailyBriefingChannel) -> Unit,
    onTimeChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SecondaryPageHeader(
            title = "Daily Briefing",
            onBack = onBack,
            backLabel = stringResource(R.string.settings_about_back_button),
            modifier = Modifier.fillMaxWidth(),
        )

        SettingsSwitchCard(
            icon = Icons.Filled.MailOutline,
            title = "Enable daily briefing",
            desc = "App notification can work offline; email requires login.",
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Channel", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DailyBriefingChannel.entries.forEach { item ->
                        val disabled = !loggedIn && item == DailyBriefingChannel.EMAIL
                        FilterChip(
                            selected = channel == item,
                            onClick = { if (!disabled) onChannelChange(item) },
                            label = { Text(item.name) },
                        )
                    }
                }
                OutlinedTextField(
                    value = time,
                    onValueChange = onTimeChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Time (HH:mm)") },
                    singleLine = true,
                )
                if (statusMessage.isNotBlank()) {
                    Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Button(onClick = onSave, shape = RoundedCornerShape(999.dp)) {
                    Text("Save briefing")
                }
            }
        }
    }
}

@Composable
internal fun AboutLayer(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val versionName = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")
    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogContent by remember { mutableStateOf("") }
    var showWechatDialog by remember { mutableStateOf(false) }

    fun showInfoDialog(title: String, content: String) {
        infoDialogTitle = title
        infoDialogContent = content
    }

    val openNotice: () -> Unit = {
        scope.launch {
            val title = context.getString(R.string.settings_about_notice_title)
            showInfoDialog(
                title = title,
                content = context.getString(R.string.settings_about_notice_loading),
            )
            val content = fetchPlainTextFromEndpoint(NOTICE_ENDPOINT_URL)
                .fold(
                    onSuccess = { text ->
                        text.ifBlank { context.getString(R.string.settings_about_notice_empty) }
                    },
                    onFailure = { error ->
                        context.getString(
                            R.string.settings_about_notice_failed,
                            error.message ?: "unknown",
                        )
                    },
                )
            showInfoDialog(title = title, content = content)
        }
    }

    val checkLatestVersion: () -> Unit = {
        scope.launch {
            val title = context.getString(R.string.settings_about_check_update_title)
            showInfoDialog(
                title = title,
                content = context.getString(R.string.settings_about_check_update_loading),
            )
            val content = fetchPlainTextFromEndpoint(LATEST_VERSION_ENDPOINT_URL)
                .fold(
                    onSuccess = { text ->
                        text.ifBlank { context.getString(R.string.settings_about_notice_empty) }
                    },
                    onFailure = { error ->
                        context.getString(
                            R.string.settings_about_check_update_failed,
                            error.message ?: "unknown",
                        )
                    },
                )
            showInfoDialog(title = title, content = content)
        }
    }

    val openWechatSupport: () -> Unit = { showWechatDialog = true }
    val aboutQuote = stringResource(R.string.settings_about_quote).trim()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .navigationBarsPadding()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        SecondaryPageHeader(
            title = stringResource(R.string.settings_about_page_title),
            onBack = onBack,
            backLabel = stringResource(R.string.settings_about_back_button),
            modifier = Modifier.fillMaxWidth(),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                                    ),
                                ),
                                shape = RoundedCornerShape(24.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier.size(54.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_about_version_value, versionName),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.settings_about_resources_title),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AboutResourceRow(
                        icon = Icons.Filled.HelpOutline,
                        title = stringResource(R.string.settings_about_notice_title),
                        showDivider = true,
                        onClick = openNotice,
                    )
                    AboutResourceRow(
                        icon = Icons.Filled.OpenInNew,
                        title = stringResource(R.string.settings_about_check_update_title),
                        showDivider = true,
                        onClick = checkLatestVersion,
                    )
                    AboutResourceRow(
                        icon = Icons.Filled.MailOutline,
                        title = stringResource(R.string.settings_about_wechat_support),
                        showDivider = false,
                        onClick = openWechatSupport,
                    )
                }
            }

            if (aboutQuote.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                ) {
                    Text(
                        text = aboutQuote,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Text(
                text = "ICP 备案号：待补充",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_about_footer_copyright, Year.now().value),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    infoDialogTitle?.let { title ->
        AlertDialog(
            onDismissRequest = { infoDialogTitle = null },
            title = { Text(title) },
            text = {
                Text(
                    text = infoDialogContent,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = { infoDialogTitle = null }) {
                    Text(text = context.getString(android.R.string.ok))
                }
            },
        )
    }

    if (showWechatDialog) {
        AlertDialog(
            onDismissRequest = { showWechatDialog = false },
            title = { Text(stringResource(R.string.settings_about_wechat_support)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.wechat_support_qr),
                        contentDescription = stringResource(R.string.settings_about_wechat_support),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Text(
                        text = stringResource(R.string.settings_about_wechat_support_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showWechatDialog = false }) {
                    Text(text = context.getString(android.R.string.ok))
                }
            },
        )
    }
}

private const val NOTICE_ENDPOINT_URL = "https://api.classing.underflo.ink/api/getNotice"
private const val LATEST_VERSION_ENDPOINT_URL = "https://api.classing.underflo.ink/api/getLatestVer"

private suspend fun fetchPlainTextFromEndpoint(url: String): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "text/plain, application/json, */*")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                error("HTTP $code ${body.take(120)}".trim())
            }
            body.trimStart('\uFEFF').trim()
        } finally {
            connection.disconnect()
        }
    }
}

@Composable
private fun AboutResourceRow(
    icon: ImageVector,
    title: String,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp),
            )
        }
        if (showDivider) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
            ) {}
        }
    }
}

@Composable
internal fun LessonCard(lesson: LessonUi) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.size(width = 72.dp, height = 48.dp),
            ) {
                Text(
                    lesson.startTime.format(clockFormatter),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    lesson.endTime.format(clockFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(lesson.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    lesson.location ?: stringResource(R.string.no_location),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!lesson.note.isNullOrBlank()) {
                    Text(
                        lesson.note,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchCard(
    icon: ImageVector,
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(999.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SettingsEntryCard(
    icon: ImageVector,
    title: String,
    desc: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
