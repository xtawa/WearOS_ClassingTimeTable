package com.classing.mobile.timetable.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.classing.mobile.timetable.R
import com.classing.mobile.timetable.data.MobilePrefsStore
import com.classing.mobile.timetable.data.MobileSettings
import com.classing.mobile.timetable.data.PersistedLesson
import com.classing.mobile.timetable.reminder.ReminderScheduler
import com.classing.shared.importer.CourseDraft
import com.classing.shared.importer.IcsImportParser
import com.classing.shared.importer.ImportResult
import com.classing.shared.importer.ScheduleImportAdapter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class MobileLayer {
    Dashboard,
    WeekBoard,
    Import,
    Settings,
}

private data class LessonUi(
    val id: String,
    val title: String,
    val location: String?,
    val note: String?,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
)

private data class ParseOutcome(
    val lessons: List<LessonUi>,
    val drafts: List<CourseDraft>,
    val message: String,
    val warnings: List<String>,
)

@Composable
fun MobileTimetableScreen() {
    val context = LocalContext.current
    val zoneId = remember { ZoneId.systemDefault() }
    val parser = remember { IcsImportParser() }
    val adapter = remember { ScheduleImportAdapter() }

    var initialized by remember { mutableStateOf(false) }
    var layerName by remember { mutableStateOf(MobileLayer.Dashboard.name) }
    var selectedDayValue by remember { mutableIntStateOf(LocalDate.now().dayOfWeek.value) }
    var showWeekend by remember { mutableStateOf(true) }
    var reminderEnabled by remember { mutableStateOf(false) }
    var reminderMinutes by remember { mutableIntStateOf(15) }
    var rawIcs by remember { mutableStateOf(sampleIcs()) }
    var parseMessage by remember { mutableStateOf(context.getString(R.string.initial_parse_message)) }
    var warnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var draftPreview by remember { mutableStateOf<List<CourseDraft>>(emptyList()) }
    var lessons by remember { mutableStateOf(seedLessons(context)) }

    fun persistSettings() {
        MobilePrefsStore.saveSettings(
            context,
            MobileSettings(
                showWeekend = showWeekend,
                reminderEnabled = reminderEnabled,
                reminderMinutes = reminderMinutes,
                rawIcs = rawIcs,
                parseMessage = parseMessage,
            ),
        )
    }

    fun persistLessons() {
        MobilePrefsStore.saveLessons(context, lessons.map { it.toPersistedLesson() })
    }

    fun syncReminderWork() {
        ReminderScheduler.sync(context, reminderEnabled)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        reminderEnabled = granted
        parseMessage = if (granted) {
            context.getString(R.string.message_reminder_enabled)
        } else {
            context.getString(R.string.message_notification_permission_missing)
        }
        persistSettings()
        syncReminderWork()
    }

    LaunchedEffect(Unit) {
        val settings = MobilePrefsStore.loadSettings(context)
        val storedLessons = MobilePrefsStore.loadLessons(context)

        showWeekend = settings.showWeekend
        reminderEnabled = settings.reminderEnabled
        reminderMinutes = settings.reminderMinutes
        rawIcs = settings.rawIcs.ifBlank { sampleIcs() }
        parseMessage = settings.parseMessage.ifBlank { context.getString(R.string.initial_parse_message) }

        if (storedLessons.isNotEmpty()) {
            lessons = storedLessons.map { it.toLessonUi() }
        } else {
            lessons = seedLessons(context)
            persistLessons()
        }

        initialized = true
        syncReminderWork()
    }

    if (!initialized) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.loading_message), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val layer = MobileLayer.entries.firstOrNull { it.name == layerName } ?: MobileLayer.Dashboard
    val selectedDay = DayOfWeek.of(selectedDayValue)
    val visibleDays = if (showWeekend) DayOfWeek.values().toList() else DayOfWeek.values().filter { it.value <= 5 }
    val lessonsByDay = lessons.groupBy { it.dayOfWeek }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.screen_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.screen_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(MobileLayer.entries) { item ->
                        FilterChip(
                            selected = item == layer,
                            onClick = { layerName = item.name },
                            label = { Text(stringResource(item.labelRes())) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        when (layer) {
            MobileLayer.Dashboard -> DashboardLayer(
                contentPadding = innerPadding,
                visibleDays = visibleDays,
                selectedDay = selectedDay,
                lessonsByDay = lessonsByDay,
                onSelectDay = { selectedDayValue = it.value },
            )

            MobileLayer.WeekBoard -> WeekBoardLayer(
                contentPadding = innerPadding,
                visibleDays = visibleDays,
                lessonsByDay = lessonsByDay,
            )

            MobileLayer.Import -> ImportLayer(
                contentPadding = innerPadding,
                rawIcs = rawIcs,
                parseMessage = parseMessage,
                warnings = warnings,
                preview = draftPreview,
                onRawChange = { rawIcs = it },
                onLoadSample = {
                    rawIcs = sampleIcs()
                    persistSettings()
                },
                onClearInput = {
                    rawIcs = ""
                    parseMessage = context.getString(R.string.message_input_cleared)
                    persistSettings()
                },
                onParse = {
                    val result = parseToLessons(rawIcs, parser, adapter, zoneId, context)
                    if (result.lessons.isNotEmpty()) {
                        lessons = result.lessons
                        persistLessons()
                    }
                    draftPreview = result.drafts
                    parseMessage = result.message
                    warnings = result.warnings
                    persistSettings()
                },
            )

            MobileLayer.Settings -> SettingsLayer(
                contentPadding = innerPadding,
                showWeekend = showWeekend,
                reminderEnabled = reminderEnabled,
                reminderMinutes = reminderMinutes,
                onToggleWeekend = {
                    showWeekend = it
                    persistSettings()
                },
                onToggleReminder = { enabled ->
                    if (!enabled) {
                        reminderEnabled = false
                        parseMessage = context.getString(R.string.message_reminder_disabled)
                        persistSettings()
                        syncReminderWork()
                    } else if (hasNotificationPermission(context)) {
                        reminderEnabled = true
                        parseMessage = context.getString(R.string.message_reminder_enabled)
                        persistSettings()
                        syncReminderWork()
                    } else {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onReminderMinutesChange = {
                    reminderMinutes = it
                    persistSettings()
                    if (reminderEnabled) syncReminderWork()
                },
            )
        }
    }
}

@Composable
private fun DashboardLayer(
    contentPadding: PaddingValues,
    visibleDays: List<DayOfWeek>,
    selectedDay: DayOfWeek,
    lessonsByDay: Map<DayOfWeek, List<LessonUi>>,
    onSelectDay: (DayOfWeek) -> Unit,
) {
    val context = LocalContext.current
    val todayLessons = lessonsByDay[selectedDay].orEmpty().sortedBy { it.startTime }
    val totalCount = lessonsByDay.values.sumOf { it.size }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.overview_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.overview_total_count, totalCount), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.overview_selected_day_count, dayLabel(selectedDay, context), todayLessons.size),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Text(stringResource(R.string.day_filter_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visibleDays) { day ->
                val count = lessonsByDay[day].orEmpty().size
                FilterChip(
                    selected = day == selectedDay,
                    onClick = { onSelectDay(day) },
                    label = { Text(stringResource(R.string.day_chip_label, dayLabel(day, context), count)) },
                )
            }
        }

        Text(stringResource(R.string.today_classes_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (todayLessons.isEmpty()) {
            Card {
                Text(
                    text = stringResource(R.string.no_classes_today),
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            todayLessons.forEach { lesson ->
                LessonCard(lesson = lesson)
            }
        }
    }
}

@Composable
private fun WeekBoardLayer(
    contentPadding: PaddingValues,
    visibleDays: List<DayOfWeek>,
    lessonsByDay: Map<DayOfWeek, List<LessonUi>>,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(visibleDays) { day ->
            val lessons = lessonsByDay[day].orEmpty().sortedBy { it.startTime }
            Card {
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
                    if (lessons.isEmpty()) {
                        Text(stringResource(R.string.no_classes), style = MaterialTheme.typography.bodySmall)
                    } else {
                        lessons.forEach { lesson ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.time_range_text,
                                        lesson.startTime.format(clockFormatter),
                                        lesson.endTime.format(clockFormatter),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = lesson.title,
                                    modifier = Modifier.padding(start = 12.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportLayer(
    contentPadding: PaddingValues,
    rawIcs: String,
    parseMessage: String,
    warnings: List<String>,
    preview: List<CourseDraft>,
    onRawChange: (String) -> Unit,
    onLoadSample: () -> Unit,
    onClearInput: () -> Unit,
    onParse: () -> Unit,
) {
    val untitled = stringResource(R.string.untitled_course)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.import_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = rawIcs,
            onValueChange = onRawChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            label = { Text(stringResource(R.string.import_input_label)) },
            maxLines = 14,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onParse) { Text(stringResource(R.string.import_button_parse)) }
            Button(onClick = onLoadSample) { Text(stringResource(R.string.import_button_sample)) }
            Button(onClick = onClearInput) { Text(stringResource(R.string.import_button_clear)) }
        }
        Card {
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
            }
        }
        Text(stringResource(R.string.import_preview_title, preview.size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        preview.take(20).forEach { draft ->
            Card {
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
    }
}

@Composable
private fun SettingsLayer(
    contentPadding: PaddingValues,
    showWeekend: Boolean,
    reminderEnabled: Boolean,
    reminderMinutes: Int,
    onToggleWeekend: (Boolean) -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onReminderMinutesChange: (Int) -> Unit,
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
    }
}

@Composable
private fun LessonCard(lesson: LessonUi) {
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

private fun parseToLessons(
    raw: String,
    parser: IcsImportParser,
    adapter: ScheduleImportAdapter,
    zoneId: ZoneId,
    context: Context,
): ParseOutcome {
    val untitled = context.getString(R.string.untitled_course)
    if (raw.isBlank()) {
        return ParseOutcome(
            lessons = emptyList(),
            drafts = emptyList(),
            message = context.getString(R.string.empty_input_message),
            warnings = emptyList(),
        )
    }

    val result = parser.parse(raw)
    val drafts = adapter.toDrafts(result)
    val lessons = drafts.mapIndexedNotNull { index, draft -> draft.toLessonUi(index, zoneId, untitled) }
        .sortedWith(compareBy<LessonUi> { it.dayOfWeek.value }.thenBy { it.startTime })

    return when (result) {
        is ImportResult.Success -> ParseOutcome(
            lessons = lessons,
            drafts = drafts,
            message = context.getString(R.string.parse_success_message, lessons.size),
            warnings = result.payload.warnings,
        )

        is ImportResult.PartialSuccess -> ParseOutcome(
            lessons = lessons,
            drafts = drafts,
            message = context.getString(R.string.parse_partial_message, lessons.size, result.droppedLines.size),
            warnings = result.payload.warnings + result.droppedLines.take(8),
        )

        is ImportResult.Failure -> ParseOutcome(
            lessons = emptyList(),
            drafts = emptyList(),
            message = context.getString(R.string.parse_failure_message, result.reason),
            warnings = emptyList(),
        )
    }
}

private fun LessonUi.toPersistedLesson(): PersistedLesson {
    return PersistedLesson(
        id = id,
        title = title,
        location = location,
        note = note,
        dayOfWeek = dayOfWeek.value,
        startMinute = startTime.hour * 60 + startTime.minute,
        endMinute = endTime.hour * 60 + endTime.minute,
    )
}

private fun PersistedLesson.toLessonUi(): LessonUi {
    val safeEnd = if (endMinute > startMinute) endMinute else (startMinute + 90).coerceAtMost(23 * 60 + 59)
    return LessonUi(
        id = id,
        title = title,
        location = location,
        note = note,
        dayOfWeek = DayOfWeek.of(dayOfWeek.coerceIn(1, 7)),
        startTime = LocalTime.of(startMinute / 60, startMinute % 60),
        endTime = LocalTime.of(safeEnd / 60, safeEnd % 60),
    )
}

private fun CourseDraft.toLessonUi(index: Int, zoneId: ZoneId, untitled: String): LessonUi? {
    val startLocal = start?.atZone(zoneId)?.toLocalDateTime() ?: return null
    val endLocal = end?.atZone(zoneId)?.toLocalDateTime() ?: startLocal.plusMinutes(100)
    return LessonUi(
        id = "$index-${title.ifBlank { "class" }}-${startLocal.toLocalDate()}",
        title = title.ifBlank { untitled },
        location = location,
        note = note,
        dayOfWeek = startLocal.dayOfWeek,
        startTime = startLocal.toLocalTime(),
        endTime = endLocal.toLocalTime(),
    )
}

private fun seedLessons(context: Context): List<LessonUi> {
    return listOf(
        LessonUi("1", context.getString(R.string.seed_course_1), "A-301", context.getString(R.string.seed_note_lecture), DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 40)),
        LessonUi("2", context.getString(R.string.seed_course_2), "B-210", context.getString(R.string.seed_note_lab), DayOfWeek.MONDAY, LocalTime.of(14, 0), LocalTime.of(15, 40)),
        LessonUi("3", context.getString(R.string.seed_course_3), "A-518", null, DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(11, 40)),
        LessonUi("4", context.getString(R.string.seed_course_4), "B-202", null, DayOfWeek.WEDNESDAY, LocalTime.of(8, 0), LocalTime.of(9, 40)),
        LessonUi("5", context.getString(R.string.seed_course_5), "A-402", context.getString(R.string.seed_note_chapter_6), DayOfWeek.FRIDAY, LocalTime.of(13, 30), LocalTime.of(15, 10)),
    )
}

private fun sampleIcs(): String {
    return """
BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//Classing//Schedule Demo//EN
BEGIN:VEVENT
SUMMARY:Software Architecture
DTSTART:20260316T010000Z
DTEND:20260316T024000Z
LOCATION:A-301
RRULE:FREQ=WEEKLY;BYDAY=MO
END:VEVENT
BEGIN:VEVENT
SUMMARY:Computer Networks
DTSTART:20260317T020000Z
DTEND:20260317T034000Z
LOCATION:A-518
RRULE:FREQ=WEEKLY;BYDAY=TU
END:VEVENT
BEGIN:VEVENT
SUMMARY:Operating Systems
DTSTART:20260320T053000Z
DTEND:20260320T071000Z
LOCATION:A-402
RRULE:FREQ=WEEKLY;BYDAY=FR
END:VEVENT
END:VCALENDAR
""".trimIndent()
}

private fun dayLabel(dayOfWeek: DayOfWeek, context: Context): String {
    val resId = when (dayOfWeek) {
        DayOfWeek.MONDAY -> R.string.weekday_mon
        DayOfWeek.TUESDAY -> R.string.weekday_tue
        DayOfWeek.WEDNESDAY -> R.string.weekday_wed
        DayOfWeek.THURSDAY -> R.string.weekday_thu
        DayOfWeek.FRIDAY -> R.string.weekday_fri
        DayOfWeek.SATURDAY -> R.string.weekday_sat
        DayOfWeek.SUNDAY -> R.string.weekday_sun
    }
    return context.getString(resId)
}

private fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

private fun MobileLayer.labelRes(): Int {
    return when (this) {
        MobileLayer.Dashboard -> R.string.layer_dashboard
        MobileLayer.WeekBoard -> R.string.layer_week_view
        MobileLayer.Import -> R.string.layer_import
        MobileLayer.Settings -> R.string.layer_settings
    }
}

private val clockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun MobileTimetablePreview() {
    MobileTimetableScreen()
}
