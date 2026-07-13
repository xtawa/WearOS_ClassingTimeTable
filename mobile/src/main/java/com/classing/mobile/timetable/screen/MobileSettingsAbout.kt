package com.xtawa.classingtime.screen

import android.app.DatePickerDialog
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import com.classing.shared.sync.SyncChangeLogEntry
import com.xtawa.classingtime.R
import com.xtawa.classingtime.BuildConfig
import com.xtawa.classingtime.data.AccountSummary
import com.xtawa.classingtime.data.DailyBriefingChannel
import com.xtawa.classingtime.data.MembershipSummary
import com.xtawa.classingtime.data.OfficialSyncFrequency
import com.xtawa.classingtime.account.PendingEmailChange
import com.xtawa.classingtime.data.SyncScope
import com.xtawa.classingtime.reminder.KeepAliveLevel
import com.xtawa.classingtime.update.AppUpdateRelease
import com.xtawa.classingtime.update.ClientRequestRateLimitException
import com.xtawa.classingtime.update.InstallLaunchResult
import com.xtawa.classingtime.update.ReleaseChannel
import com.xtawa.classingtime.update.ReleaseChannelPreference
import com.xtawa.classingtime.update.UpdateApiClient
import com.xtawa.classingtime.update.launchUpdateInstaller
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
internal fun SettingsLayer(
    contentPadding: PaddingValues,
    onOpenAccountPage: () -> Unit,
    onOpenImportPage: () -> Unit,
    onOpenBackupRestorePage: () -> Unit,
    onOpenWeekModePage: () -> Unit,
    onOpenReminderKeepAlivePage: () -> Unit,
    onOpenSyncCommunicationPage: () -> Unit,
    onOpenAboutPage: () -> Unit,
    onClearAllSchedules: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
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

        SettingsEntryCard(
            icon = Icons.Filled.CalendarMonth,
            title = stringResource(R.string.settings_week_mode_title),
            desc = stringResource(R.string.settings_week_mode_desc),
            onClick = onOpenWeekModePage,
        )

        SettingsEntryCard(
            icon = Icons.Filled.CheckCircle,
            title = stringResource(R.string.settings_reminder_keepalive_title),
            desc = stringResource(R.string.settings_reminder_keepalive_desc),
            onClick = onOpenReminderKeepAlivePage,
        )

        SettingsEntryCard(
            icon = Icons.Filled.Person,
            title = stringResource(R.string.settings_account_title),
            desc = stringResource(R.string.settings_account_desc),
            onClick = onOpenAccountPage,
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
            icon = Icons.Filled.Sync,
            title = stringResource(R.string.settings_sync_comm_title),
            desc = stringResource(R.string.settings_sync_comm_desc),
            onClick = onOpenSyncCommunicationPage,
        )

        SettingsEntryCard(
            icon = Icons.Filled.OpenInNew,
            title = stringResource(R.string.settings_open_web_title),
            desc = stringResource(R.string.settings_open_web_desc),
            onClick = { uriHandler.openUri("https://api-classing.underflo.ink") },
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
    showWeekend: Boolean,
    weekNumberMode: WeekNumberMode,
    semesterWeekStartDate: LocalDate,
    weekStartDay: DayOfWeek,
    onBack: () -> Unit,
    onShowWeekendChange: (Boolean) -> Unit,
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

        SettingsSwitchCard(
            icon = Icons.Filled.CalendarMonth,
            title = stringResource(R.string.settings_show_weekend_title),
            desc = stringResource(R.string.settings_show_weekend_desc),
            checked = showWeekend,
            onCheckedChange = onShowWeekendChange,
        )

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
                            text = stringResource(R.string.settings_semester_switch_button),
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
    keepAliveStatus: String,
    onBack: () -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onReminderMinutesChange: (Int) -> Unit,
    onKeepAliveLevelChange: (KeepAliveLevel) -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onRefreshKeepAliveStatus: () -> Unit,
    onOpenDailyBriefing: () -> Unit,
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
                text = stringResource(R.string.keepalive_level_title),
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = keepAliveLevel == KeepAliveLevel.ECO,
                        onClick = { onKeepAliveLevelChange(KeepAliveLevel.ECO) },
                    label = { Text(stringResource(R.string.keepalive_level_eco)) },
                    )
                    FilterChip(
                        selected = keepAliveLevel == KeepAliveLevel.BALANCED,
                        onClick = { onKeepAliveLevelChange(KeepAliveLevel.BALANCED) },
                    label = { Text(stringResource(R.string.keepalive_level_balanced)) },
                    )
                    FilterChip(
                        selected = keepAliveLevel == KeepAliveLevel.AGGRESSIVE,
                        onClick = { onKeepAliveLevelChange(KeepAliveLevel.AGGRESSIVE) },
                    label = { Text(stringResource(R.string.keepalive_level_aggressive)) },
                    )
                }
                Text(
                    text = keepAliveStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onRefreshKeepAliveStatus,
                    shape = RoundedCornerShape(999.dp),
                ) { Text(stringResource(R.string.keepalive_refresh_status)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpenBatteryOptimizationSettings,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                ) { Text(stringResource(R.string.keepalive_battery_whitelist)) }
                    Button(
                        onClick = onOpenExactAlarmSettings,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                ) { Text(stringResource(R.string.keepalive_exact_alarm)) }
                }
            }
        }

        SettingsEntryCard(
            icon = Icons.Filled.MailOutline,
            title = stringResource(R.string.settings_daily_briefing_title),
            desc = stringResource(R.string.settings_daily_briefing_desc),
            onClick = onOpenDailyBriefing,
        )
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
                stringResource(R.string.official_cloud_membership_required)
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
                        label = { Text(stringResource(R.string.cloud_provider_webdav)) },
                    )
                    FilterChip(
                        selected = provider == CloudProviderUi.GOOGLE_DRIVE,
                        onClick = { onProviderChange(CloudProviderUi.GOOGLE_DRIVE) },
                        label = { Text(stringResource(R.string.cloud_provider_google_drive)) },
                    )
                    FilterChip(
                        selected = provider == CloudProviderUi.OFFICIAL,
                        onClick = { onProviderChange(CloudProviderUi.OFFICIAL) },
                        label = { Text(stringResource(R.string.cloud_provider_official)) },
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
                        label = { Text(stringResource(R.string.official_cloud_api_label)) },
                        singleLine = true,
                        readOnly = true,
                    )
                    Text(
                        text = if (accountSummary.userId.isBlank()) {
                            stringResource(R.string.official_cloud_login_required)
                        } else if (membershipSummary.isMember) {
                            stringResource(R.string.official_cloud_membership_active)
                        } else {
                            stringResource(R.string.official_cloud_membership_inactive)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (officialLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (officialLocked) {
                        Button(
                            onClick = onOpenAccountPage,
                            shape = RoundedCornerShape(999.dp),
                        ) {
                            Text(stringResource(R.string.official_cloud_open_account))
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.cloud_sync_scopes_title),
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
                                SyncScope.TIMETABLE -> stringResource(R.string.cloud_sync_scope_timetable)
                                SyncScope.MOBILE_SETTINGS -> stringResource(R.string.cloud_sync_scope_mobile_settings)
                                SyncScope.WEAR_SETTINGS -> stringResource(R.string.cloud_sync_scope_wear_settings)
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
                        text = stringResource(R.string.official_cloud_frequency_title),
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
                                            OfficialSyncFrequency.MANUAL_ONLY -> stringResource(R.string.official_cloud_frequency_manual)
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
    pendingEmailChange: PendingEmailChange?,
    loginLockSeconds: Int,
    onBack: () -> Unit,
    onLogin: (String, String) -> Unit,
    onLogout: () -> Unit,
    onRefresh: () -> Unit,
    onRedeem: (String) -> Unit,
    onOpenRegister: () -> Unit,
    onOpenPasswordReset: () -> Unit,
    onOpenEmailChange: () -> Unit,
) {
    var identifier by remember { mutableStateOf(accountSummary.identifier) }
    var password by remember { mutableStateOf("") }
    var redeemCode by remember { mutableStateOf("") }

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
            title = stringResource(R.string.settings_account_title),
            onBack = onBack,
            backLabel = stringResource(R.string.settings_about_back_button),
            modifier = Modifier.fillMaxWidth(),
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.account_status_title), fontWeight = FontWeight.SemiBold)
                Text(
                    if (accountSummary.userId.isBlank()) {
                        stringResource(R.string.account_not_logged_in)
                    } else {
                        stringResource(
                            R.string.account_signed_in_as,
                            accountSummary.username.ifBlank { accountSummary.identifier },
                        )
                    },
                )
                Text(
                    stringResource(
                        R.string.account_membership_status,
                        if (membershipSummary.isMember) membershipSummary.tier else "FREE",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (membershipSummary.expiresAt > 0L) {
                    Text(
                        stringResource(
                            R.string.account_membership_expires_at,
                            LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(membershipSummary.expiresAt),
                                java.time.ZoneId.systemDefault(),
                            ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (statusMessage.isNotBlank()) {
                    Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                pendingEmailChange?.let {
                    Text(
                        if (it.expiresAt > System.currentTimeMillis()) {
                            stringResource(R.string.account_email_change_pending, it.newEmail)
                        } else {
                            stringResource(R.string.account_email_change_expired)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRefresh, enabled = !busy, shape = RoundedCornerShape(999.dp)) {
                        Text(stringResource(R.string.common_refresh))
                    }
                    Button(onClick = onLogout, enabled = !busy && accountSummary.userId.isNotBlank(), shape = RoundedCornerShape(999.dp)) {
                        Text(stringResource(R.string.account_logout))
                    }
                }
                if (accountSummary.userId.isNotBlank()) {
                    TextButton(onClick = onOpenEmailChange, enabled = !busy) {
                        Text(stringResource(R.string.account_change_email))
                    }
                }
            }
        }

        if (accountSummary.userId.isBlank()) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.account_login), fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.account_identifier)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.account_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    singleLine = true,
                )
                Button(
                    onClick = { onLogin(identifier, password) },
                    enabled = !busy && loginLockSeconds <= 0 && identifier.isNotBlank() && password.isNotBlank(),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        if (loginLockSeconds > 0) stringResource(
                            R.string.account_error_login_locked,
                            loginLockSeconds / 60,
                            loginLockSeconds % 60,
                        ) else stringResource(R.string.account_login),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onOpenRegister, enabled = !busy) {
                        Text(stringResource(R.string.account_register))
                    }
                    TextButton(onClick = onOpenPasswordReset, enabled = !busy) {
                        Text(stringResource(R.string.account_forgot_password))
                    }
                }
            }
        }

        if (accountSummary.userId.isNotBlank() && !membershipSummary.isMember) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.account_membership_code), fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = redeemCode, onValueChange = { redeemCode = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.account_redeem_code)) }, singleLine = true)
                Button(
                    onClick = { onRedeem(redeemCode) },
                    enabled = !busy && accountSummary.userId.isNotBlank() && isValidRedeemCode(redeemCode),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(stringResource(R.string.account_redeem))
                }
            }
        }
    }
}

@Composable
internal fun AccountEmailChangePage(
    contentPadding: PaddingValues,
    username: String,
    statusMessage: String,
    busy: Boolean,
    requestId: String,
    verificationLocked: Boolean,
    onBack: () -> Unit,
    onRequest: (String, String) -> Unit,
    onConfirm: (String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember(requestId) { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp)
            .navigationBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SecondaryPageHeader(
            title = stringResource(R.string.account_change_email),
            onBack = onBack,
            backLabel = stringResource(R.string.settings_account_title),
            modifier = Modifier.fillMaxWidth(),
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.account_new_email)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.account_current_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                Button(
                    onClick = { onRequest(email, password) },
                    enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                    shape = RoundedCornerShape(999.dp),
                ) { Text(stringResource(R.string.account_email_change_send)) }
                if (requestId.isNotBlank()) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(6) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.account_verification_code)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    Button(
                        onClick = { onConfirm(code) },
                        enabled = !busy && !verificationLocked && code.length == 6,
                        shape = RoundedCornerShape(999.dp),
                    ) { Text(stringResource(R.string.account_email_change_confirm)) }
                }
                if (statusMessage.isNotBlank()) {
                    Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
internal fun AccountRegisterPage(
    contentPadding: PaddingValues,
    statusMessage: String,
    busy: Boolean,
    challengeId: String,
    onBack: () -> Unit,
    onRequestVerification: (String, String, String) -> Unit,
    onConfirmVerification: (String) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var resendCooldownSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(resendCooldownSeconds) {
        if (resendCooldownSeconds > 0) {
            delay(1_000)
            resendCooldownSeconds -= 1
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp)
            .navigationBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SecondaryPageHeader(
            title = stringResource(R.string.account_register),
            onBack = onBack,
            backLabel = stringResource(R.string.settings_account_title),
            modifier = Modifier.fillMaxWidth(),
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.account_register_desc), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = username, onValueChange = { username = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.account_username)) }, singleLine = true)
                OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.account_email)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true)
                OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.account_password)) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
                if (statusMessage.isNotBlank()) {
                    Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (challengeId.isBlank()) {
                    Button(
                        onClick = {
                            resendCooldownSeconds = 60
                            onRequestVerification(username.trim(), email.trim(), password)
                        },
                        enabled = !busy && resendCooldownSeconds == 0 && isValidUsername(username) && isValidEmail(email) && isValidPassword(password),
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(stringResource(R.string.account_send_verification))
                    }
                } else {
                    OutlinedTextField(
                        value = verificationCode,
                        onValueChange = { verificationCode = it.filter(Char::isDigit).take(6) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.account_verification_code)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        singleLine = true,
                    )
                    Button(
                        onClick = { onConfirmVerification(verificationCode) },
                        enabled = !busy && isValidVerificationCode(verificationCode),
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(stringResource(R.string.account_confirm_registration))
                    }
                    TextButton(
                        onClick = {
                            resendCooldownSeconds = 60
                            onRequestVerification(username.trim(), email.trim(), password)
                        },
                        enabled = !busy && resendCooldownSeconds == 0 && isValidUsername(username) && isValidEmail(email) && isValidPassword(password),
                    ) {
                        Text(
                            if (resendCooldownSeconds > 0) stringResource(R.string.account_resend_verification_countdown, resendCooldownSeconds)
                            else stringResource(R.string.account_resend_verification)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TurnstileVerificationDialog(
    siteKey: String,
    onVerified: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_turnstile_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.account_turnstile_desc), style = MaterialTheme.typography.bodySmall)
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()
                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun verified(token: String) {
                                    post { if (token.isNotBlank()) onVerified(token) }
                                }
                            }, "ClassingNative")
                            val safeSiteKey = siteKey.replace(Regex("[^A-Za-z0-9_-]"), "")
                            loadDataWithBaseURL(
                                "https://api-classing.underflo.ink/",
                                """<!doctype html><html><head><meta name="viewport" content="width=device-width"></head><body style="margin:0;background:transparent"><div class="cf-turnstile" data-sitekey="$safeSiteKey" data-callback="verified"></div><script>function verified(token){ClassingNative.verified(token)}</script><script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script></body></html>""",
                                "text/html",
                                "UTF-8",
                                null,
                            )
                        }
                    },
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_about_update_close)) } },
    )
}

@Composable
internal fun AccountPasswordResetPage(
    contentPadding: PaddingValues,
    initialEmail: String,
    statusMessage: String,
    busy: Boolean,
    onBack: () -> Unit,
    onRequestPasswordReset: (String) -> Unit,
    onConfirmPasswordReset: (String, String) -> Unit,
) {
    var email by remember { mutableStateOf(initialEmail) }
    var token by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var requestCooldownSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(requestCooldownSeconds) {
        if (requestCooldownSeconds > 0) {
            delay(1_000)
            requestCooldownSeconds -= 1
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp)
            .navigationBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SecondaryPageHeader(
            title = stringResource(R.string.password_reset_title),
            onBack = onBack,
            backLabel = stringResource(R.string.settings_account_title),
            modifier = Modifier.fillMaxWidth(),
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.password_reset_request_desc), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.account_email)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true)
                Button(onClick = {
                    requestCooldownSeconds = 60
                    onRequestPasswordReset(email.trim())
                }, enabled = !busy && requestCooldownSeconds == 0 && isValidEmail(email), shape = RoundedCornerShape(999.dp)) {
                    Text(
                        if (requestCooldownSeconds > 0) stringResource(R.string.password_reset_send_countdown, requestCooldownSeconds)
                        else stringResource(R.string.password_reset_send_email)
                    )
                }
                OutlinedTextField(value = token, onValueChange = { token = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.password_reset_token)) }, singleLine = true)
                OutlinedTextField(value = newPassword, onValueChange = { newPassword = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.password_reset_new_password)) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
                if (statusMessage.isNotBlank()) {
                    Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Button(onClick = { onConfirmPasswordReset(token.trim(), newPassword) }, enabled = !busy && token.isNotBlank() && isValidPassword(newPassword), shape = RoundedCornerShape(999.dp)) {
                    Text(stringResource(R.string.password_reset_confirm))
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
            title = stringResource(R.string.settings_daily_briefing_title),
            onBack = onBack,
            backLabel = stringResource(R.string.settings_about_back_button),
            modifier = Modifier.fillMaxWidth(),
        )

        SettingsSwitchCard(
            icon = Icons.Filled.MailOutline,
            title = stringResource(R.string.daily_briefing_enable_title),
            desc = stringResource(R.string.daily_briefing_enable_desc),
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.daily_briefing_channel_title), fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DailyBriefingChannel.entries.forEach { item ->
                        val disabled = !loggedIn && item == DailyBriefingChannel.EMAIL
                        FilterChip(
                            selected = channel == item,
                            onClick = { if (!disabled) onChannelChange(item) },
                            label = {
                                Text(
                                    when (item) {
                                        DailyBriefingChannel.APP_NOTIFICATION -> stringResource(R.string.daily_briefing_channel_app)
                                        DailyBriefingChannel.EMAIL -> stringResource(R.string.daily_briefing_channel_email)
                                        DailyBriefingChannel.BOTH -> stringResource(R.string.daily_briefing_channel_both)
                                    },
                                )
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = time,
                    onValueChange = onTimeChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.daily_briefing_time_label)) },
                    singleLine = true,
                )
                if (statusMessage.isNotBlank()) {
                    Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Button(onClick = onSave, shape = RoundedCornerShape(999.dp)) {
                    Text(stringResource(R.string.daily_briefing_save))
                }
            }
        }
    }
}

@Composable
internal fun AboutLayer(
    contentPadding: PaddingValues,
    devModeEnabled: Boolean,
    onBack: () -> Unit,
    onToggleDevMode: (Boolean) -> Unit,
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
    val updateApiClient = remember { UpdateApiClient() }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateAvailable by remember { mutableStateOf(false) }
    var latestRelease by remember { mutableStateOf<AppUpdateRelease?>(null) }
    var updateStatus by remember { mutableStateOf("") }
    var updateDownloading by remember { mutableStateOf(false) }
    var updateDownloadedBytes by remember { mutableStateOf(0L) }
    var updateTotalBytes by remember { mutableStateOf(0L) }
    var downloadedUpdateFile by remember { mutableStateOf<File?>(null) }
	var releaseChannel by remember(context) { mutableStateOf(ReleaseChannelPreference.load(context)) }
	var forceUpdate by remember { mutableStateOf(false) }

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
            val content = updateApiClient.fetchAnnouncements()
                .fold(
                    onSuccess = { announcements ->
                        announcements.joinToString("\n\n") { announcement ->
                            "${announcement.title}\n${announcement.content}"
                        }.ifBlank { context.getString(R.string.settings_about_notice_empty) }
                    },
                    onFailure = { error ->
                        if (error is ClientRequestRateLimitException) {
                            context.getString(R.string.settings_about_rate_limited, error.retryAfterSeconds)
                        } else {
                            context.getString(R.string.settings_about_notice_failed_short)
                        }
                    },
                )
            showInfoDialog(title = title, content = content)
        }
    }

    val checkLatestVersion: () -> Unit = {
        scope.launch {
            showUpdateDialog = true
            updateChecking = true
            updateAvailable = false
            latestRelease = null
            updateStatus = context.getString(R.string.settings_about_check_update_loading)
            updateDownloading = false
            updateDownloadedBytes = 0L
            updateTotalBytes = 0L
            downloadedUpdateFile = null
			updateApiClient.checkLatest(BuildConfig.VERSION_CODE.toLong(), releaseChannel).fold(
                onSuccess = { result ->
                    latestRelease = result.release
                    updateAvailable = result.updateAvailable
					forceUpdate = result.forceUpdate
                    updateStatus = when {
                        result.release == null -> context.getString(R.string.settings_about_update_no_release)
                        result.updateAvailable -> context.getString(
                            R.string.settings_about_update_available,
                            versionName,
                            result.release.versionName,
                        )
                        else -> context.getString(
                            R.string.settings_about_update_latest,
                            versionName,
                            result.release.versionName,
                        )
                    }
                },
                onFailure = { error ->
                    updateStatus = if (error is ClientRequestRateLimitException) {
                        context.getString(R.string.settings_about_rate_limited, error.retryAfterSeconds)
                    } else {
                        context.getString(R.string.settings_about_check_update_failed_short)
                    }
                },
            )
            updateChecking = false
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

            SettingsSwitchCard(
                icon = Icons.Filled.DataObject,
                title = stringResource(R.string.settings_dev_mode_title),
                desc = stringResource(R.string.settings_dev_mode_desc),
                checked = devModeEnabled,
                onCheckedChange = onToggleDevMode,
            )

            if (devModeEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(stringResource(R.string.settings_dev_mode_details_title), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(
                                R.string.settings_dev_mode_details_value,
                                BuildConfig.BUILD_TYPE,
                                BuildConfig.VERSION_CODE,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column {
					Column(
						modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Text(
							text = stringResource(R.string.settings_about_update_channel),
							style = MaterialTheme.typography.labelSmall,
							fontWeight = FontWeight.Bold,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
						Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
							ReleaseChannel.entries.forEach { channel ->
								FilterChip(
									selected = releaseChannel == channel,
									onClick = {
										releaseChannel = channel
										ReleaseChannelPreference.save(context, channel)
									},
									label = {
										Text(
											if (channel == ReleaseChannel.STABLE) {
												stringResource(R.string.settings_about_update_channel_stable)
											} else {
												stringResource(R.string.settings_about_update_channel_beta)
											},
										)
									},
								)
							}
						}
					}
					androidx.compose.material3.HorizontalDivider()
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

    if (showUpdateDialog) {
        val release = latestRelease
        val progress = if (updateTotalBytes > 0L) {
            (updateDownloadedBytes.toFloat() / updateTotalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        AlertDialog(
            onDismissRequest = { if (!updateDownloading) showUpdateDialog = false },
            title = { Text(stringResource(R.string.settings_about_check_update_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(updateStatus, style = MaterialTheme.typography.bodyMedium)
                    if (release != null) {
                        Text(
                            stringResource(
                                R.string.settings_about_update_version_and_size,
                                release.versionName,
                                formatUpdateBytes(release.artifactSize),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
						if (release.mandatory || forceUpdate) {
                            Text(
                                stringResource(R.string.settings_about_update_mandatory),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (release.changelog.isNotBlank()) {
                            Text(release.changelog, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (updateDownloading) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(
                                R.string.settings_about_update_progress,
                                (progress * 100).toInt(),
                                formatUpdateBytes(updateDownloadedBytes),
                                formatUpdateBytes(updateTotalBytes),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            },
            confirmButton = {
                when {
                    updateChecking -> Unit
                    downloadedUpdateFile != null -> {
                        TextButton(
                            onClick = {
                                val result = launchUpdateInstaller(context, downloadedUpdateFile!!)
                                updateStatus = if (result == InstallLaunchResult.PERMISSION_REQUIRED) {
                                    context.getString(R.string.settings_about_update_install_permission)
                                } else {
                                    context.getString(R.string.settings_about_update_installer_opened)
                                }
                            },
                        ) {
                            Text(stringResource(R.string.settings_about_update_install))
                        }
                    }
                    updateAvailable && release != null && !updateDownloading -> {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    updateDownloading = true
                                    updateDownloadedBytes = 0L
                                    updateTotalBytes = release.artifactSize
                                    updateStatus = context.getString(R.string.settings_about_update_downloading)
                                    updateApiClient.downloadRelease(context, release) { downloaded, total ->
                                        updateDownloadedBytes = downloaded
                                        updateTotalBytes = total
                                    }.fold(
                                        onSuccess = { file ->
                                            downloadedUpdateFile = file
                                            updateStatus = context.getString(R.string.settings_about_update_downloaded)
                                            val result = launchUpdateInstaller(context, file)
                                            updateStatus = if (result == InstallLaunchResult.PERMISSION_REQUIRED) {
                                                context.getString(R.string.settings_about_update_install_permission)
                                            } else {
                                                context.getString(R.string.settings_about_update_installer_opened)
                                            }
                                        },
                                        onFailure = {
                                            updateStatus = context.getString(R.string.settings_about_update_download_failed)
                                        },
                                    )
                                    updateDownloading = false
                                }
                            },
                        ) {
                            Text(stringResource(R.string.settings_about_update_download))
                        }
                    }
                }
            },
            dismissButton = {
                if (!updateDownloading) {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text(stringResource(R.string.settings_about_update_close))
                    }
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

private fun formatUpdateBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    if (bytes < 1024L) return "$bytes B"
    if (bytes < 1024L * 1024L) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    return String.format(Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024.0)
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
