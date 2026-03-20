package com.xtawa.classingtime.screen

import android.app.DatePickerDialog
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.xtawa.classingtime.R
import com.xtawa.classingtime.data.MobilePrefsStore
import com.xtawa.classingtime.data.MobileSettings
import com.xtawa.classingtime.data.PersistedLesson
import com.xtawa.classingtime.reminder.ReminderScheduler
import com.classing.shared.sync.WearDataLayerContracts
import com.xtawa.classingtime.sync.WearSyncAckInfo
import com.xtawa.classingtime.sync.WearSyncAckStore
import com.xtawa.classingtime.sync.WearDataLayerSyncPublisher
import com.xtawa.classingtime.sync.WearSyncDispatchResult
import com.google.android.gms.wearable.Wearable
import com.classing.shared.importer.CourseDraft
import com.classing.shared.importer.IcsImportParser
import com.classing.shared.importer.ImportResult
import com.classing.shared.importer.ScheduleImportAdapter
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun SettingsLayer(
    contentPadding: PaddingValues,
    showWeekend: Boolean,
    reminderEnabled: Boolean,
    reminderMinutes: Int,
    weekNumberMode: WeekNumberMode,
    semesterWeekStartDate: LocalDate,
    onOpenImportPage: () -> Unit,
    onToggleWeekend: (Boolean) -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onReminderMinutesChange: (Int) -> Unit,
    onWeekNumberModeChange: (WeekNumberMode) -> Unit,
    onSemesterWeekStartDateChange: (LocalDate) -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onClearAllSchedules: () -> Unit,
    wearSyncMode: WearSyncMode,
    wearConnectionMessage: String,
    wearSyncMessage: String,
    wearSyncInProgress: Boolean,
    onWearSyncModeChange: (WearSyncMode) -> Unit,
    onRefreshWearStatus: () -> Unit,
    onManualWearSync: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Card {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(stringResource(R.string.settings_show_weekend_title), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.settings_show_weekend_desc), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = showWeekend, onCheckedChange = onToggleWeekend)
            }
        }
        Card {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(stringResource(R.string.settings_reminder_toggle_title), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.settings_reminder_toggle_desc), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = reminderEnabled, onCheckedChange = onToggleReminder)
            }
        }
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.settings_reminder_lead_title, reminderMinutes), fontWeight = FontWeight.SemiBold)
                Slider(
                    value = reminderMinutes.toFloat(),
                    onValueChange = { onReminderMinutesChange(it.toInt().coerceIn(5, 60)) },
                    valueRange = 5f..60f,
                )
            }
        }
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.settings_import_entry_title), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_import_entry_desc), style = MaterialTheme.typography.bodySmall)
                Button(onClick = onOpenImportPage) {
                    Text(stringResource(R.string.settings_import_entry_button))
                }
            }
        }
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.settings_backup_title), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_backup_desc), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onExportBackup) {
                        Text(stringResource(R.string.settings_backup_button))
                    }
                    Button(onClick = onRestoreBackup) {
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
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.settings_week_mode_title), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_week_mode_desc), style = MaterialTheme.typography.bodySmall)
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
                    Text(
                        text = stringResource(
                            R.string.settings_semester_start_date_value,
                            semesterWeekStartDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = {
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
                    ) {
                        Text(stringResource(R.string.settings_semester_start_date_pick_button))
                    }
                }
            }
        }
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.settings_wear_comm_title), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_wear_comm_desc), style = MaterialTheme.typography.bodySmall)
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
                Text(
                    text = stringResource(R.string.settings_wear_connection_label, wearConnectionMessage),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.settings_wear_sync_label, wearSyncMessage),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRefreshWearStatus) {
                        Text(stringResource(R.string.settings_wear_refresh_button))
                    }
                    Button(onClick = onManualWearSync, enabled = !wearSyncInProgress) {
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
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.settings_danger_title), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_danger_desc), style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = onClearAllSchedules,
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
internal fun AboutLayer(contentPadding: PaddingValues) {
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
        Text(
            stringResource(R.string.settings_about_page_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Card {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 18.dp),
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
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
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
                    modifier = Modifier.clickable {
                        runCatching { uriHandler.openUri("https://lyxyy.notion.site/classing") }
                    },
                )
                Text(
                    text = stringResource(R.string.settings_about_help_support),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        runCatching { uriHandler.openUri("https://lyxyy.notion.site/classinghelp") }
                    },
                )
                Text(
                    text = stringResource(R.string.settings_about_contact_us),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        runCatching { uriHandler.openUri("mailto:zeromostia@gmail.com") }
                    },
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
    Card {
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
                Text(lesson.startTime.format(clockFormatter), style = MaterialTheme.typography.labelLarge)
                Text(lesson.endTime.format(clockFormatter), style = MaterialTheme.typography.bodySmall)
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

