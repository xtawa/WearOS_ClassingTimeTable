package com.xtawa.classingtime.screen

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
internal fun LessonEditDialog(
    lesson: LessonUi,
    onDismiss: () -> Unit,
    onSave: (LessonUi, ChangeScope) -> Unit,
    onDelete: (ChangeScope) -> Unit,
) {
    val context = LocalContext.current
    var title by remember(lesson.id) { mutableStateOf(lesson.title) }
    var location by remember(lesson.id) { mutableStateOf(lesson.location.orEmpty()) }
    var note by remember(lesson.id) { mutableStateOf(lesson.note.orEmpty()) }
    var dayValue by remember(lesson.id) { mutableIntStateOf(lesson.dayOfWeek.value) }
    var startRaw by remember(lesson.id) { mutableStateOf(lesson.startTime.format(clockFormatter)) }
    var endRaw by remember(lesson.id) { mutableStateOf(lesson.endTime.format(clockFormatter)) }
    var scope by remember(lesson.id) { mutableStateOf(ChangeScope.Persistent) }
    var validationMessage by remember(lesson.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lesson_edit_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.lesson_edit_scope_title),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = scope == ChangeScope.Persistent,
                        onClick = { scope = ChangeScope.Persistent },
                        label = { Text(stringResource(R.string.lesson_edit_scope_persistent)) },
                    )
                    FilterChip(
                        selected = scope == ChangeScope.Temporary,
                        onClick = { scope = ChangeScope.Temporary },
                        label = { Text(stringResource(R.string.lesson_edit_scope_temporary)) },
                    )
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.manual_input_title_label)) },
                    singleLine = true,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DayOfWeek.values()) { day ->
                        FilterChip(
                            selected = dayValue == day.value,
                            onClick = { dayValue = day.value },
                            label = { Text(dayLabel(day, context)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = startRaw,
                    onValueChange = { startRaw = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.manual_input_start_time_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = endRaw,
                    onValueChange = { endRaw = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.manual_input_end_time_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.manual_input_location_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.manual_input_note_label)) },
                    maxLines = 4,
                )
                val message = validationMessage
                if (!message.isNullOrBlank()) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val safeTitle = title.trim()
                    val start = parseManualTime(startRaw)
                    val end = parseManualTime(endRaw)
                    when {
                        safeTitle.isBlank() -> {
                            validationMessage = context.getString(R.string.manual_import_title_required_message)
                        }

                        start == null || end == null -> {
                            validationMessage = context.getString(R.string.manual_import_time_format_message)
                        }

                        !end.isAfter(start) -> {
                            validationMessage = context.getString(R.string.manual_import_time_order_message)
                        }

                        else -> {
                            onSave(
                                lesson.copy(
                                    title = safeTitle,
                                    location = location.trim().ifBlank { null },
                                    note = note.trim().ifBlank { null },
                                    dayOfWeek = DayOfWeek.of(dayValue),
                                    startTime = start,
                                    endTime = end,
                                ),
                                scope,
                            )
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.lesson_edit_save_button))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { onDelete(scope) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.lesson_edit_delete_button))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.lesson_edit_cancel_button))
                }
            }
        },
    )
}

