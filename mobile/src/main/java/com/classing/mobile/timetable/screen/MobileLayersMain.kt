package com.xtawa.classingtime.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.BorderStroke
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
internal fun WeekBoardLayer(
    contentPadding: PaddingValues,
    visibleDays: List<DayOfWeek>,
    lessonsByDay: Map<DayOfWeek, List<LessonUi>>,
    onLongPressLesson: (LessonUi) -> Unit,
) {
    val context = LocalContext.current
    val todayDay = LocalDate.now().dayOfWeek
    val prioritizedDays = remember(visibleDays, todayDay) {
        if (visibleDays.contains(todayDay)) {
            listOf(todayDay) + visibleDays.filterNot { it == todayDay }
        } else {
            visibleDays
        }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.ghost_title_schedule),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = stringResource(R.string.layer_dashboard),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.week_long_press_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(prioritizedDays) { day ->
            val lessons = lessonsByDay[day].orEmpty().sortedBy { it.startTime }
            val isEmpty = lessons.isEmpty()
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isEmpty) {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    },
                ),
                border = if (isEmpty) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                } else {
                    null
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.day_header_title, dayLabel(day, context), lessons.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isEmpty) {
                        Text(stringResource(R.string.no_classes), style = MaterialTheme.typography.bodySmall)
                    } else {
                        lessons.forEach { lesson ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(lesson.id) {
                                        detectTapGestures(
                                            onLongPress = { onLongPressLesson(lesson) },
                                        )
                                },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 9.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        modifier = Modifier.size(width = 70.dp, height = 40.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(
                                            text = lesson.startTime.format(clockFormatter),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            text = lesson.endTime.format(clockFormatter),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(width = 1.dp, height = 30.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    )
                                    {
                                        Text(
                                            text = lesson.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        if (!lesson.location.isNullOrBlank()) {
                                            Text(
                                                text = lesson.location,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
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
internal fun ImportLayer(
    contentPadding: PaddingValues,
    onBackToSettings: (() -> Unit)? = null,
    rawIcs: String,
    rawJson: String,
    parseMessage: String,
    warnings: List<String>,
    preview: List<CourseDraft>,
    jsonPreview: List<LessonUi>,
    hasPendingImport: Boolean,
    onRawChange: (String) -> Unit,
    onJsonRawChange: (String) -> Unit,
    onClearInput: () -> Unit,
    onParsePreview: () -> Unit,
    onParseJsonPreview: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelPreview: () -> Unit,
    onManualImport: (title: String, location: String, note: String, dayOfWeek: DayOfWeek, startRaw: String, endRaw: String) -> Boolean,
) {
    val context = LocalContext.current
    val untitled = stringResource(R.string.untitled_course)
    var manualTitle by remember { mutableStateOf("") }
    var manualLocation by remember { mutableStateOf("") }
    var manualNote by remember { mutableStateOf("") }
    var manualStart by remember { mutableStateOf("08:00") }
    var manualEnd by remember { mutableStateOf("09:40") }
    var manualDay by remember { mutableIntStateOf(DayOfWeek.MONDAY.value) }
    var showJsonPromptPage by remember { mutableStateOf(false) }
    val previewCollapseThreshold = 8
    var expandIcsPreview by remember(preview.size) { mutableStateOf(preview.size <= previewCollapseThreshold) }
    var expandJsonPreview by remember(jsonPreview.size) { mutableStateOf(jsonPreview.size <= previewCollapseThreshold) }

    if (showJsonPromptPage) {
        JsonPromptPage(
            contentPadding = contentPadding,
            onBack = { showJsonPromptPage = false },
        )
        return
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
        if (onBackToSettings != null) {
            SecondaryPageHeader(
                title = stringResource(R.string.import_page_title),
                onBack = onBackToSettings,
                backLabel = stringResource(R.string.settings_about_back_button),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.ghost_title_import),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = stringResource(R.string.import_page_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.import_page_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Text(
                text = stringResource(R.string.import_method_ics),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        OutlinedTextField(
            value = rawIcs,
            onValueChange = onRawChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            label = { Text(stringResource(R.string.import_input_label)) },
            maxLines = 14,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onParsePreview) { Text(stringResource(R.string.import_button_parse_preview)) }
            Button(onClick = onConfirmImport, enabled = hasPendingImport) { Text(stringResource(R.string.import_button_confirm)) }
            Button(onClick = onCancelPreview, enabled = hasPendingImport) { Text(stringResource(R.string.import_button_cancel_preview)) }
            Button(onClick = onClearInput) { Text(stringResource(R.string.import_button_clear)) }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.status_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(parseMessage, style = MaterialTheme.typography.bodySmall)
                if (warnings.isNotEmpty()) {
                    warnings.take(5).forEach {
                        Text(stringResource(R.string.status_warning_prefix, it), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (hasPendingImport) {
                    Text(
                        text = stringResource(R.string.import_pending_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Text(stringResource(R.string.import_preview_title, preview.size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        val collapsedIcs = preview.size > previewCollapseThreshold
        val shownIcsPreview = if (collapsedIcs && !expandIcsPreview) preview.take(previewCollapseThreshold) else preview
        shownIcsPreview.forEach { draft ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(draft.title.ifBlank { untitled }, fontWeight = FontWeight.SemiBold)
                    Text(
                        draft.location ?: stringResource(R.string.no_location),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        draft.recurrence ?: stringResource(R.string.one_time_schedule),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (collapsedIcs) {
            TextButton(onClick = { expandIcsPreview = !expandIcsPreview }) {
                Text(
                    if (expandIcsPreview) {
                        stringResource(R.string.preview_collapse_button)
                    } else {
                        stringResource(R.string.preview_expand_button, preview.size - previewCollapseThreshold)
                    },
                )
            }
        }

        HorizontalDivider()
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Text(
                text = stringResource(R.string.import_method_json),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(stringResource(R.string.json_import_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            text = stringResource(R.string.json_import_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = rawJson,
            onValueChange = onJsonRawChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            label = { Text(stringResource(R.string.json_input_label)) },
            maxLines = 12,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onParseJsonPreview) { Text(stringResource(R.string.json_button_parse_preview)) }
            Button(onClick = { showJsonPromptPage = true }) { Text(stringResource(R.string.json_button_prompt_page)) }
        }
        Text(stringResource(R.string.json_preview_title, jsonPreview.size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        val collapsedJson = jsonPreview.size > previewCollapseThreshold
        val shownJsonPreview = if (collapsedJson && !expandJsonPreview) jsonPreview.take(previewCollapseThreshold) else jsonPreview
        shownJsonPreview.forEach { lesson ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(lesson.title, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = formatLessonSummary(lesson, context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!lesson.location.isNullOrBlank()) {
                        Text(
                            text = lesson.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (collapsedJson) {
            TextButton(onClick = { expandJsonPreview = !expandJsonPreview }) {
                Text(
                    if (expandJsonPreview) {
                        stringResource(R.string.preview_collapse_button)
                    } else {
                        stringResource(R.string.preview_expand_button, jsonPreview.size - previewCollapseThreshold)
                    },
                )
            }
        }

        HorizontalDivider()
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Text(
                text = stringResource(R.string.import_method_manual),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(stringResource(R.string.manual_import_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            text = stringResource(R.string.manual_import_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = manualTitle,
            onValueChange = { manualTitle = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.manual_input_title_label)) },
            singleLine = true,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DayOfWeek.values()) { day ->
                FilterChip(
                    selected = manualDay == day.value,
                    onClick = { manualDay = day.value },
                    label = { Text(dayLabel(day, context)) },
                )
            }
        }
        OutlinedTextField(
            value = manualStart,
            onValueChange = { manualStart = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.manual_input_start_time_label)) },
            placeholder = { Text(stringResource(R.string.manual_input_start_time_placeholder)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = manualEnd,
            onValueChange = { manualEnd = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.manual_input_end_time_label)) },
            placeholder = { Text(stringResource(R.string.manual_input_end_time_placeholder)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = manualLocation,
            onValueChange = { manualLocation = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.manual_input_location_label)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = manualNote,
            onValueChange = { manualNote = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp),
            label = { Text(stringResource(R.string.manual_input_note_label)) },
            maxLines = 4,
        )
        Button(
            onClick = {
                val imported = onManualImport(
                    manualTitle,
                    manualLocation,
                    manualNote,
                    DayOfWeek.of(manualDay),
                    manualStart,
                    manualEnd,
                )
                if (imported) {
                    manualTitle = ""
                    manualLocation = ""
                    manualNote = ""
                }
            },
        ) {
            Text(stringResource(R.string.manual_import_button))
        }
    }
}

