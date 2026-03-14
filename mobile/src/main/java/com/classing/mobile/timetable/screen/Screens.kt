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
import java.net.HttpURLConnection
import java.net.URL
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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private enum class MobileLayer {
    Dashboard,
    WeekBoard,
    Import,
    Settings,
    About,
}

private enum class ChangeScope {
    Temporary,
    Persistent,
}

private enum class WearSyncMode {
    WEARABLE_API,
    WEAROS_APP,
}

private const val CLASSING_NOTICE_URL = "https://api.rskiller.zcwww.cc/GetClassingNotice"
private const val CLASSING_NOTICE_KEY = "A8bC9dE0fG1hI2jK"

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

private data class JsonParseOutcome(
    val lessons: List<LessonUi>,
    val message: String,
    val warnings: List<String>,
)

private data class LessonConflict(
    val first: LessonUi,
    val second: LessonUi,
)

private data class WearOsCompanionInfo(
    val packageName: String,
    val versionName: String,
    val isChinaOrLe: Boolean,
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
    var rawIcs by remember { mutableStateOf("") }
    var rawJson by remember { mutableStateOf("") }
    var parseMessage by remember { mutableStateOf(context.getString(R.string.initial_parse_message)) }
    var warnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var draftPreview by remember { mutableStateOf<List<CourseDraft>>(emptyList()) }
    var jsonPreview by remember { mutableStateOf<List<LessonUi>>(emptyList()) }
    var pendingImportLessons by remember { mutableStateOf<List<LessonUi>>(emptyList()) }
    var pendingImportConflicts by remember { mutableStateOf<List<LessonConflict>>(emptyList()) }
    var showImportConflictDialog by remember { mutableStateOf(false) }
    var pendingManualLesson by remember { mutableStateOf<LessonUi?>(null) }
    var pendingManualConflicts by remember { mutableStateOf<List<LessonUi>>(emptyList()) }
    var showManualConflictDialog by remember { mutableStateOf(false) }
    var editingLesson by remember { mutableStateOf<LessonUi?>(null) }
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    var pendingRestoreLessons by remember { mutableStateOf<List<LessonUi>>(emptyList()) }
    var pendingRestoreWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showClearAllConfirmDialog by remember { mutableStateOf(false) }
    var lessons by remember { mutableStateOf(emptyList<LessonUi>()) }
    var wearConnectedCount by remember { mutableIntStateOf(0) }
    var wearConnectionMessage by remember { mutableStateOf(context.getString(R.string.wear_connection_checking)) }
    var wearSyncMessage by remember {
        mutableStateOf(
            WearSyncAckStore.load(context)?.let { formatWearSyncAckMessage(context, it) }
                ?: context.getString(R.string.wear_sync_never),
        )
    }
    var latestWearAckAtMillis by remember { mutableStateOf(WearSyncAckStore.load(context)?.syncedAtMillis ?: 0L) }
    var wearSyncInProgress by remember { mutableStateOf(false) }
    var wearSyncMode by remember { mutableStateOf(WearSyncMode.WEARABLE_API) }
    val coroutineScope = rememberCoroutineScope()

    fun persistSettings() {
        MobilePrefsStore.saveSettings(
            context,
            MobileSettings(
                showWeekend = showWeekend,
                reminderEnabled = reminderEnabled,
                reminderMinutes = reminderMinutes,
                rawIcs = rawIcs,
                parseMessage = parseMessage,
                wearSyncMode = wearSyncMode.name,
            ),
        )
    }

    fun persistLessons() {
        MobilePrefsStore.saveLessons(context, lessons.map { it.toPersistedLesson() })
    }

    fun applyImportedLessons(importLessons: List<LessonUi>) {
        lessons = importLessons.sortedWith(compareBy<LessonUi> { it.dayOfWeek.value }.thenBy { it.startTime })
        persistLessons()
    }

    fun appendManualLesson(newLesson: LessonUi) {
        lessons = (lessons + newLesson).sortedWith(compareBy<LessonUi> { it.dayOfWeek.value }.thenBy { it.startTime })
        persistLessons()
    }

    fun applyLessonEdit(updatedLesson: LessonUi, scope: ChangeScope) {
        lessons = lessons.map { if (it.id == updatedLesson.id) updatedLesson else it }
            .sortedWith(compareBy<LessonUi> { it.dayOfWeek.value }.thenBy { it.startTime })
        if (scope == ChangeScope.Persistent) persistLessons()
        parseMessage = if (scope == ChangeScope.Persistent) {
            context.getString(R.string.lesson_edit_saved_persistent_message, updatedLesson.title)
        } else {
            context.getString(R.string.lesson_edit_saved_temporary_message, updatedLesson.title)
        }
        persistSettings()
    }

    fun removeLesson(targetLesson: LessonUi, scope: ChangeScope) {
        lessons = lessons.filterNot { it.id == targetLesson.id }
        if (scope == ChangeScope.Persistent) persistLessons()
        parseMessage = if (scope == ChangeScope.Persistent) {
            context.getString(R.string.lesson_delete_persistent_message, targetLesson.title)
        } else {
            context.getString(R.string.lesson_delete_temporary_message, targetLesson.title)
        }
        persistSettings()
    }

    fun syncReminderWork() {
        ReminderScheduler.sync(context, reminderEnabled)
    }

    fun refreshWearSyncAckStatus(force: Boolean = false) {
        val ack = WearSyncAckStore.load(context) ?: return
        if (force || ack.syncedAtMillis > latestWearAckAtMillis) {
            latestWearAckAtMillis = ack.syncedAtMillis
            wearSyncMessage = formatWearSyncAckMessage(context, ack)
        }
    }

    suspend fun refreshWearConnectionStatus() {
        when (wearSyncMode) {
            WearSyncMode.WEARABLE_API -> {
                val result = fetchConnectedWearNodeCount(context)
                wearConnectedCount = result.getOrDefault(0)
                wearConnectionMessage = if (result.isSuccess) {
                    if (wearConnectedCount > 0) {
                        context.getString(R.string.wear_connection_connected, wearConnectedCount)
                    } else {
                        context.getString(R.string.wear_connection_disconnected)
                    }
                } else {
                    context.getString(R.string.wear_connection_error, result.exceptionOrNull()?.message ?: "unknown")
                }
            }

            WearSyncMode.WEAROS_APP -> {
                val companion = findWearOsCompanionInfo(context)
                val result = fetchConnectedWearNodeCount(context)
                val nodeCount = result.getOrDefault(0)
                wearConnectedCount = nodeCount
                wearConnectionMessage = if (companion == null) {
                    context.getString(R.string.wearos_app_unavailable)
                } else if (nodeCount > 0) {
                    context.getString(
                        R.string.wearos_app_available_connected,
                        companion.toDisplayLabel(),
                        nodeCount,
                    )
                } else {
                    context.getString(R.string.wearos_app_available, companion.toDisplayLabel())
                }
            }
        }
        refreshWearSyncAckStatus()
    }

    suspend fun runManualWearSync() {
        wearSyncInProgress = true
        val startedAtMillis = System.currentTimeMillis()
        when (wearSyncMode) {
            WearSyncMode.WEARABLE_API -> {
                val result = syncLessonsToWear(
                    context = context,
                    lessons = lessons,
                    zoneId = zoneId,
                    source = WearDataLayerContracts.SOURCE_WEARABLE_API,
                    allowDisconnectedQueue = false,
                )
                wearSyncMessage = handleStartedWearSync(
                    context = context,
                    result = result,
                    startedAtMillis = startedAtMillis,
                    latestAckUpdater = { ack ->
                        latestWearAckAtMillis = ack.syncedAtMillis
                        wearSyncMessage = formatWearSyncAckMessage(context, ack)
                    },
                )
            }

            WearSyncMode.WEAROS_APP -> {
                val companion = findWearOsCompanionInfo(context)
                    ?: run {
                        wearSyncMessage = context.getString(R.string.wear_sync_via_wearos_app_failed, "WearOS app not installed")
                        wearSyncInProgress = false
                        refreshWearConnectionStatus()
                        return
                    }
                val result = syncLessonsViaWearOsApp(context, lessons, zoneId)
                wearSyncMessage = handleStartedWearSync(
                    context = context,
                    result = result,
                    startedAtMillis = startedAtMillis,
                    queuedMessage = context.getString(
                        R.string.wear_sync_queued_via_wearos_app,
                        companion.toDisplayLabel(),
                    ),
                    latestAckUpdater = { ack ->
                        latestWearAckAtMillis = ack.syncedAtMillis
                        wearSyncMessage = formatWearSyncAckMessage(context, ack)
                    },
                )
            }
        }
        wearSyncInProgress = false
        refreshWearConnectionStatus()
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

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val exportJson = pendingExportJson
        pendingExportJson = null
        if (uri == null || exportJson.isNullOrBlank()) {
            parseMessage = context.getString(R.string.backup_export_canceled_message)
            persistSettings()
            return@rememberLauncherForActivityResult
        }

        val saved = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(exportJson.toByteArray(Charsets.UTF_8))
            } ?: error("openOutputStream returned null")
        }.isSuccess

        parseMessage = if (saved) {
            context.getString(R.string.backup_export_success_message)
        } else {
            context.getString(R.string.backup_export_failed_message)
        }
        persistSettings()
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            parseMessage = context.getString(R.string.backup_restore_canceled_message)
            persistSettings()
            return@rememberLauncherForActivityResult
        }

        val rawJson = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        }.getOrNull()

        if (rawJson.isNullOrBlank()) {
            parseMessage = context.getString(R.string.backup_restore_failed_message)
            persistSettings()
            return@rememberLauncherForActivityResult
        }

        val parsed = parseJsonToLessons(rawJson, context)
        if (parsed.lessons.isEmpty()) {
            parseMessage = context.getString(R.string.backup_restore_no_valid_lesson_message)
            warnings = parsed.warnings
            persistSettings()
            return@rememberLauncherForActivityResult
        }

        pendingRestoreLessons = parsed.lessons
        pendingRestoreWarnings = parsed.warnings
        showRestoreConfirmDialog = true
        parseMessage = context.getString(
            R.string.backup_restore_pending_confirmation_message,
            lessons.size,
            parsed.lessons.size,
        )
        persistSettings()
    }

    LaunchedEffect(Unit) {
        val settings = MobilePrefsStore.loadSettings(context)
        val storedLessons = MobilePrefsStore.loadLessons(context)

        showWeekend = settings.showWeekend
        reminderEnabled = settings.reminderEnabled
        reminderMinutes = settings.reminderMinutes
        rawIcs = settings.rawIcs.takeUnless { it.contains("PRODID:-//Classing//Schedule Demo//EN") }.orEmpty()
        parseMessage = settings.parseMessage.ifBlank { context.getString(R.string.initial_parse_message) }
        wearSyncMode = WearSyncMode.entries.firstOrNull { it.name == settings.wearSyncMode } ?: WearSyncMode.WEARABLE_API

        if (storedLessons.isNotEmpty()) {
            lessons = storedLessons.map { it.toLessonUi() }
        } else {
            lessons = emptyList()
            persistLessons()
        }

        initialized = true
        syncReminderWork()
        refreshWearSyncAckStatus(force = true)
        refreshWearConnectionStatus()
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
                onLongPressLesson = { editingLesson = it },
            )

            MobileLayer.Import -> ImportLayer(
                contentPadding = innerPadding,
                rawIcs = rawIcs,
                rawJson = rawJson,
                parseMessage = parseMessage,
                warnings = warnings,
                preview = draftPreview,
                jsonPreview = jsonPreview,
                hasPendingImport = pendingImportLessons.isNotEmpty(),
                onRawChange = { rawIcs = it },
                onJsonRawChange = { rawJson = it },
                onClearInput = {
                    rawIcs = ""
                    rawJson = ""
                    pendingImportLessons = emptyList()
                    pendingImportConflicts = emptyList()
                    showImportConflictDialog = false
                    draftPreview = emptyList()
                    jsonPreview = emptyList()
                    warnings = emptyList()
                    parseMessage = context.getString(R.string.message_input_cleared)
                    persistSettings()
                },
                onParsePreview = {
                    val result = parseToLessons(rawIcs, parser, adapter, zoneId, context)
                    pendingImportLessons = result.lessons
                    draftPreview = result.drafts
                    jsonPreview = emptyList()
                    parseMessage = result.message
                    warnings = result.warnings
                    persistSettings()
                },
                onParseJsonPreview = {
                    val result = parseJsonToLessons(rawJson, context)
                    pendingImportLessons = result.lessons
                    draftPreview = emptyList()
                    jsonPreview = result.lessons
                    parseMessage = result.message
                    warnings = result.warnings
                    persistSettings()
                },
                onConfirmImport = {
                    if (pendingImportLessons.isEmpty()) {
                        parseMessage = context.getString(R.string.no_pending_import_message)
                    } else {
                        val conflicts = detectLessonConflicts(pendingImportLessons)
                        if (conflicts.isEmpty()) {
                            applyImportedLessons(pendingImportLessons)
                            parseMessage = context.getString(R.string.import_confirmed_message, pendingImportLessons.size)
                            pendingImportLessons = emptyList()
                            draftPreview = emptyList()
                            jsonPreview = emptyList()
                            warnings = emptyList()
                        } else {
                            pendingImportConflicts = conflicts
                            showImportConflictDialog = true
                            parseMessage = context.getString(R.string.import_conflict_detected_message, conflicts.size)
                        }
                    }
                    persistSettings()
                },
                onCancelPreview = {
                    pendingImportLessons = emptyList()
                    draftPreview = emptyList()
                    jsonPreview = emptyList()
                    warnings = emptyList()
                    parseMessage = context.getString(R.string.import_preview_canceled_message)
                    persistSettings()
                },
                onManualImport = { title, location, note, dayOfWeek, startRaw, endRaw ->
                    val safeTitle = title.trim()
                    val safeLocation = location.trim().ifBlank { null }
                    val safeNote = note.trim().ifBlank { null }
                    val start = parseManualTime(startRaw)
                    val end = parseManualTime(endRaw)
                    when {
                        safeTitle.isBlank() -> {
                            parseMessage = context.getString(R.string.manual_import_title_required_message)
                            persistSettings()
                            false
                        }

                        start == null || end == null -> {
                            parseMessage = context.getString(R.string.manual_import_time_format_message)
                            persistSettings()
                            false
                        }

                        !end.isAfter(start) -> {
                            parseMessage = context.getString(R.string.manual_import_time_order_message)
                            persistSettings()
                            false
                        }

                        else -> {
                            val newLesson = LessonUi(
                                id = "manual-${System.currentTimeMillis()}-${safeTitle.hashCode()}",
                                title = safeTitle,
                                location = safeLocation,
                                note = safeNote,
                                dayOfWeek = dayOfWeek,
                                startTime = start,
                                endTime = end,
                            )
                            val conflicts = findConflictsWithExisting(newLesson, lessons)
                            if (conflicts.isEmpty()) {
                                appendManualLesson(newLesson)
                                parseMessage = context.getString(R.string.manual_import_success_message, safeTitle)
                                persistSettings()
                                true
                            } else {
                                pendingManualLesson = newLesson
                                pendingManualConflicts = conflicts
                                showManualConflictDialog = true
                                parseMessage = context.getString(R.string.manual_import_conflict_detected_message, conflicts.size)
                                persistSettings()
                                false
                            }
                        }
                    }
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
                onExportBackup = {
                    pendingExportJson = buildScheduleBackupJson(lessons, zoneId)
                    val name = "classingtime_backup_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}.json"
                    exportBackupLauncher.launch(name)
                },
                onRestoreBackup = {
                    restoreBackupLauncher.launch(arrayOf("application/json", "text/plain"))
                },
                onClearAllSchedules = {
                    showClearAllConfirmDialog = true
                },
                wearSyncMode = wearSyncMode,
                wearConnectionMessage = wearConnectionMessage,
                wearSyncMessage = wearSyncMessage,
                wearSyncInProgress = wearSyncInProgress,
                onWearSyncModeChange = { mode ->
                    wearSyncMode = mode
                    persistSettings()
                    coroutineScope.launch {
                        refreshWearConnectionStatus()
                    }
                },
                onRefreshWearStatus = {
                    coroutineScope.launch {
                        refreshWearConnectionStatus()
                    }
                },
                onManualWearSync = {
                    coroutineScope.launch {
                        runManualWearSync()
                    }
                },
            )

            MobileLayer.About -> AboutLayer(contentPadding = innerPadding)
        }
    }

    if (showImportConflictDialog && pendingImportConflicts.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {
                showImportConflictDialog = false
                pendingImportConflicts = emptyList()
                parseMessage = context.getString(R.string.import_conflict_cancel_message)
                persistSettings()
            },
            title = { Text(stringResource(R.string.import_conflict_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.import_conflict_dialog_message, pendingImportConflicts.size),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    pendingImportConflicts.take(5).forEach { conflict ->
                        Text(
                            text = formatLessonConflict(conflict, context),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (pendingImportConflicts.size > 5) {
                        Text(
                            text = stringResource(R.string.import_conflict_more, pendingImportConflicts.size - 5),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val importSize = pendingImportLessons.size
                        if (importSize > 0) {
                            applyImportedLessons(pendingImportLessons)
                            parseMessage = context.getString(R.string.import_confirmed_with_conflict_message, importSize)
                        }
                        pendingImportLessons = emptyList()
                        pendingImportConflicts = emptyList()
                        draftPreview = emptyList()
                        jsonPreview = emptyList()
                        warnings = emptyList()
                        showImportConflictDialog = false
                        persistSettings()
                    },
                ) {
                    Text(stringResource(R.string.import_conflict_continue_button))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportConflictDialog = false
                        pendingImportConflicts = emptyList()
                        parseMessage = context.getString(R.string.import_conflict_cancel_message)
                        persistSettings()
                    },
                ) {
                    Text(stringResource(R.string.import_conflict_cancel_button))
                }
            },
        )
    }

    if (showManualConflictDialog && pendingManualLesson != null && pendingManualConflicts.isNotEmpty()) {
        val manualLesson = pendingManualLesson
        if (manualLesson != null) {
            AlertDialog(
                onDismissRequest = {
                    pendingManualLesson = null
                    pendingManualConflicts = emptyList()
                    showManualConflictDialog = false
                    parseMessage = context.getString(R.string.manual_conflict_cancel_message)
                    persistSettings()
                },
                title = { Text(stringResource(R.string.manual_conflict_dialog_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.manual_conflict_dialog_message, pendingManualConflicts.size),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = stringResource(R.string.manual_conflict_new_lesson_label, formatLessonSummary(manualLesson, context)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        pendingManualConflicts.take(5).forEach { conflictLesson ->
                            Text(
                                text = stringResource(R.string.manual_conflict_existing_lesson_label, formatLessonSummary(conflictLesson, context)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (pendingManualConflicts.size > 5) {
                            Text(
                                text = stringResource(R.string.manual_conflict_more, pendingManualConflicts.size - 5),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            appendManualLesson(manualLesson)
                            parseMessage = context.getString(R.string.manual_import_success_with_conflict_message, manualLesson.title)
                            pendingManualLesson = null
                            pendingManualConflicts = emptyList()
                            showManualConflictDialog = false
                            persistSettings()
                        },
                    ) {
                        Text(stringResource(R.string.manual_conflict_continue_button))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            pendingManualLesson = null
                            pendingManualConflicts = emptyList()
                            showManualConflictDialog = false
                            parseMessage = context.getString(R.string.manual_conflict_cancel_message)
                            persistSettings()
                        },
                    ) {
                        Text(stringResource(R.string.manual_conflict_cancel_button))
                    }
                },
            )
        }
    }

    val targetLesson = editingLesson
    if (targetLesson != null) {
        LessonEditDialog(
            lesson = targetLesson,
            onDismiss = { editingLesson = null },
            onSave = { updatedLesson, scope ->
                applyLessonEdit(updatedLesson, scope)
                editingLesson = null
            },
            onDelete = { scope ->
                removeLesson(targetLesson, scope)
                editingLesson = null
            },
        )
    }

    if (showRestoreConfirmDialog && pendingRestoreLessons.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirmDialog = false
                pendingRestoreLessons = emptyList()
                pendingRestoreWarnings = emptyList()
                parseMessage = context.getString(R.string.backup_restore_canceled_message)
                persistSettings()
            },
            title = { Text(stringResource(R.string.backup_restore_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(
                            R.string.backup_restore_dialog_message,
                            lessons.size,
                            pendingRestoreLessons.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    pendingRestoreLessons.take(5).forEach { lesson ->
                        Text(
                            text = formatLessonSummary(lesson, context),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (pendingRestoreLessons.size > 5) {
                        Text(
                            text = stringResource(R.string.backup_restore_dialog_more, pendingRestoreLessons.size - 5),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (pendingRestoreWarnings.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.backup_restore_warning_title),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        pendingRestoreWarnings.take(3).forEach { warning ->
                            Text(
                                text = stringResource(R.string.status_warning_prefix, warning),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        lessons = pendingRestoreLessons
                            .sortedWith(compareBy<LessonUi> { it.dayOfWeek.value }.thenBy { it.startTime })
                        persistLessons()
                        warnings = pendingRestoreWarnings
                        parseMessage = context.getString(R.string.backup_restore_success_message, pendingRestoreLessons.size)
                        pendingRestoreLessons = emptyList()
                        pendingRestoreWarnings = emptyList()
                        showRestoreConfirmDialog = false
                        persistSettings()
                    },
                ) {
                    Text(stringResource(R.string.backup_restore_confirm_button))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirmDialog = false
                        pendingRestoreLessons = emptyList()
                        pendingRestoreWarnings = emptyList()
                        parseMessage = context.getString(R.string.backup_restore_canceled_message)
                        persistSettings()
                    },
                ) {
                    Text(stringResource(R.string.backup_restore_cancel_button))
                }
            },
        )
    }

    if (showClearAllConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirmDialog = false },
            title = { Text(stringResource(R.string.danger_clear_dialog_title)) },
            text = {
                Text(
                    text = stringResource(R.string.danger_clear_dialog_message, lessons.size),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        lessons = emptyList()
                        persistLessons()
                        showClearAllConfirmDialog = false
                        parseMessage = context.getString(R.string.danger_clear_success_message)
                        persistSettings()
                    },
                ) {
                    Text(stringResource(R.string.danger_clear_confirm_button))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showClearAllConfirmDialog = false
                        parseMessage = context.getString(R.string.danger_clear_canceled_message)
                        persistSettings()
                    },
                ) {
                    Text(stringResource(R.string.danger_clear_cancel_button))
                }
            },
        )
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
    onLongPressLesson: (LessonUi) -> Unit,
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
                        Text(
                            text = stringResource(R.string.week_long_press_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        lessons.forEach { lesson ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(lesson.id) {
                                        detectTapGestures(
                                            onLongPress = { onLongPressLesson(lesson) },
                                        )
                                    },
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.import_page_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = stringResource(R.string.import_page_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Text(
                text = stringResource(R.string.import_method_ics),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleSmall,
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
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Text(
                text = stringResource(R.string.import_method_json),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleSmall,
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
            Card {
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
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Text(
                text = stringResource(R.string.import_method_manual),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleSmall,
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
            placeholder = { Text("08:00") },
            singleLine = true,
        )
        OutlinedTextField(
            value = manualEnd,
            onValueChange = { manualEnd = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.manual_input_end_time_label)) },
            placeholder = { Text("09:40") },
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

@Composable
private fun JsonPromptPage(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val promptText = stringResource(R.string.json_prompt_content)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.json_prompt_page_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.json_prompt_page_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = promptText,
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp),
            readOnly = true,
            label = { Text(stringResource(R.string.json_prompt_page_label)) },
            maxLines = 40,
        )
        Button(onClick = onBack) {
            Text(stringResource(R.string.json_prompt_back_button))
        }
    }
}

@Composable
private fun LessonEditDialog(
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

@Composable
private fun SettingsLayer(
    contentPadding: PaddingValues,
    showWeekend: Boolean,
    reminderEnabled: Boolean,
    reminderMinutes: Int,
    onToggleWeekend: (Boolean) -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onReminderMinutesChange: (Int) -> Unit,
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
private fun AboutLayer(contentPadding: PaddingValues) {
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    var noticeText by remember { mutableStateOf("") }
    var noticeLoading by remember { mutableStateOf(false) }
    var noticeError by remember { mutableStateOf<String?>(null) }

    fun refreshNotice() {
        coroutineScope.launch {
            noticeLoading = true
            noticeError = null
            val result = fetchClassingNotice()
            if (result.isSuccess) {
                noticeText = result.getOrNull().orEmpty()
            } else {
                noticeError = result.exceptionOrNull()?.message
            }
            noticeLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshNotice()
    }

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
                    modifier = Modifier.size(72.dp),
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
                Text(stringResource(R.string.settings_about_notice_title), fontWeight = FontWeight.SemiBold)
                when {
                    noticeLoading -> {
                        Text(
                            text = stringResource(R.string.settings_about_notice_loading),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    !noticeError.isNullOrBlank() -> {
                        Text(
                            text = stringResource(R.string.settings_about_notice_failed, noticeError.orEmpty()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    noticeText.isBlank() -> {
                        Text(
                            text = stringResource(R.string.settings_about_notice_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> {
                        Text(
                            text = noticeText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                TextButton(onClick = { refreshNotice() }) {
                    Text(stringResource(R.string.settings_about_notice_refresh))
                }
            }
        }
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

private fun detectLessonConflicts(lessons: List<LessonUi>): List<LessonConflict> {
    if (lessons.size < 2) return emptyList()
    val conflicts = mutableListOf<LessonConflict>()
    val grouped = lessons.groupBy { it.dayOfWeek }
    grouped.values.forEach { dayLessons ->
        val sorted = dayLessons.sortedBy { it.startTime }
        for (i in 0 until sorted.lastIndex) {
            val current = sorted[i]
            for (j in i + 1 until sorted.size) {
                val next = sorted[j]
                if (next.startTime >= current.endTime) break
                if (lessonsOverlap(current, next)) conflicts += LessonConflict(current, next)
            }
        }
    }
    return conflicts
}

private fun findConflictsWithExisting(candidate: LessonUi, existing: List<LessonUi>): List<LessonUi> {
    return existing
        .asSequence()
        .filter { it.dayOfWeek == candidate.dayOfWeek && lessonsOverlap(it, candidate) }
        .sortedBy { it.startTime }
        .toList()
}

private fun lessonsOverlap(first: LessonUi, second: LessonUi): Boolean {
    if (first.dayOfWeek != second.dayOfWeek) return false
    return first.startTime < second.endTime && second.startTime < first.endTime
}

private fun formatLessonConflict(conflict: LessonConflict, context: Context): String {
    return context.getString(
        R.string.import_conflict_item,
        formatLessonSummary(conflict.first, context),
        formatLessonSummary(conflict.second, context),
    )
}

private fun formatLessonSummary(lesson: LessonUi, context: Context): String {
    return context.getString(
        R.string.lesson_summary_format,
        dayLabel(lesson.dayOfWeek, context),
        lesson.startTime.format(clockFormatter),
        lesson.endTime.format(clockFormatter),
        lesson.title,
    )
}

private fun buildScheduleBackupJson(lessons: List<LessonUi>, zoneId: ZoneId): String {
    val courses = JSONArray()
    lessons.sortedWith(compareBy<LessonUi> { it.dayOfWeek.value }.thenBy { it.startTime }).forEach { lesson ->
        courses.put(
            JSONObject()
                .put("id", lesson.id)
                .put("title", lesson.title)
                .put("dayOfWeek", lesson.dayOfWeek.value)
                .put("startTime", lesson.startTime.format(clockFormatter))
                .put("endTime", lesson.endTime.format(clockFormatter))
                .put("location", lesson.location ?: "")
                .put("note", lesson.note ?: ""),
        )
    }
    return JSONObject()
        .put("format", "classingtime_backup_v1")
        .put("version", 1)
        .put("timezone", zoneId.id)
        .put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        .put("courses", courses)
        .toString(2)
}

private suspend fun fetchConnectedWearNodeCount(context: Context): Result<Int> {
    return runCatching {
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        nodes.size
    }
}

private suspend fun syncLessonsToWear(
    context: Context,
    lessons: List<LessonUi>,
    zoneId: ZoneId,
    source: String,
    allowDisconnectedQueue: Boolean,
): Result<WearSyncDispatchResult> {
    val persisted = lessons.map { it.toPersistedLesson() }
    return WearDataLayerSyncPublisher.publishLessonsSnapshot(
        context = context,
        lessons = persisted,
        zoneId = zoneId,
        source = source,
        allowDisconnectedQueue = allowDisconnectedQueue,
    )
}

private suspend fun syncLessonsViaWearOsApp(
    context: Context,
    lessons: List<LessonUi>,
    zoneId: ZoneId,
): Result<WearSyncDispatchResult> {
    val companion = findWearOsCompanionInfo(context) ?: return Result.failure(
        IllegalStateException("WearOS app not installed"),
    )
    return syncLessonsToWear(
        context = context,
        lessons = lessons,
        zoneId = zoneId,
        source = WearDataLayerContracts.SOURCE_WEAROS_APP,
        allowDisconnectedQueue = true,
    )
}

private suspend fun fetchClassingNotice(): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        val postBody = "key=$CLASSING_NOTICE_KEY"
        val connection = (URL(CLASSING_NOTICE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/json,text/plain,*/*")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        }
        try {
            connection.outputStream.use { output ->
                output.write(postBody.toByteArray(Charsets.UTF_8))
                output.flush()
            }
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty().trim()
            if (responseCode !in 200..299) {
                throw IllegalStateException("HTTP $responseCode")
            }
            parseNoticePayload(response)
        } finally {
            connection.disconnect()
        }
    }
}

private fun parseNoticePayload(raw: String): String {
    val content = raw.trim()
    if (content.isBlank()) return ""

    if (content.startsWith("{") && content.endsWith("}")) {
        return runCatching { parseNoticeObject(JSONObject(content)) }.getOrDefault(content)
    }

    if (content.startsWith("[") && content.endsWith("]")) {
        return runCatching { parseNoticeArray(JSONArray(content)) }.getOrDefault(content)
    }

    return content
}

private fun parseNoticeObject(json: JSONObject): String {
    val preferredKeys = listOf("notice", "content", "message", "msg", "data", "result")
    preferredKeys.forEach { key ->
        if (!json.has(key) || json.isNull(key)) return@forEach
        val parsed = parseNoticeValue(json.get(key))
        if (parsed.isNotBlank()) return parsed
    }

    val keys = json.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        if (json.isNull(key)) continue
        val parsed = parseNoticeValue(json.get(key))
        if (parsed.isNotBlank()) return parsed
    }
    return ""
}

private fun parseNoticeArray(array: JSONArray): String {
    for (index in 0 until array.length()) {
        val value = array.opt(index) ?: continue
        val parsed = parseNoticeValue(value)
        if (parsed.isNotBlank()) return parsed
    }
    return ""
}

private fun parseNoticeValue(value: Any): String {
    return when (value) {
        is String -> value.trim()
        is JSONObject -> parseNoticeObject(value)
        is JSONArray -> parseNoticeArray(value)
        else -> value.toString().trim()
    }
}

private fun findWearOsCompanionInfo(context: Context): WearOsCompanionInfo? {
    val packageManager = context.packageManager
    val candidates = listOf(
        "com.google.android.wearable.app.cn",
        "com.google.android.wearable.app",
    )
    candidates.forEach { pkg ->
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(pkg, 0)
            }
        }.getOrNull() ?: return@forEach
        val versionName = packageInfo.versionName.orEmpty()
        return WearOsCompanionInfo(
            packageName = pkg,
            versionName = versionName,
            isChinaOrLe = pkg.endsWith(".cn") || isLeVersion(versionName),
        )
    }
    return null
}

private fun isLeVersion(versionName: String): Boolean {
    return Regex("(^|[._-])le($|[._-])", RegexOption.IGNORE_CASE).containsMatchIn(versionName)
}

private fun WearOsCompanionInfo.toDisplayLabel(): String {
    val variant = if (isChinaOrLe) "CN/LE" else "Global"
    return if (versionName.isBlank()) {
        "$packageName [$variant]"
    } else {
        "$packageName $versionName [$variant]"
    }
}

private suspend fun handleStartedWearSync(
    context: Context,
    result: Result<WearSyncDispatchResult>,
    startedAtMillis: Long,
    queuedMessage: String = context.getString(R.string.wear_sync_queued),
    latestAckUpdater: (WearSyncAckInfo) -> Unit,
): String {
    if (result.isFailure) {
        return context.getString(
            R.string.wear_sync_failed,
            result.exceptionOrNull()?.message ?: "unknown",
        )
    }

    val dispatch = result.getOrThrow()
    if (dispatch.connectedNodeCount <= 0) {
        return if (dispatch.queuedForCompanion) {
            queuedMessage
        } else {
            context.getString(R.string.wear_connection_disconnected)
        }
    }

    val ack = awaitWearSyncAck(context, startedAtMillis)
    if (ack != null) {
        latestAckUpdater(ack)
        return formatWearSyncAckMessage(context, ack)
    }

    return context.getString(R.string.wear_sync_ack_timeout)
}

private suspend fun awaitWearSyncAck(
    context: Context,
    minSyncedAtMillis: Long,
    timeoutMillis: Long = 8_000L,
): WearSyncAckInfo? {
    val started = System.currentTimeMillis()
    while (System.currentTimeMillis() - started <= timeoutMillis) {
        val ack = WearSyncAckStore.load(context)
        if (ack != null && ack.syncedAtMillis >= minSyncedAtMillis) {
            return ack
        }
        delay(350L)
    }
    return null
}

private fun formatWearSyncAckMessage(context: Context, ack: WearSyncAckInfo): String {
    val timeText = LocalDateTime.ofInstant(Instant.ofEpochMilli(ack.syncedAtMillis), ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
    return if (ack.success) {
        context.getString(
            R.string.wear_sync_ack_success,
            ack.appliedLessonCount,
            timeText,
            resolveWearSyncSourceLabel(context, ack.source),
        )
    } else {
        context.getString(
            R.string.wear_sync_ack_failed,
            timeText,
            ack.errorMessage.ifBlank { "unknown" },
        )
    }
}

private fun resolveWearSyncSourceLabel(context: Context, source: String): String {
    return when (source.uppercase()) {
        WearDataLayerContracts.SOURCE_WEARABLE_API -> context.getString(R.string.settings_wear_sync_mode_wearable_api)
        WearDataLayerContracts.SOURCE_WEAROS_APP -> context.getString(R.string.settings_wear_sync_mode_wearos_app)
        else -> source.ifBlank { "unknown" }
    }
}

private fun parseJsonToLessons(raw: String, context: Context): JsonParseOutcome {
    if (raw.isBlank()) {
        return JsonParseOutcome(
            lessons = emptyList(),
            message = context.getString(R.string.json_empty_input_message),
            warnings = emptyList(),
        )
    }

    val warnings = mutableListOf<String>()
    val jsonArray = parseLessonArray(raw) ?: return JsonParseOutcome(
        lessons = emptyList(),
        message = context.getString(R.string.json_parse_failure_message),
        warnings = emptyList(),
    )

    val lessons = buildList {
        for (index in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(index)
            if (item == null) {
                warnings += context.getString(R.string.json_warning_item_not_object, index + 1)
                continue
            }

            val title = item.optString("title").ifBlank {
                item.optString("name").ifBlank { item.optString("courseName") }
            }.trim()
            val dayOfWeek = parseJsonDayOfWeek(item.opt("dayOfWeek") ?: item.opt("day") ?: item.opt("weekday"))
            val start = parseFlexibleTime(item.optString("startTime").ifBlank { item.optString("start") })
            val end = parseFlexibleTime(item.optString("endTime").ifBlank { item.optString("end") })

            if (title.isBlank()) {
                warnings += context.getString(R.string.json_warning_missing_title, index + 1)
                continue
            }
            if (dayOfWeek == null) {
                warnings += context.getString(R.string.json_warning_invalid_day, index + 1)
                continue
            }
            if (start == null || end == null) {
                warnings += context.getString(R.string.json_warning_invalid_time, index + 1)
                continue
            }
            if (!end.isAfter(start)) {
                warnings += context.getString(R.string.json_warning_time_order, index + 1)
                continue
            }

            add(
                LessonUi(
                    id = "json-${System.currentTimeMillis()}-$index-${title.hashCode()}",
                    title = title,
                    location = item.optString("location").ifBlank { item.optString("classroom") }.ifBlank { null },
                    note = item.optString("note").ifBlank { item.optString("description") }.ifBlank { null },
                    dayOfWeek = dayOfWeek,
                    startTime = start,
                    endTime = end,
                ),
            )
        }
    }.sortedWith(compareBy<LessonUi> { it.dayOfWeek.value }.thenBy { it.startTime })

    if (lessons.isEmpty()) {
        val warningCount = warnings.size
        return JsonParseOutcome(
            lessons = emptyList(),
            message = context.getString(R.string.json_no_valid_lesson_message, warningCount),
            warnings = warnings.take(8),
        )
    }

    val message = if (warnings.isEmpty()) {
        context.getString(R.string.json_parse_success_preview_message, lessons.size)
    } else {
        context.getString(R.string.json_parse_partial_preview_message, lessons.size, warnings.size)
    }

    return JsonParseOutcome(
        lessons = lessons,
        message = message,
        warnings = warnings.take(8),
    )
}

private fun parseLessonArray(raw: String): JSONArray? {
    return runCatching { JSONArray(raw) }.getOrNull()
        ?: runCatching {
            val root = JSONObject(raw)
            root.optJSONArray("courses")
                ?: root.optJSONArray("lessons")
                ?: root.optJSONArray("events")
        }.getOrNull()
}

private fun parseJsonDayOfWeek(raw: Any?): DayOfWeek? {
    return when (raw) {
        is Number -> raw.toInt().takeIf { it in 1..7 }?.let { DayOfWeek.of(it) }
        is String -> {
            val normalized = raw.trim().uppercase()
            when (normalized) {
                "1", "MON", "MONDAY", "周一", "星期一" -> DayOfWeek.MONDAY
                "2", "TUE", "TUESDAY", "周二", "星期二" -> DayOfWeek.TUESDAY
                "3", "WED", "WEDNESDAY", "周三", "星期三" -> DayOfWeek.WEDNESDAY
                "4", "THU", "THURSDAY", "周四", "星期四" -> DayOfWeek.THURSDAY
                "5", "FRI", "FRIDAY", "周五", "星期五" -> DayOfWeek.FRIDAY
                "6", "SAT", "SATURDAY", "周六", "星期六" -> DayOfWeek.SATURDAY
                "7", "SUN", "SUNDAY", "周日", "星期日", "周天", "星期天" -> DayOfWeek.SUNDAY
                else -> null
            }
        }

        else -> null
    }
}

private fun parseFlexibleTime(raw: String?): LocalTime? {
    if (raw.isNullOrBlank()) return null
    val text = raw.trim()
    val formatters = listOf(
        DateTimeFormatter.ofPattern("H:mm"),
        DateTimeFormatter.ofPattern("HH:mm"),
        DateTimeFormatter.ofPattern("H:mm:ss"),
        DateTimeFormatter.ofPattern("HH:mm:ss"),
    )

    formatters.forEach { formatter ->
        runCatching { LocalTime.parse(text, formatter) }.getOrNull()?.let { return it }
    }

    if (text.length == 4 && text.all { it.isDigit() }) {
        val hour = text.substring(0, 2).toIntOrNull()
        val minute = text.substring(2, 4).toIntOrNull()
        if (hour != null && minute != null && hour in 0..23 && minute in 0..59) {
            return LocalTime.of(hour, minute)
        }
    }

    return null
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

    if (drafts.isNotEmpty() && lessons.isEmpty()) {
        return ParseOutcome(
            lessons = emptyList(),
            drafts = drafts,
            message = context.getString(R.string.parse_no_valid_lesson_message),
            warnings = emptyList(),
        )
    }

    return when (result) {
        is ImportResult.Success -> ParseOutcome(
            lessons = lessons,
            drafts = drafts,
            message = context.getString(R.string.parse_success_preview_message, lessons.size),
            warnings = result.payload.warnings,
        )

        is ImportResult.PartialSuccess -> ParseOutcome(
            lessons = lessons,
            drafts = drafts,
            message = context.getString(R.string.parse_partial_preview_message, lessons.size, result.droppedLines.size),
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

private fun parseManualTime(raw: String): LocalTime? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    return runCatching {
        LocalTime.parse(trimmed, DateTimeFormatter.ofPattern("H:mm"))
    }.getOrNull()
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
        MobileLayer.About -> R.string.layer_about
    }
}

private val clockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun MobileTimetablePreview() {
    MobileTimetableScreen()
}

