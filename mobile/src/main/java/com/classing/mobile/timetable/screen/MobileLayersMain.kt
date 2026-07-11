package com.xtawa.classingtime.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
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
    lessonsForDate: (LocalDate) -> List<LessonUi>,
    hasSchedule: Boolean,
    onOpenCalendar: () -> Unit,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                FilterChip(
                    selected = false,
                    onClick = onOpenCalendar,
                    label = { Text(text = stringResource(R.string.schedule_open_calendar)) },
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
private fun MobileNextLessonHeroCard(
    nextLesson: UpcomingLessonForBoard?,
    hasSchedule: Boolean,
    now: LocalDateTime,
) {
    val context = LocalContext.current
    val countdown = if (nextLesson == null) {
        ""
    } else if (!now.isBefore(nextLesson.startAt) && now.isBefore(nextLesson.endAt)) {
        stringResource(R.string.schedule_next_lesson_countdown_in_progress)
    } else {
        val minutes = java.time.Duration.between(now, nextLesson.startAt).toMinutes().coerceAtLeast(0L)
        when {
            minutes <= 0L -> stringResource(R.string.schedule_next_lesson_countdown_soon)
            minutes >= 60L -> {
                val h = minutes / 60L
                val m = minutes % 60L
                stringResource(R.string.schedule_next_lesson_countdown_in_hours_minutes, h, m)
            }

            else -> stringResource(R.string.schedule_next_lesson_countdown_in_minutes, minutes)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.schedule_next_lesson_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (nextLesson == null) {
                Text(
                    text = if (hasSchedule) {
                        stringResource(R.string.schedule_next_lesson_empty)
                    } else {
                        stringResource(R.string.schedule_next_lesson_no_data)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val dayText = dayLabel(nextLesson.startAt.dayOfWeek, context)
                val timeRange = stringResource(
                    R.string.time_range_text,
                    nextLesson.lesson.startTime.format(clockFormatter),
                    nextLesson.lesson.endTime.format(clockFormatter),
                )
                Text(
                    text = nextLesson.lesson.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                )
                Text(
                    text = "$dayText · $timeRange",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (countdown.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(999.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = countdown,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

internal data class UpcomingLessonForBoard(
    val lesson: LessonUi,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
)

internal fun resolveNextLessonForBoard(
    lessonsForDate: (LocalDate) -> List<LessonUi>,
    now: LocalDateTime,
): UpcomingLessonForBoard? {
    val candidates = (0..7).asSequence().flatMap { offset ->
        val date = now.toLocalDate().plusDays(offset.toLong())
        lessonsForDate(date).asSequence().map { lesson ->
            val startAt = LocalDateTime.of(date, lesson.startTime)
            val endAt = LocalDateTime.of(date, lesson.endTime)
            UpcomingLessonForBoard(lesson = lesson, startAt = startAt, endAt = endAt)
        }
    }

    return candidates
        .filter { it.endAt.isAfter(now) }
        .sortedBy { it.startAt }
        .firstOrNull()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ImportLayer(
    contentPadding: PaddingValues,
    onBackToSettings: (() -> Unit)? = null,
    showJsonPromptPage: Boolean,
    onBackFromJsonPromptPage: () -> Unit,
    onOpenJsonPromptPage: () -> Unit,
    initialFocusMethod: ImportFocusMethod? = null,
    onInitialFocusConsumed: ((ImportFocusMethod) -> Unit)? = null,
    rawIcs: String,
    rawJson: String,
    parseMessage: String,
    warnings: List<String>,
    preview: List<CourseDraft>,
    jsonPreview: List<LessonUi>,
    hasPendingImport: Boolean,
    importItemStates: List<ImportItemState>,
    importPreviewSummary: ImportPreviewSummary?,
    onRawChange: (String) -> Unit,
    onJsonRawChange: (String) -> Unit,
    onClearInput: () -> Unit,
    onParsePreview: () -> Unit,
    onParseJsonPreview: () -> Unit,
    onConfirmImport: () -> Unit,
    jsonImportMode: JsonImportMode,
    onJsonImportModeChange: (JsonImportMode) -> Unit,
    onConfirmJsonImport: () -> Unit,
    onConfirmSelectiveImport: (List<LessonUi>) -> Unit,
    onConfirmSelectiveJsonImport: (List<LessonUi>) -> Unit,
    onCancelPreview: () -> Unit,
    onToggleImportItem: (Int) -> Unit,
    onIcsFileSelected: (android.net.Uri) -> Unit,
    onJsonFileSelected: (android.net.Uri) -> Unit,
    onManualImport: (
        title: String,
        teacher: String,
        location: String,
        note: String,
        dayOfWeek: DayOfWeek,
        startRaw: String,
        endRaw: String,
        startWeekRaw: String,
        endWeekRaw: String,
        weekParity: LessonWeekParity,
    ) -> Boolean,
) {
    val context = LocalContext.current
    val untitled = stringResource(R.string.untitled_course)
    var manualTitle by remember { mutableStateOf("") }
    var manualTeacher by remember { mutableStateOf("") }
    var manualLocation by remember { mutableStateOf("") }
    var manualNote by remember { mutableStateOf("") }
    var manualStart by remember { mutableStateOf("08:00") }
    var manualEnd by remember { mutableStateOf("09:40") }
    var manualStartWeek by remember { mutableStateOf(DEFAULT_START_WEEK.toString()) }
    var manualEndWeek by remember { mutableStateOf(DEFAULT_END_WEEK.toString()) }
    var manualWeekParity by remember { mutableStateOf(LessonWeekParity.ALL) }
    var manualDay by remember { mutableIntStateOf(DayOfWeek.MONDAY.value) }
    val previewCollapseThreshold = 8
    var expandIcsPreview by remember(preview.size) { mutableStateOf(preview.size <= previewCollapseThreshold) }
    var expandJsonPreview by remember(jsonPreview.size) { mutableStateOf(jsonPreview.size <= previewCollapseThreshold) }
    var expandedImportMethod by remember { mutableStateOf<ImportFocusMethod?>(initialFocusMethod ?: ImportFocusMethod.ICS) }
    val icsSectionRequester = remember { BringIntoViewRequester() }
    val jsonSectionRequester = remember { BringIntoViewRequester() }
    val manualSectionRequester = remember { BringIntoViewRequester() }
    val hasPendingJsonImport = hasPendingImport && jsonPreview.isNotEmpty()

    val icsFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onIcsFileSelected(uri)
    }
    val jsonFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onJsonFileSelected(uri)
    }

    LaunchedEffect(initialFocusMethod, showJsonPromptPage) {
        val focusMethod = initialFocusMethod ?: return@LaunchedEffect
        if (showJsonPromptPage) return@LaunchedEffect
        expandedImportMethod = focusMethod
        when (focusMethod) {
            ImportFocusMethod.ICS -> icsSectionRequester.bringIntoView()
            ImportFocusMethod.JSON -> jsonSectionRequester.bringIntoView()
            ImportFocusMethod.MANUAL -> manualSectionRequester.bringIntoView()
        }
        onInitialFocusConsumed?.invoke(focusMethod)
    }

    if (showJsonPromptPage) {
        JsonPromptPage(
            contentPadding = contentPadding,
            onBack = onBackFromJsonPromptPage,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(icsSectionRequester),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    expandedImportMethod = if (expandedImportMethod == ImportFocusMethod.ICS) null else ImportFocusMethod.ICS
                },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.import_method_ics),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(stringResource(if (expandedImportMethod == ImportFocusMethod.ICS) R.string.import_section_collapse else R.string.import_section_expand))
                }
            }
            if (expandedImportMethod == ImportFocusMethod.ICS) {
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
                Button(onClick = {
                    icsFilePicker.launch(arrayOf("text/calendar", "application/ics", "*/*"))
                }) { Text(stringResource(R.string.import_button_select_ics_file)) }
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
            if (importPreviewSummary != null && importItemStates.isNotEmpty()) {
                ImportPreviewSummaryCard(
                    summary = importPreviewSummary,
                    itemStates = importItemStates,
                )
            }
            Text(stringResource(R.string.import_preview_title, preview.size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            val collapsedIcs = preview.size > previewCollapseThreshold
            val shownIcsPreview = if (collapsedIcs && !expandIcsPreview) preview.take(previewCollapseThreshold) else preview
            shownIcsPreview.forEachIndexed { index, draft ->
                val itemState = importItemStates.getOrNull(index)
                ImportPreviewDraftCard(
                    draft = draft,
                    untitled = untitled,
                    itemState = itemState,
                    index = index,
                    onToggle = onToggleImportItem,
                )
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
            if (hasPendingImport && importItemStates.isNotEmpty()) {
                val includedCount = importItemStates.count { it.included }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            val included = importItemStates.filter { it.included }.map { it.lesson }
                            if (included.isNotEmpty()) onConfirmSelectiveImport(included)
                        },
                        enabled = includedCount > 0,
                    ) {
                        Text(stringResource(R.string.import_selective_confirm_button, includedCount))
                    }
                    Button(onClick = onCancelPreview) {
                        Text(stringResource(R.string.import_selective_cancel_button))
                    }
                }
            }
            }
        }

        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(jsonSectionRequester),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    expandedImportMethod = if (expandedImportMethod == ImportFocusMethod.JSON) null else ImportFocusMethod.JSON
                },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.import_method_json),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(stringResource(if (expandedImportMethod == ImportFocusMethod.JSON) R.string.import_section_collapse else R.string.import_section_expand))
                }
            }
            if (expandedImportMethod == ImportFocusMethod.JSON) {
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
                Button(onClick = {
                    jsonFilePicker.launch(arrayOf("application/json", "text/plain", "*/*"))
                }) { Text(stringResource(R.string.import_button_select_json_file)) }
                Button(onClick = onOpenJsonPromptPage) { Text(stringResource(R.string.json_button_prompt_page)) }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.json_import_mode_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = jsonImportMode == JsonImportMode.REPLACE,
                            onClick = { onJsonImportModeChange(JsonImportMode.REPLACE) },
                            label = { Text(stringResource(R.string.json_import_mode_replace)) },
                        )
                        FilterChip(
                            selected = jsonImportMode == JsonImportMode.APPEND,
                            onClick = { onJsonImportModeChange(JsonImportMode.APPEND) },
                            label = { Text(stringResource(R.string.json_import_mode_append)) },
                        )
                    }
                    Text(
                        text = stringResource(
                            if (jsonImportMode == JsonImportMode.REPLACE) {
                                R.string.json_import_mode_replace_helper
                            } else {
                                R.string.json_import_mode_append_helper
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onConfirmJsonImport, enabled = hasPendingJsonImport) {
                    Text(
                        stringResource(
                            if (jsonImportMode == JsonImportMode.REPLACE) {
                                R.string.json_import_confirm_replace_button
                            } else {
                                R.string.json_import_confirm_append_button
                            },
                        ),
                    )
                }
                Button(onClick = onCancelPreview, enabled = hasPendingJsonImport) { Text(stringResource(R.string.import_button_cancel_preview)) }
            }
            if (importPreviewSummary != null && importItemStates.isNotEmpty() && jsonPreview.isNotEmpty()) {
                ImportPreviewSummaryCard(
                    summary = importPreviewSummary,
                    itemStates = importItemStates,
                )
            }
            Text(stringResource(R.string.json_preview_title, jsonPreview.size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            val collapsedJson = jsonPreview.size > previewCollapseThreshold
            val shownJsonPreview = if (collapsedJson && !expandJsonPreview) jsonPreview.take(previewCollapseThreshold) else jsonPreview
            shownJsonPreview.forEachIndexed { index, lesson ->
                val itemState = importItemStates.getOrNull(index)
                ImportPreviewLessonCard(
                    lesson = lesson,
                    context = context,
                    itemState = itemState,
                    index = index,
                    onToggle = onToggleImportItem,
                )
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
            if (hasPendingJsonImport && importItemStates.isNotEmpty()) {
                val includedCount = importItemStates.count { it.included }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            val included = importItemStates.filter { it.included }.map { it.lesson }
                            if (included.isNotEmpty()) onConfirmSelectiveJsonImport(included)
                        },
                        enabled = includedCount > 0,
                    ) {
                        Text(
                            stringResource(
                                if (jsonImportMode == JsonImportMode.REPLACE) {
                                    R.string.json_import_selective_replace_button
                                } else {
                                    R.string.json_import_selective_append_button
                                },
                                includedCount,
                            ),
                        )
                    }
                    Button(onClick = onCancelPreview) {
                        Text(stringResource(R.string.import_selective_cancel_button))
                    }
                }
            }
            }
        }

        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(manualSectionRequester),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    expandedImportMethod = if (expandedImportMethod == ImportFocusMethod.MANUAL) null else ImportFocusMethod.MANUAL
                },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.import_method_manual),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(stringResource(if (expandedImportMethod == ImportFocusMethod.MANUAL) R.string.import_section_collapse else R.string.import_section_expand))
                }
            }
            if (expandedImportMethod == ImportFocusMethod.MANUAL) {
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
            OutlinedTextField(
                value = manualTeacher,
                onValueChange = { manualTeacher = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.manual_input_teacher_label)) },
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = manualStartWeek,
                    onValueChange = { manualStartWeek = it },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.manual_input_start_week_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = manualEndWeek,
                    onValueChange = { manualEndWeek = it },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.manual_input_end_week_label)) },
                    singleLine = true,
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LessonWeekParity.entries) { parity ->
                    val labelRes = when (parity) {
                        LessonWeekParity.ALL -> R.string.week_parity_all
                        LessonWeekParity.ODD -> R.string.week_parity_odd
                        LessonWeekParity.EVEN -> R.string.week_parity_even
                    }
                    FilterChip(
                        selected = manualWeekParity == parity,
                        onClick = { manualWeekParity = parity },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
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
                        manualTeacher,
                        manualLocation,
                        manualNote,
                        DayOfWeek.of(manualDay),
                        manualStart,
                        manualEnd,
                        manualStartWeek,
                        manualEndWeek,
                        manualWeekParity,
                    )
                    if (imported) {
                        manualTitle = ""
                        manualTeacher = ""
                        manualLocation = ""
                        manualNote = ""
                    }
                },
            ) {
                Text(stringResource(R.string.manual_import_button))
            }
            }
        }
    }
}

@Composable
private fun ImportPreviewSummaryCard(
    summary: ImportPreviewSummary,
    itemStates: List<ImportItemState>,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.import_preview_summary_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.import_preview_summary_valid, summary.validCount),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (summary.conflictCount > 0) {
                    Text(
                        text = stringResource(R.string.import_preview_summary_conflict, summary.conflictCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (summary.anomalyCount > 0) {
                    Text(
                        text = stringResource(R.string.import_preview_summary_skipped, summary.anomalyCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            if (summary.conflictCount > 0 || summary.anomalyCount > 0) {
                Text(
                    text = stringResource(
                        R.string.import_selective_conflict_summary,
                        summary.conflictCount,
                        summary.total,
                        summary.anomalyCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (itemStates.all { !it.included }) {
                Text(
                    text = stringResource(R.string.import_preview_no_valid_items),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ImportPreviewDraftCard(
    draft: CourseDraft,
    untitled: String,
    itemState: ImportItemState?,
    index: Int,
    onToggle: (Int) -> Unit,
) {
    val hasConflict = itemState?.hasConflict == true
    val hasAnomaly = itemState?.anomalies?.isNotEmpty() == true
    val included = itemState?.included ?: true
    val borderColor = when {
        hasConflict -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
        hasAnomaly -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
        else -> null
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (!included) {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        ),
        border = borderColor?.let { BorderStroke(1.dp, it) },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    draft.title.ifBlank { untitled },
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (itemState != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (hasConflict) {
                            Text(
                                text = stringResource(R.string.import_preview_item_conflict_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                        RoundedCornerShape(4.dp),
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        if (hasAnomaly) {
                            Text(
                                text = stringResource(R.string.import_preview_item_anomaly_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                                        RoundedCornerShape(4.dp),
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        TextButton(
                            onClick = { onToggle(index) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Text(
                                text = if (included) stringResource(R.string.import_preview_item_skip)
                                else stringResource(R.string.import_preview_item_include),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
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
            if (hasAnomaly && itemState != null) {
                itemState.anomalies.take(3).forEach { anomaly ->
                    Text(
                        text = anomaly,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            if (hasConflict && itemState != null) {
                itemState.conflictWithExisting.take(2).forEach { existing ->
                    Text(
                        text = stringResource(
                            R.string.import_selective_conflict_detail,
                            draft.title.ifBlank { untitled },
                            existing.title,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportPreviewLessonCard(
    lesson: LessonUi,
    context: Context,
    itemState: ImportItemState?,
    index: Int,
    onToggle: (Int) -> Unit,
) {
    val hasConflict = itemState?.hasConflict == true
    val hasAnomaly = itemState?.anomalies?.isNotEmpty() == true
    val included = itemState?.included ?: true
    val borderColor = when {
        hasConflict -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
        hasAnomaly -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
        else -> null
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (!included) {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        ),
        border = borderColor?.let { BorderStroke(1.dp, it) },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    lesson.title,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (itemState != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (hasConflict) {
                            Text(
                                text = stringResource(R.string.import_preview_item_conflict_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                        RoundedCornerShape(4.dp),
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        if (hasAnomaly) {
                            Text(
                                text = stringResource(R.string.import_preview_item_anomaly_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                                        RoundedCornerShape(4.dp),
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        TextButton(
                            onClick = { onToggle(index) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Text(
                                text = if (included) stringResource(R.string.import_preview_item_skip)
                                else stringResource(R.string.import_preview_item_include),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
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
            if (hasAnomaly && itemState != null) {
                itemState.anomalies.take(3).forEach { anomaly ->
                    Text(
                        text = anomaly,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            if (hasConflict && itemState != null) {
                itemState.conflictWithExisting.take(2).forEach { existing ->
                    Text(
                        text = stringResource(
                            R.string.import_selective_conflict_detail,
                            lesson.title,
                            existing.title,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
