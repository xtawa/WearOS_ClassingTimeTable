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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xtawa.classingtime.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
internal fun SettingsLayer(
    contentPadding: PaddingValues,
    showWeekend: Boolean,
    reminderEnabled: Boolean,
    reminderMinutes: Int,
    onOpenImportPage: () -> Unit,
    onOpenWeekModePage: () -> Unit,
    onOpenWearCommunicationPage: () -> Unit,
    onOpenAboutPage: () -> Unit,
    onToggleWeekend: (Boolean) -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onReminderMinutesChange: (Int) -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onClearAllSchedules: () -> Unit,
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
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
            badge = stringResource(R.string.settings_badge_weekend),
            title = stringResource(R.string.settings_show_weekend_title),
            desc = stringResource(R.string.settings_show_weekend_desc),
            checked = showWeekend,
            onCheckedChange = onToggleWeekend,
        )

        SettingsSwitchCard(
            badge = stringResource(R.string.settings_badge_reminder),
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

        SettingsEntryCard(
            badge = stringResource(R.string.settings_badge_import),
            title = stringResource(R.string.settings_import_entry_title),
            desc = stringResource(R.string.settings_import_entry_desc),
            onClick = onOpenImportPage,
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.settings_backup_title), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_backup_desc), style = MaterialTheme.typography.bodySmall)
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

        SettingsEntryCard(
            badge = stringResource(R.string.settings_badge_week_mode),
            title = stringResource(R.string.settings_week_mode_title),
            desc = stringResource(R.string.settings_week_mode_desc),
            onClick = onOpenWeekModePage,
        )

        SettingsEntryCard(
            badge = stringResource(R.string.settings_badge_wear),
            title = stringResource(R.string.settings_wear_comm_title),
            desc = stringResource(R.string.settings_wear_comm_desc),
            onClick = onOpenWearCommunicationPage,
        )

        SettingsEntryCard(
            badge = stringResource(R.string.settings_badge_about),
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
internal fun WeekModeSettingsPage(
    contentPadding: PaddingValues,
    weekNumberMode: WeekNumberMode,
    semesterWeekStartDate: LocalDate,
    onBack: () -> Unit,
    onWeekNumberModeChange: (WeekNumberMode) -> Unit,
    onSemesterWeekStartDateChange: (LocalDate) -> Unit,
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val currentSemesterWeek = (ChronoUnit.DAYS.between(semesterWeekStartDate, today) / 7L + 1L)
        .toInt()
        .coerceAtLeast(1)
    val currentNaturalWeek = today.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.settings_about_back_button))
        }

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
internal fun WearCommunicationSettingsPage(
    contentPadding: PaddingValues,
    wearSyncMode: WearSyncMode,
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
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.settings_about_back_button))
        }

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
internal fun AboutLayer(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.settings_about_back_button))
            }
        }
        Text(
            stringResource(R.string.settings_about_page_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Card {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.settings_about_links_title), fontWeight = FontWeight.SemiBold)
                Text(
                    text = stringResource(R.string.settings_about_official_site),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { runCatching { uriHandler.openUri("https://lyxyy.notion.site/classing") } },
                )
                Text(
                    text = stringResource(R.string.settings_about_help_support),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { runCatching { uriHandler.openUri("https://lyxyy.notion.site/classinghelp") } },
                )
                Text(
                    text = stringResource(R.string.settings_about_contact_us),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { runCatching { uriHandler.openUri("mailto:zeromostia@gmail.com") } },
                )
            }
        }
        Text(
            text = stringResource(R.string.settings_about_ai_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp),
        )
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
    badge: String,
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
                    Text(badge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(desc, style = MaterialTheme.typography.bodySmall)
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SettingsEntryCard(
    badge: String,
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
                Text(badge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
