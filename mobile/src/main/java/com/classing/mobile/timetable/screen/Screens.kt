package com.xtawa.classingtime.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import com.xtawa.classingtime.BuildConfig
import com.xtawa.classingtime.R
import com.xtawa.classingtime.data.MobilePrefsStore
import com.xtawa.classingtime.data.MobileSettings
import com.xtawa.classingtime.data.PersistedLesson
import com.xtawa.classingtime.reminder.KeepAliveLevel
import com.xtawa.classingtime.reminder.ReminderRuntime
import com.xtawa.classingtime.reminder.ReminderScheduler
import com.classing.shared.sync.CloudSyncContracts
import com.classing.shared.sync.WearDataLayerContracts
import com.xtawa.classingtime.sync.CloudCredentialStore
import com.xtawa.classingtime.sync.GoogleDriveAuthManager
import com.xtawa.classingtime.sync.MobileCloudSyncCoordinator
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun MobileTimetableScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val zoneId = remember { ZoneId.systemDefault() }
    val parser = remember { IcsImportParser() }
    val adapter = remember { ScheduleImportAdapter() }

    var initialized by remember { mutableStateOf(false) }
    var layerName by remember { mutableStateOf(MobileLayer.Schedule.name) }
    var previousMainLayerName by remember { mutableStateOf(MobileLayer.Schedule.name) }
    var settingsPageName by remember { mutableStateOf(SettingsPage.Main.name) }
    var showImportJsonPromptPage by remember { mutableStateOf(false) }
    var showWeekend by remember { mutableStateOf(true) }
    var reminderEnabled by remember { mutableStateOf(false) }
    var reminderMinutes by remember { mutableIntStateOf(15) }
    var keepAliveLevel by remember { mutableStateOf(KeepAliveLevel.BALANCED) }
    var experimentalAccessibilityKeepAliveEnabled by remember { mutableStateOf(false) }
    var keepAliveStatusTick by remember { mutableIntStateOf(0) }
    var weekNumberMode by remember { mutableStateOf(WeekNumberMode.NATURAL) }
    var semesterWeekStartDate by remember { mutableStateOf(LocalDate.now()) }
    var weekStartDay by remember { mutableStateOf(DayOfWeek.MONDAY) }
    var rawIcs by remember { mutableStateOf("") }
    var rawJson by remember { mutableStateOf("") }
    var parseMessage by remember { mutableStateOf(context.getString(R.string.initial_parse_message)) }
    var warnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var draftPreview by remember { mutableStateOf<List<CourseDraft>>(emptyList()) }
    var jsonPreview by remember { mutableStateOf<List<LessonUi>>(emptyList()) }
    var pendingImportLessons by remember { mutableStateOf<List<LessonUi>>(emptyList()) }
    var pendingImportConflicts by remember { mutableStateOf<List<LessonConflict>>(emptyList()) }
    var showImportConflictDialog by remember { mutableStateOf(false) }
    var importItemStates by remember { mutableStateOf<List<ImportItemState>>(emptyList()) }
    var importPreviewSummary by remember { mutableStateOf<ImportPreviewSummary?>(null) }
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
    var wearSyncMode by remember { mutableStateOf(WearSyncMode.AUTO) }
    var showOnboarding by remember { mutableStateOf(false) }
    var onboardingImportFocusMethod by remember { mutableStateOf<ImportFocusMethod?>(null) }
    var cloudProvider by remember { mutableStateOf(CloudProviderUi.WEBDAV) }
    var cloudSyncEnabled by remember { mutableStateOf(false) }
    var cloudServerUrl by remember { mutableStateOf("") }
    var cloudRemotePath by remember { mutableStateOf(CloudSyncContracts.DEFAULT_REMOTE_PATH) }
    var cloudUsername by remember { mutableStateOf("") }
    var cloudPassword by remember { mutableStateOf("") }
    var cloudDriveFileName by remember { mutableStateOf(CloudSyncContracts.DEFAULT_DRIVE_FILE_NAME) }
    var cloudDriveTokenExpireAt by remember { mutableStateOf(0L) }
    var cloudDriveAccessToken by remember { mutableStateOf("") }
    var cloudSyncStatus by remember { mutableStateOf("") }
    var cloudLastSyncedAt by remember { mutableStateOf(0L) }
    var cloudSyncInProgress by remember { mutableStateOf(false) }
    var cloudConfigPushStatus by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val wearSyncMutex = remember { Mutex() }
    var weekSettingsAutoSyncPending by remember { mutableStateOf(false) }
    var weekSettingsAutoSyncJob by remember { mutableStateOf<Job?>(null) }

    fun goToSettingsRoot() {
        settingsPageName = SettingsPage.Main.name
        showImportJsonPromptPage = false
        onboardingImportFocusMethod = null
    }

    fun openSettingsPage(page: SettingsPage, importFocusMethod: ImportFocusMethod? = null) {
        settingsPageName = page.name
        showImportJsonPromptPage = false
        onboardingImportFocusMethod = if (page == SettingsPage.Import) importFocusMethod else null
    }

    fun handleBackNavigation(): Boolean {
        val current = MobileBackState(
            layer = MobileLayer.entries.firstOrNull { it.name == layerName } ?: MobileLayer.Schedule,
            settingsPage = SettingsPage.entries.firstOrNull { it.name == settingsPageName } ?: SettingsPage.Main,
            previousMainLayer = MobileLayer.entries.firstOrNull { it.name == previousMainLayerName } ?: MobileLayer.Schedule,
            showImportJsonPromptPage = showImportJsonPromptPage,
        )
        val reduced = reduceBackState(current) ?: return false
        layerName = reduced.layer.name
        settingsPageName = reduced.settingsPage.name
        showImportJsonPromptPage = reduced.showImportJsonPromptPage
        return true
    }

    fun persistSettings() {
        com.xtawa.classingtime.screen.persistSettings(
            context = context,
            showWeekend = showWeekend,
            reminderEnabled = reminderEnabled,
            reminderMinutes = reminderMinutes,
            keepAliveLevel = keepAliveLevel,
            experimentalAccessibilityKeepAliveEnabled = experimentalAccessibilityKeepAliveEnabled,
            rawIcs = rawIcs,
            parseMessage = parseMessage,
            wearSyncMode = wearSyncMode,
            weekNumberMode = weekNumberMode,
            semesterWeekStartDate = semesterWeekStartDate,
            weekStartDay = weekStartDay,
            cloudProvider = cloudProvider,
            cloudSyncEnabled = cloudSyncEnabled,
            cloudServerUrl = cloudServerUrl,
            cloudRemotePath = cloudRemotePath,
            cloudUsername = cloudUsername,
            cloudDriveFileName = cloudDriveFileName,
            cloudDriveTokenExpireAt = cloudDriveTokenExpireAt,
            cloudConfigPushStatus = cloudConfigPushStatus,
            cloudLastResult = cloudSyncStatus,
            cloudLastSyncedAt = cloudLastSyncedAt,
        )
    }

    fun persistLessons() {
        com.xtawa.classingtime.screen.persistLessons(context, lessons)
    }

    fun applyImportedLessons(importLessons: List<LessonUi>) {
        lessons = com.xtawa.classingtime.screen.applyImportedLessons(importLessons)
        persistLessons()
    }

    fun appendManualLesson(newLesson: LessonUi) {
        lessons = com.xtawa.classingtime.screen.appendManualLesson(lessons, newLesson)
        persistLessons()
    }

    fun applyLessonEdit(updatedLesson: LessonUi, scope: ChangeScope) {
        lessons = com.xtawa.classingtime.screen.applyLessonEdit(lessons, updatedLesson)
        if (scope == ChangeScope.Persistent) persistLessons()
        parseMessage = if (scope == ChangeScope.Persistent) {
            context.getString(R.string.lesson_edit_saved_persistent_message, updatedLesson.title)
        } else {
            context.getString(R.string.lesson_edit_saved_temporary_message, updatedLesson.title)
        }
        persistSettings()
    }

    fun removeLesson(targetLesson: LessonUi, scope: ChangeScope) {
        lessons = com.xtawa.classingtime.screen.removeLesson(lessons, targetLesson)
        if (scope == ChangeScope.Persistent) persistLessons()
        parseMessage = if (scope == ChangeScope.Persistent) {
            context.getString(R.string.lesson_delete_persistent_message, targetLesson.title)
        } else {
            context.getString(R.string.lesson_delete_temporary_message, targetLesson.title)
        }
        persistSettings()
    }

    fun syncReminderWork() {
        ReminderScheduler.sync(
            context = context,
            enabled = reminderEnabled,
            keepAliveLevel = keepAliveLevel,
            reminderMinutes = reminderMinutes,
        )
    }

    fun applyIcsPreviewFromRaw(raw: String) {
        val result = parseToLessons(
            raw = raw,
            parser = parser,
            adapter = adapter,
            zoneId = zoneId,
            weekNumberMode = weekNumberMode,
            semesterWeekStartDate = semesterWeekStartDate,
            context = context,
        )
        pendingImportLessons = result.lessons
        draftPreview = result.drafts
        jsonPreview = emptyList()
        parseMessage = result.message
        warnings = result.warnings
        importItemStates = buildImportItemStates(result.lessons, lessons)
        importPreviewSummary = buildImportPreviewSummary(importItemStates)
    }

    fun applyJsonPreviewFromRaw(raw: String) {
        val result = parseJsonToLessons(raw, context)
        pendingImportLessons = result.lessons
        draftPreview = emptyList()
        jsonPreview = result.lessons
        parseMessage = result.message
        warnings = result.warnings
        importItemStates = buildImportItemStates(result.lessons, lessons)
        importPreviewSummary = buildImportPreviewSummary(importItemStates)
    }

    suspend fun runCloudSync(trigger: String, force: Boolean = false, alsoPushConfigToWear: Boolean = true) {
        cloudSyncInProgress = true
        try {
            val result = MobileCloudSyncCoordinator.requestCloudSync(
                context = context,
                trigger = trigger,
                force = force,
                alsoPushConfigToWear = alsoPushConfigToWear,
            )
            if (result.isSuccess) {
                val outcome = result.getOrThrow()
                cloudSyncStatus = outcome.message
                cloudLastSyncedAt = outcome.syncedAt
                cloudConfigPushStatus = if (outcome.pushedConfigNodes > 0) {
                    "Config pushed to ${outcome.pushedConfigNodes} wear node(s)"
                } else {
                    cloudConfigPushStatus
                }
                val updated = MobilePrefsStore.loadSettings(context)
                cloudSyncStatus = updated.cloudLastResult
                cloudLastSyncedAt = updated.cloudLastSyncedAt
                cloudConfigPushStatus = updated.cloudConfigPushStatus
            } else {
                cloudSyncStatus = "Cloud sync failed: ${result.exceptionOrNull()?.message ?: "unknown"}"
            }
            persistSettings()
        } finally {
            cloudSyncInProgress = false
        }
    }

    fun requestCloudSync(trigger: String, force: Boolean = false, alsoPushConfigToWear: Boolean = true) {
        coroutineScope.launch {
            runCloudSync(trigger = trigger, force = force, alsoPushConfigToWear = alsoPushConfigToWear)
        }
    }

    fun refreshWearSyncAckStatus(force: Boolean = false) {
        val update = resolveSyncAckUpdate(context, latestWearAckAtMillis, force) ?: return
        latestWearAckAtMillis = update.latestAckAtMillis
        wearSyncMessage = update.wearSyncMessage
    }

    suspend fun refreshWearConnectionStatus() {
        val refreshed = computeWearConnectionStatus(
            context = context,
            wearSyncMode = wearSyncMode,
            latestWearAckAtMillis = latestWearAckAtMillis,
            currentWearSyncMessage = wearSyncMessage,
        )
        wearConnectedCount = refreshed.wearConnectedCount
        wearConnectionMessage = refreshed.wearConnectionMessage
        latestWearAckAtMillis = refreshed.latestAckAtMillis
        wearSyncMessage = refreshed.wearSyncMessage
    }

    suspend fun runWearSyncInternal() {
        val result = executeManualWearSync(
            context = context,
            wearSyncMode = wearSyncMode,
            lessons = lessons,
            zoneId = zoneId,
            latestWearAckAtMillis = latestWearAckAtMillis,
            weekNumberMode = weekNumberMode,
            semesterWeekStartDate = semesterWeekStartDate,
        )
        wearSyncMessage = result.wearSyncMessage
        latestWearAckAtMillis = result.latestAckAtMillis
        wearConnectedCount = result.wearConnectedCount
        wearConnectionMessage = result.wearConnectionMessage
    }

    suspend fun runManualWearSync() {
        wearSyncMutex.withLock {
            wearSyncInProgress = true
            try {
                runWearSyncInternal()
            } finally {
                wearSyncInProgress = false
            }
        }
    }

    suspend fun runWeekSettingsAutoWearSync() {
        wearSyncMutex.withLock {
            wearSyncInProgress = true
            try {
                runWearSyncInternal()
            } finally {
                wearSyncInProgress = false
            }
        }
    }

    fun scheduleWeekSettingsAutoSync() {
        weekSettingsAutoSyncPending = true
        if (weekSettingsAutoSyncJob?.isActive == true) return
        weekSettingsAutoSyncJob = coroutineScope.launch {
            try {
                delay(700L)
                while (weekSettingsAutoSyncPending) {
                    weekSettingsAutoSyncPending = false
                    runWeekSettingsAutoWearSync()
                    if (weekSettingsAutoSyncPending) {
                        delay(700L)
                    }
                }
            } finally {
                weekSettingsAutoSyncJob = null
            }
        }
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
        requestCloudSync(
            trigger = CloudSyncContracts.TRIGGER_SETTINGS_CHANGED,
            alsoPushConfigToWear = false,
        )
    }

    val driveAuthorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val data = result.data
        if (data == null) {
            cloudSyncInProgress = false
            cloudSyncStatus = context.getString(R.string.settings_cloud_sync_drive_auth_canceled)
            persistSettings()
            return@rememberLauncherForActivityResult
        }
        val authResult = GoogleDriveAuthManager.getAuthorizationResultFromIntent(context, data).getOrNull()
            ?: run {
                cloudSyncInProgress = false
                cloudSyncStatus = context.getString(R.string.settings_cloud_sync_drive_auth_failed, "authorization result error")
                persistSettings()
                return@rememberLauncherForActivityResult
            }
        val token = GoogleDriveAuthManager.parseAccessToken(authResult).getOrNull() ?: run {
            cloudSyncInProgress = false
            cloudSyncStatus = context.getString(R.string.settings_cloud_sync_drive_auth_failed, "empty token")
            persistSettings()
            return@rememberLauncherForActivityResult
        }
        cloudDriveAccessToken = token.token
        cloudDriveTokenExpireAt = token.expireAt
        CloudCredentialStore.saveDriveAccessToken(context, token.token, token.expireAt)
        cloudConfigPushStatus = ""
        cloudSyncStatus = context.getString(R.string.settings_cloud_sync_drive_connected)
        cloudSyncInProgress = false
        persistSettings()
        requestCloudSync(
            trigger = CloudSyncContracts.TRIGGER_SETTINGS_CHANGED,
            force = true,
            alsoPushConfigToWear = true,
        )
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

        val restoreRawJson = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        }.getOrNull()

        if (restoreRawJson.isNullOrBlank()) {
            parseMessage = context.getString(R.string.backup_restore_failed_message)
            persistSettings()
            return@rememberLauncherForActivityResult
        }

        val parsed = parseJsonToLessons(restoreRawJson, context)
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
        keepAliveLevel = KeepAliveLevel.fromRaw(settings.keepAliveLevel)
        experimentalAccessibilityKeepAliveEnabled = settings.experimentalAccessibilityKeepAliveEnabled
        rawIcs = settings.rawIcs.takeUnless { it.contains("PRODID:-//Classing//Schedule Demo//EN") }.orEmpty()
        parseMessage = settings.parseMessage.ifBlank { context.getString(R.string.initial_parse_message) }
        wearSyncMode = WearSyncMode.entries.firstOrNull { it.name == settings.wearSyncMode } ?: WearSyncMode.AUTO
        weekNumberMode = WeekNumberMode.entries.firstOrNull { it.name == settings.weekNumberMode } ?: WeekNumberMode.NATURAL
        semesterWeekStartDate = runCatching { LocalDate.parse(settings.semesterWeekStartDate) }.getOrDefault(LocalDate.now())
        weekStartDay = parseWeekStartDay(settings.weekStartDay)
        cloudProvider = CloudProviderUi.entries.firstOrNull { it.name == settings.cloudProvider } ?: CloudProviderUi.WEBDAV
        cloudSyncEnabled = settings.cloudSyncEnabled
        cloudServerUrl = settings.cloudServerUrl
        cloudRemotePath = settings.cloudRemotePath.ifBlank { CloudSyncContracts.DEFAULT_REMOTE_PATH }
        cloudUsername = settings.cloudUsername
        cloudDriveFileName = settings.cloudDriveFileName.ifBlank { CloudSyncContracts.DEFAULT_DRIVE_FILE_NAME }
        cloudDriveTokenExpireAt = maxOf(
            settings.cloudDriveTokenExpireAt,
            CloudCredentialStore.loadDriveAccessTokenExpireAt(context),
        )
        cloudConfigPushStatus = settings.cloudConfigPushStatus
        cloudPassword = CloudCredentialStore.loadPassword(context)
        cloudDriveAccessToken = CloudCredentialStore.loadDriveAccessToken(context)
        cloudSyncStatus = settings.cloudLastResult
        cloudLastSyncedAt = settings.cloudLastSyncedAt

        if (storedLessons.isNotEmpty()) {
            lessons = storedLessons.map { it.toLessonUi() }
        } else {
            lessons = emptyList()
            persistLessons()
        }
        MobilePrefsStore.ensureOnboardingCompletedForLegacyUser(context)
        showOnboarding = !MobilePrefsStore.isOnboardingCompleted(context)

        initialized = true
        if (!showOnboarding) {
            syncReminderWork()
            refreshWearSyncAckStatus(force = true)
            refreshWearConnectionStatus()
            runCloudSync(trigger = CloudSyncContracts.TRIGGER_APP_START, force = true)
        }
    }

    LaunchedEffect(initialized) {
        if (!initialized) return@LaunchedEffect
        val activity = context as? android.app.Activity ?: return@LaunchedEffect
        val mainActivity = activity as? com.xtawa.classingtime.MainActivity ?: return@LaunchedEffect
        val sharedUri = mainActivity.sharedImportUri.value
        val sharedMime = mainActivity.sharedImportMime.value
        val sharedText = com.xtawa.classingtime.MainActivity.sharedImportText
        com.xtawa.classingtime.MainActivity.sharedImportText = null
        if (sharedUri != null) {
            val content = runCatching {
                context.contentResolver.openInputStream(sharedUri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            }.getOrNull()
            if (!content.isNullOrBlank()) {
                val isIcs = sharedMime?.contains("calendar") == true ||
                    sharedUri.path?.endsWith(".ics", ignoreCase = true) == true ||
                    content.contains("BEGIN:VCALENDAR", ignoreCase = true)
                if (isIcs) {
                    rawIcs = content
                    applyIcsPreviewFromRaw(content)
                } else {
                    rawJson = content
                    applyJsonPreviewFromRaw(content)
                }
                previousMainLayerName = MobileLayer.Schedule.name
                layerName = MobileLayer.Settings.name
                settingsPageName = SettingsPage.Import.name
                showImportJsonPromptPage = false
                onboardingImportFocusMethod = if (isIcs) ImportFocusMethod.ICS else ImportFocusMethod.JSON
                persistSettings()
            }
            mainActivity.sharedImportUri.value = null
            mainActivity.sharedImportMime.value = null
        } else if (!sharedText.isNullOrBlank()) {
            val isIcs = sharedText.contains("BEGIN:VCALENDAR", ignoreCase = true)
            if (isIcs) {
                rawIcs = sharedText
                applyIcsPreviewFromRaw(sharedText)
            } else {
                rawJson = sharedText
                applyJsonPreviewFromRaw(sharedText)
            }
            previousMainLayerName = MobileLayer.Schedule.name
            layerName = MobileLayer.Settings.name
            settingsPageName = SettingsPage.Import.name
            showImportJsonPromptPage = false
            onboardingImportFocusMethod = if (isIcs) ImportFocusMethod.ICS else ImportFocusMethod.JSON
            persistSettings()
        }
    }

    LaunchedEffect(initialized, cloudSyncEnabled, lifecycleOwner, showOnboarding) {
        if (!initialized) return@LaunchedEffect
        while (true) {
            if (!showOnboarding && cloudSyncEnabled && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                runCloudSync(
                    trigger = CloudSyncContracts.TRIGGER_FOREGROUND_TICK,
                    force = false,
                    alsoPushConfigToWear = false,
                )
            }
            delay(120_000L)
        }
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
    if (showOnboarding) {
        MobileOnboardingFlow(
            initialShowWeekend = showWeekend,
            initialReminderEnabled = reminderEnabled,
            initialSemesterWeekStartDate = semesterWeekStartDate,
            initialWearSyncMode = wearSyncMode,
            initialCloudProvider = cloudProvider,
            initialCloudSyncEnabled = cloudSyncEnabled,
            initialCloudServerUrl = cloudServerUrl,
            initialCloudRemotePath = cloudRemotePath,
            initialCloudUsername = cloudUsername,
            initialCloudPassword = cloudPassword,
            initialCloudDriveFileName = cloudDriveFileName,
            onComplete = { completion ->
                showWeekend = completion.showWeekend
                semesterWeekStartDate = completion.semesterWeekStartDate
                wearSyncMode = completion.wearSyncMode
                reminderEnabled = completion.reminderEnabled
                cloudProvider = completion.cloudProvider
                cloudSyncEnabled = completion.cloudSyncEnabled
                cloudServerUrl = completion.cloudServerUrl
                cloudRemotePath = completion.cloudRemotePath.ifBlank { CloudSyncContracts.DEFAULT_REMOTE_PATH }
                cloudUsername = completion.cloudUsername
                cloudPassword = completion.cloudPassword
                cloudDriveFileName = completion.cloudDriveFileName.ifBlank { CloudSyncContracts.DEFAULT_DRIVE_FILE_NAME }
                if (cloudProvider == CloudProviderUi.WEBDAV) {
                    CloudCredentialStore.savePassword(context, cloudPassword)
                }
                if (completion.reminderEnabled &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    reminderEnabled = false
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                MobilePrefsStore.setOnboardingCompleted(context, completed = true)
                showOnboarding = false
                persistSettings()
                syncReminderWork()
                requestCloudSync(
                    trigger = CloudSyncContracts.TRIGGER_SETTINGS_CHANGED,
                    alsoPushConfigToWear = false,
                )

                val navigationDecision = resolveOnboardingNavigation(completion)
                val targetSettingsPage = navigationDecision.targetSettingsPage

                if (targetSettingsPage == null) {
                    layerName = MobileLayer.Schedule.name
                    settingsPageName = SettingsPage.Main.name
                    showImportJsonPromptPage = false
                    onboardingImportFocusMethod = null
                } else {
                    previousMainLayerName = MobileLayer.Schedule.name
                    layerName = MobileLayer.Settings.name
                    settingsPageName = targetSettingsPage.name
                    showImportJsonPromptPage = false
                    onboardingImportFocusMethod = navigationDecision.importFocusMethod
                }
                coroutineScope.launch {
                    refreshWearConnectionStatus()
                }
            },
        )
        return
    }

    val layer = MobileLayer.entries.firstOrNull { it.name == layerName } ?: MobileLayer.Schedule
    val settingsPage = SettingsPage.entries.firstOrNull { it.name == settingsPageName } ?: SettingsPage.Main
    val contentDestination = remember(layer, settingsPage, showImportJsonPromptPage) {
        MobileContentDestination(
            layer = layer,
            settingsPage = settingsPage,
            showImportJsonPromptPage = showImportJsonPromptPage,
        )
    }
    val previousMainLayer = MobileLayer.entries.firstOrNull { it.name == previousMainLayerName } ?: MobileLayer.Schedule
    val currentBackState = remember(contentDestination, previousMainLayer) {
        MobileBackState(
            layer = contentDestination.layer,
            settingsPage = contentDestination.settingsPage,
            previousMainLayer = previousMainLayer,
            showImportJsonPromptPage = contentDestination.showImportJsonPromptPage,
        )
    }
    val canHandleBack = remember(currentBackState) { reduceBackState(currentBackState) != null }
    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(contentDestination) {
        predictiveBackProgress = 0f
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        PredictiveBackHandler(enabled = canHandleBack) { progress ->
            try {
                progress.collect { backEvent ->
                    predictiveBackProgress = backEvent.progress.coerceIn(0f, 1f)
                }
                predictiveBackProgress = 0f
                handleBackNavigation()
            } catch (_: CancellationException) {
                predictiveBackProgress = 0f
            }
        }
    } else {
        BackHandler(enabled = canHandleBack) {
            handleBackNavigation()
        }
    }
    val contentGestureProgress = if (canHandleBack) predictiveBackProgress else 0f
    val visibleDays = if (showWeekend) DayOfWeek.values().toList() else DayOfWeek.values().filter { it.value <= 5 }
    val lessonsByDay = lessons.groupBy { it.dayOfWeek }
    val latestWearAck = remember(latestWearAckAtMillis) { WearSyncAckStore.load(context) }
    val bluetoothSyncState = resolveBluetoothSyncIndicatorState(
        syncInProgress = wearSyncInProgress,
        syncMessage = wearSyncMessage,
        latestAck = latestWearAck,
        wearConnectedCount = wearConnectedCount,
        wearConnectionMessage = wearConnectionMessage,
    )
    val cloudSyncState = resolveCloudSyncIndicatorState(
        syncInProgress = cloudSyncInProgress,
        syncStatus = cloudSyncStatus,
    )
    val keepAliveRuntimeStatus = remember(
        keepAliveLevel,
        experimentalAccessibilityKeepAliveEnabled,
        reminderEnabled,
        reminderMinutes,
        keepAliveStatusTick,
    ) {
        ReminderRuntime.resolveStatus(context)
    }
    val keepAliveStatusText = buildString {
        append("精确闹钟: ")
        append(if (keepAliveRuntimeStatus.canScheduleExactAlarm) "已授权" else "未授权")
        append(" · 电池优化白名单: ")
        append(if (keepAliveRuntimeStatus.ignoringBatteryOptimizations) "已加入" else "未加入")
        append(" · 无障碍服务: ")
        append(if (keepAliveRuntimeStatus.accessibilityServiceEnabled) "已启用" else "未启用")
    }
    val autoDetection = remember {
        detectWearAutoSyncPlan(findWearOsCompanionInfo(context))
    }
    val autoDetectedLabel = stringResource(
        R.string.settings_wear_auto_detected_label,
        wearAutoVariantLabel(context, autoDetection.variant),
    )
    val autoEffectiveLabel = stringResource(
        R.string.settings_wear_auto_effective_label,
        wearSyncModeLabel(context, autoDetection.effectiveMode),
    )
    val autoFallbackHint = if (autoDetection.variant == WearAutoVariant.UNKNOWN) {
        stringResource(R.string.settings_wear_auto_unknown_hint)
    } else {
        ""
    }
    val showDriveCnWarning = cloudProvider == CloudProviderUi.GOOGLE_DRIVE &&
        autoDetection.variant == WearAutoVariant.CN_LE
    val driveTokenExpireText = if (cloudDriveTokenExpireAt > 0L) {
        LocalDateTime.ofInstant(
            Instant.ofEpochMilli(cloudDriveTokenExpireAt),
            ZoneId.systemDefault(),
        ).format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
    } else {
        stringResource(R.string.settings_cloud_sync_never)
    }
    val driveConnected = cloudDriveAccessToken.isNotBlank() &&
        cloudDriveTokenExpireAt > System.currentTimeMillis() + 60_000L
    val importContent: @Composable (PaddingValues, Boolean) -> Unit = { innerPadding, showJsonPromptPage ->
        ImportLayer(
            contentPadding = innerPadding,
            onBackToSettings = { handleBackNavigation() },
            showJsonPromptPage = showJsonPromptPage,
            onBackFromJsonPromptPage = { handleBackNavigation() },
            onOpenJsonPromptPage = { showImportJsonPromptPage = true },
            initialFocusMethod = onboardingImportFocusMethod,
            onInitialFocusConsumed = { consumed ->
                onboardingImportFocusMethod = consumeImportFocus(onboardingImportFocusMethod, consumed)
            },
            rawIcs = rawIcs,
            rawJson = rawJson,
            parseMessage = parseMessage,
            warnings = warnings,
            preview = draftPreview,
            jsonPreview = jsonPreview,
            hasPendingImport = pendingImportLessons.isNotEmpty(),
            importItemStates = importItemStates,
            importPreviewSummary = importPreviewSummary,
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
                importItemStates = emptyList()
                importPreviewSummary = null
                parseMessage = context.getString(R.string.message_input_cleared)
                persistSettings()
            },
            onParsePreview = {
                applyIcsPreviewFromRaw(rawIcs)
                persistSettings()
            },
            onParseJsonPreview = {
                applyJsonPreviewFromRaw(rawJson)
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
                        importItemStates = emptyList()
                        importPreviewSummary = null
                    } else {
                        pendingImportConflicts = conflicts
                        showImportConflictDialog = true
                        parseMessage = context.getString(R.string.import_conflict_detected_message, conflicts.size)
                    }
                }
                persistSettings()
            },
            onConfirmSelectiveImport = { selectedLessons ->
                val skippedCount = pendingImportLessons.size - selectedLessons.size
                applyImportedLessons(selectedLessons)
                parseMessage = context.getString(R.string.import_selective_applied, selectedLessons.size, skippedCount)
                pendingImportLessons = emptyList()
                pendingImportConflicts = emptyList()
                showImportConflictDialog = false
                draftPreview = emptyList()
                jsonPreview = emptyList()
                warnings = emptyList()
                importItemStates = emptyList()
                importPreviewSummary = null
                persistSettings()
            },
            onCancelPreview = {
                pendingImportLessons = emptyList()
                draftPreview = emptyList()
                jsonPreview = emptyList()
                warnings = emptyList()
                importItemStates = emptyList()
                importPreviewSummary = null
                parseMessage = context.getString(R.string.import_preview_canceled_message)
                persistSettings()
            },
            onToggleImportItem = { index ->
                if (index in importItemStates.indices) {
                    val current = importItemStates[index]
                    importItemStates = importItemStates.toMutableList().also {
                        it[index] = current.copy(included = !current.included)
                    }
                    importPreviewSummary = buildImportPreviewSummary(importItemStates)
                }
            },
            onIcsFileSelected = { uri ->
                val content = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }.getOrNull()
                if (content.isNullOrBlank()) {
                    parseMessage = context.getString(R.string.import_file_read_failed)
                } else {
                    rawIcs = content
                    applyIcsPreviewFromRaw(content)
                }
                persistSettings()
            },
            onJsonFileSelected = { uri ->
                val content = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }.getOrNull()
                if (content.isNullOrBlank()) {
                    parseMessage = context.getString(R.string.import_file_read_failed)
                } else {
                    rawJson = content
                    applyJsonPreviewFromRaw(content)
                }
                persistSettings()
            },
            onManualImport = { title, teacher, location, note, dayOfWeek, startRaw, endRaw, startWeekRaw, endWeekRaw, weekParity ->
                val safeTitle = title.trim()
                val safeTeacher = teacher.trim().ifBlank { null }
                val safeLocation = location.trim().ifBlank { null }
                val safeNote = note.trim().ifBlank { null }
                val start = parseManualTime(startRaw)
                val end = parseManualTime(endRaw)
                val startWeek = startWeekRaw.trim().toIntOrNull()
                val endWeek = endWeekRaw.trim().toIntOrNull()
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

                    startWeek == null || startWeek !in DEFAULT_START_WEEK..DEFAULT_END_WEEK -> {
                        parseMessage = context.getString(R.string.week_rule_invalid_start_week_message)
                        persistSettings()
                        false
                    }

                    endWeek == null || endWeek !in startWeek..DEFAULT_END_WEEK -> {
                        parseMessage = context.getString(R.string.week_rule_invalid_end_week_message)
                        persistSettings()
                        false
                    }

                    else -> {
                        val newLesson = LessonUi(
                            id = "manual-${System.currentTimeMillis()}-${safeTitle.hashCode()}",
                            title = safeTitle,
                            teacher = safeTeacher,
                            location = safeLocation,
                            note = safeNote,
                            dayOfWeek = dayOfWeek,
                            startTime = start,
                            endTime = end,
                            startWeek = startWeek,
                            endWeek = endWeek,
                            weekParity = weekParity,
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
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                    contentDescription = stringResource(R.string.app_name),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.screen_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SyncStatusGroup(
                            bluetoothState = bluetoothSyncState,
                            cloudState = cloudSyncState,
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
            ) {
                NavigationBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(82.dp),
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                ) {
                    MobileLayer.entries.forEach { item ->
                        val icon = when (item) {
                            MobileLayer.Schedule -> Icons.AutoMirrored.Filled.MenuBook
                            MobileLayer.Dashboard -> Icons.Filled.GridView
                            MobileLayer.Calendar -> Icons.Filled.CalendarMonth
                            MobileLayer.Settings -> Icons.Filled.Settings
                        }
                        NavigationBarItem(
                            selected = item == layer,
                            onClick = {
                                if (item == MobileLayer.Settings && layer != MobileLayer.Settings) {
                                    previousMainLayerName = layer.name
                                }
                                layerName = item.name
                                if (item != MobileLayer.Settings) {
                                    goToSettingsRoot()
                                }
                            },
                            icon = { Icon(imageVector = icon, contentDescription = null) },
                            alwaysShowLabel = true,
                            label = {
                                Text(
                                    stringResource(item.labelRes()),
                                    fontWeight = if (item == layer) FontWeight.SemiBold else FontWeight.Medium,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = contentDestination,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val progress = contentGestureProgress.coerceIn(0f, 1f)
                    translationX = 48f * progress
                    scaleX = 1f - (0.05f * progress)
                    scaleY = 1f - (0.05f * progress)
                    alpha = 1f - (0.08f * progress)
                },
            transitionSpec = {
                when (resolveContentTransitionDirection(initialState, targetState)) {
                    ContentTransitionDirection.Forward -> (
                        slideInHorizontally(
                            animationSpec = tween(260),
                            initialOffsetX = { fullWidth -> fullWidth / 4 },
                        ) + fadeIn(animationSpec = tween(220))
                        ).togetherWith(
                        slideOutHorizontally(
                            animationSpec = tween(220),
                            targetOffsetX = { fullWidth -> -(fullWidth / 6) },
                        ) + fadeOut(animationSpec = tween(180)),
                    )

                    ContentTransitionDirection.Backward -> (
                        slideInHorizontally(
                            animationSpec = tween(260),
                            initialOffsetX = { fullWidth -> -(fullWidth / 4) },
                        ) + fadeIn(animationSpec = tween(220))
                        ).togetherWith(
                        slideOutHorizontally(
                            animationSpec = tween(220),
                            targetOffsetX = { fullWidth -> fullWidth / 6 },
                        ) + fadeOut(animationSpec = tween(180)),
                    )

                    ContentTransitionDirection.None -> (
                        fadeIn(animationSpec = tween(120))
                        ).togetherWith(
                        fadeOut(animationSpec = tween(90)),
                    )
                }.using(
                    SizeTransform(clip = false),
                )
            },
            label = "mobile_content_transition",
        ) { destination ->
            when (destination.layer) {
            MobileLayer.Schedule -> WeekBoardLayer(
                contentPadding = innerPadding,
                visibleDays = visibleDays,
                lessonsByDay = lessonsByDay,
                onLongPressLesson = { editingLesson = it },
            )

            MobileLayer.Dashboard -> DashboardHeatmapLayer(
                contentPadding = innerPadding,
                lessons = lessons,
            )

            MobileLayer.Calendar -> CalendarMonthLayer(
                contentPadding = innerPadding,
                lessonsByDay = lessonsByDay,
            )

            MobileLayer.Settings -> when (destination.settingsPage) {
                SettingsPage.Main -> SettingsLayer(
                    contentPadding = innerPadding,
                    showWeekend = showWeekend,
                    onOpenImportPage = {
                        openSettingsPage(SettingsPage.Import)
                    },
                    onOpenBackupRestorePage = {
                        openSettingsPage(SettingsPage.BackupRestore)
                    },
                    onOpenWeekModePage = {
                        openSettingsPage(SettingsPage.WeekMode)
                    },
                    onOpenReminderKeepAlivePage = {
                        openSettingsPage(SettingsPage.ReminderKeepAlive)
                    },
                    onOpenSyncCommunicationPage = {
                        openSettingsPage(SettingsPage.SyncCommunication)
                    },
                    onOpenAboutPage = {
                        openSettingsPage(SettingsPage.About)
                    },
                    onToggleWeekend = {
                        showWeekend = it
                        persistSettings()
                        requestCloudSync(
                            trigger = CloudSyncContracts.TRIGGER_SETTINGS_CHANGED,
                            alsoPushConfigToWear = false,
                        )
                    },
                    onClearAllSchedules = {
                        showClearAllConfirmDialog = true
                    },
                )

                SettingsPage.Import -> importContent(innerPadding, destination.showImportJsonPromptPage)

                SettingsPage.BackupRestore -> BackupRestoreSettingsPage(
                    contentPadding = innerPadding,
                    onBack = {
                        handleBackNavigation()
                    },
                    onExportBackup = {
                        pendingExportJson = buildScheduleBackupJson(lessons, zoneId)
                        val name = "classingtime_backup_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}.json"
                        exportBackupLauncher.launch(name)
                    },
                    onRestoreBackup = {
                        restoreBackupLauncher.launch(arrayOf("application/json", "text/plain"))
                    },
                )

                SettingsPage.WeekMode -> WeekModeSettingsPage(
                    contentPadding = innerPadding,
                    weekNumberMode = weekNumberMode,
                    semesterWeekStartDate = semesterWeekStartDate,
                    weekStartDay = weekStartDay,
                    onBack = {
                        handleBackNavigation()
                    },
                    onWeekNumberModeChange = { mode ->
                        if (weekNumberMode != mode) {
                            weekNumberMode = mode
                            persistSettings()
                            scheduleWeekSettingsAutoSync()
                            requestCloudSync(
                                trigger = CloudSyncContracts.TRIGGER_SETTINGS_CHANGED,
                                alsoPushConfigToWear = false,
                            )
                        }
                    },
                    onWeekStartDayChange = { day ->
                        if (weekStartDay != day) {
                            weekStartDay = day
                            persistSettings()
                            scheduleWeekSettingsAutoSync()
                            requestCloudSync(
                                trigger = CloudSyncContracts.TRIGGER_SETTINGS_CHANGED,
                                alsoPushConfigToWear = false,
                            )
                        }
                    },
                    onSemesterWeekStartDateChange = { date ->
                        if (semesterWeekStartDate != date) {
                            semesterWeekStartDate = date
                            persistSettings()
                            scheduleWeekSettingsAutoSync()
                            requestCloudSync(
                                trigger = CloudSyncContracts.TRIGGER_SETTINGS_CHANGED,
                                alsoPushConfigToWear = false,
                            )
                        }
                    },
                )

                SettingsPage.ReminderKeepAlive -> ReminderKeepAliveSettingsPage(
                    contentPadding = innerPadding,
                    reminderEnabled = reminderEnabled,
                    reminderMinutes = reminderMinutes,
                    keepAliveLevel = keepAliveLevel,
                    experimentalAccessibilityKeepAliveEnabled = experimentalAccessibilityKeepAliveEnabled,
                    keepAliveStatus = keepAliveStatusText,
                    onBack = {
                        handleBackNavigation()
                    },
                    onToggleReminder = { enabled ->
                        if (!enabled) {
                            reminderEnabled = false
                            parseMessage = context.getString(R.string.message_reminder_disabled)
                            persistSettings()
                            syncReminderWork()
                            requestCloudSync(
                                trigger = CloudSyncContracts.TRIGGER_SETTINGS_CHANGED,
                                alsoPushConfigToWear = false,
                            )
                        } else if (hasNotificationPermission(context)) {
                            reminderEnabled = true
                            parseMessage = context.getString(R.string.message_reminder_enabled)
                            persistSettings()
                            syncReminderWork()
                            requestCloudSync(
                                trigger = CloudSyncContracts.TRIGGER_SETTINGS_CHANGED,
                                alsoPushConfigToWear = false,
                            )
                        } else {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onReminderMinutesChange = {
                        reminderMinutes = it
                        persistSettings()
                        syncReminderWork()
                        requestCloudSync(
                            trigger = CloudSyncContracts.TRIGGER_SETTINGS_CHANGED,
                            alsoPushConfigToWear = false,
                        )
                    },
                    onKeepAliveLevelChange = {
                        keepAliveLevel = it
                        persistSettings()
                        syncReminderWork()
                        requestCloudSync(
                            trigger = CloudSyncContracts.TRIGGER_SETTINGS_CHANGED,
                            alsoPushConfigToWear = false,
                        )
                    },
                    onToggleExperimentalAccessibilityKeepAlive = {
                        experimentalAccessibilityKeepAliveEnabled = it
                        persistSettings()
                        syncReminderWork()
                    },
                    onOpenAccessibilitySettings = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
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
                    onRefreshKeepAliveStatus = {
                        keepAliveStatusTick += 1
                        syncReminderWork()
                        requestCloudSync(
                            trigger = CloudSyncContracts.TRIGGER_SETTINGS_CHANGED,
                            alsoPushConfigToWear = false,
                        )
                    },
                )

                SettingsPage.SyncCommunication -> SyncCommunicationSettingsPage(
                    contentPadding = innerPadding,
                    onBack = {
                        handleBackNavigation()
                    },
                    onOpenWearCommunicationPage = {
                        openSettingsPage(SettingsPage.WearCommunication)
                    },
                    onOpenCloudSyncPage = {
                        openSettingsPage(SettingsPage.CloudSync)
                    },
                )

                SettingsPage.WearCommunication -> WearCommunicationSettingsPage(
                    contentPadding = innerPadding,
                    wearSyncMode = wearSyncMode,
                    autoDetectedLabel = autoDetectedLabel,
                    autoEffectiveLabel = autoEffectiveLabel,
                    autoFallbackHint = autoFallbackHint,
                    wearConnectionMessage = wearConnectionMessage,
                    wearSyncMessage = wearSyncMessage,
                    wearSyncInProgress = wearSyncInProgress,
                    onBack = {
                        handleBackNavigation()
                    },
                    onWearSyncModeChange = { mode ->
                        wearSyncMode = mode
                        persistSettings()
                        requestCloudSync(
                            trigger = CloudSyncContracts.TRIGGER_SETTINGS_CHANGED,
                            alsoPushConfigToWear = false,
                        )
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

                SettingsPage.CloudSync -> CloudSyncSettingsPage(
                    contentPadding = innerPadding,
                    provider = cloudProvider,
                    enabled = cloudSyncEnabled,
                    serverUrl = cloudServerUrl,
                    remotePath = cloudRemotePath,
                    username = cloudUsername,
                    password = cloudPassword,
                    driveFileName = cloudDriveFileName,
                    driveConnected = driveConnected,
                    driveTokenExpireText = driveTokenExpireText,
                    showDriveCnWarning = showDriveCnWarning,
                    syncStatus = cloudSyncStatus,
                    configPushStatus = cloudConfigPushStatus,
                    lastSyncedAt = cloudLastSyncedAt,
                    syncInProgress = cloudSyncInProgress,
                    onBack = {
                        handleBackNavigation()
                    },
                    onProviderChange = { cloudProvider = it },
                    onEnabledChange = {
                        cloudSyncEnabled = it
                    },
                    onServerUrlChange = { cloudServerUrl = it },
                    onRemotePathChange = { cloudRemotePath = it },
                    onUsernameChange = { cloudUsername = it },
                    onPasswordChange = { cloudPassword = it },
                    onDriveFileNameChange = { cloudDriveFileName = it },
                    onConnectDrive = {
                        coroutineScope.launch {
                            cloudSyncInProgress = true
                            try {
                                if (BuildConfig.DRIVE_OAUTH_CLIENT_ID.isBlank()) {
                                    cloudSyncStatus = context.getString(R.string.settings_cloud_sync_drive_config_missing)
                                    persistSettings()
                                    return@launch
                                }
                                val authorization = GoogleDriveAuthManager.authorize(context)
                                if (authorization.isFailure) {
                                    cloudSyncStatus = context.getString(
                                        R.string.settings_cloud_sync_drive_auth_failed,
                                        authorization.exceptionOrNull()?.message ?: "unknown",
                                    )
                                    persistSettings()
                                    return@launch
                                }
                                val authResult = authorization.getOrThrow()
                                if (authResult.hasResolution()) {
                                    val pendingIntent = authResult.pendingIntent
                                    if (pendingIntent != null) {
                                        driveAuthorizationLauncher.launch(
                                            IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                                        )
                                        return@launch
                                    }
                                }
                                val token = GoogleDriveAuthManager.parseAccessToken(authResult).getOrThrow()
                                cloudDriveAccessToken = token.token
                                cloudDriveTokenExpireAt = token.expireAt
                                CloudCredentialStore.saveDriveAccessToken(context, token.token, token.expireAt)
                                cloudSyncStatus = context.getString(R.string.settings_cloud_sync_drive_connected)
                                cloudConfigPushStatus = ""
                                persistSettings()
                                requestCloudSync(
                                    trigger = CloudSyncContracts.TRIGGER_SETTINGS_CHANGED,
                                    force = true,
                                    alsoPushConfigToWear = true,
                                )
                            } catch (error: Throwable) {
                                cloudSyncStatus = context.getString(
                                    R.string.settings_cloud_sync_drive_auth_failed,
                                    error.message ?: "unknown",
                                )
                                persistSettings()
                            } finally {
                                cloudSyncInProgress = false
                            }
                        }
                    },
                    onDisconnectDrive = {
                        coroutineScope.launch {
                            cloudSyncInProgress = true
                            try {
                                GoogleDriveAuthManager.clearToken(context, cloudDriveAccessToken)
                                CloudCredentialStore.clearDriveAccessToken(context)
                                cloudDriveAccessToken = ""
                                cloudDriveTokenExpireAt = 0L
                                cloudSyncStatus = context.getString(R.string.settings_cloud_sync_drive_disconnected)
                                cloudConfigPushStatus = ""
                                persistSettings()
                                requestCloudSync(
                                    trigger = CloudSyncContracts.TRIGGER_SETTINGS_CHANGED,
                                    force = true,
                                    alsoPushConfigToWear = true,
                                )
                            } finally {
                                cloudSyncInProgress = false
                            }
                        }
                    },
                    onSave = {
                        if (cloudProvider == CloudProviderUi.WEBDAV) {
                            CloudCredentialStore.savePassword(context, cloudPassword)
                        }
                        cloudConfigPushStatus = ""
                        persistSettings()
                        requestCloudSync(
                            trigger = CloudSyncContracts.TRIGGER_SETTINGS_CHANGED,
                            force = true,
                            alsoPushConfigToWear = true,
                        )
                    },
                    onTestConnection = {
                        coroutineScope.launch {
                            cloudSyncInProgress = true
                            try {
                                if (cloudProvider == CloudProviderUi.WEBDAV) {
                                    CloudCredentialStore.savePassword(context, cloudPassword)
                                }
                                persistSettings()
                                val result = MobileCloudSyncCoordinator.testConnection(context)
                                cloudSyncStatus = when {
                                    result.isSuccess && cloudProvider == CloudProviderUi.WEBDAV -> "WebDAV connection OK"
                                    result.isSuccess && cloudProvider == CloudProviderUi.GOOGLE_DRIVE -> "Google Drive connection OK"
                                    cloudProvider == CloudProviderUi.WEBDAV -> "WebDAV connection failed: ${result.exceptionOrNull()?.message ?: "unknown"}"
                                    else -> "Google Drive connection failed: ${result.exceptionOrNull()?.message ?: "unknown"}"
                                }
                                persistSettings()
                            } finally {
                                cloudSyncInProgress = false
                            }
                        }
                    },
                    onSyncNow = {
                        if (cloudProvider == CloudProviderUi.WEBDAV) {
                            CloudCredentialStore.savePassword(context, cloudPassword)
                        }
                        persistSettings()
                        requestCloudSync(
                            trigger = CloudSyncContracts.TRIGGER_MANUAL,
                            force = true,
                            alsoPushConfigToWear = true,
                        )
                    },
                )

                SettingsPage.About -> AboutLayer(
                    contentPadding = innerPadding,
                    onBack = {
                        handleBackNavigation()
                    },
                )
            }
        }
        }
    }

    MobileDialogs(
        context = context,
        showImportConflictDialog = showImportConflictDialog,
        pendingImportConflicts = pendingImportConflicts,
        onDismissImportConflict = {
            showImportConflictDialog = false
            pendingImportConflicts = emptyList()
            parseMessage = context.getString(R.string.import_conflict_cancel_message)
            persistSettings()
        },
        onConfirmImportConflict = {
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
            importItemStates = emptyList()
            importPreviewSummary = null
            showImportConflictDialog = false
            persistSettings()
        },
        onCancelImportConflict = {
            showImportConflictDialog = false
            pendingImportConflicts = emptyList()
            parseMessage = context.getString(R.string.import_conflict_cancel_message)
            persistSettings()
        },
        showManualConflictDialog = showManualConflictDialog,
        pendingManualLesson = pendingManualLesson,
        pendingManualConflicts = pendingManualConflicts,
        onDismissManualConflict = {
            pendingManualLesson = null
            pendingManualConflicts = emptyList()
            showManualConflictDialog = false
            parseMessage = context.getString(R.string.manual_conflict_cancel_message)
            persistSettings()
        },
        onConfirmManualConflict = { lesson ->
            appendManualLesson(lesson)
            parseMessage = context.getString(R.string.manual_import_success_with_conflict_message, lesson.title)
            pendingManualLesson = null
            pendingManualConflicts = emptyList()
            showManualConflictDialog = false
            persistSettings()
        },
        onCancelManualConflict = {
            pendingManualLesson = null
            pendingManualConflicts = emptyList()
            showManualConflictDialog = false
            parseMessage = context.getString(R.string.manual_conflict_cancel_message)
            persistSettings()
        },
        editingLesson = editingLesson,
        onDismissEditLesson = { editingLesson = null },
        onSaveEditLesson = { updatedLesson, scope ->
            applyLessonEdit(updatedLesson, scope)
            editingLesson = null
        },
        onDeleteEditLesson = { lesson, scope ->
            removeLesson(lesson, scope)
            editingLesson = null
        },
        showRestoreConfirmDialog = showRestoreConfirmDialog,
        pendingRestoreLessons = pendingRestoreLessons,
        pendingRestoreWarnings = pendingRestoreWarnings,
        currentLessonsCount = lessons.size,
        onDismissRestore = {
            showRestoreConfirmDialog = false
            pendingRestoreLessons = emptyList()
            pendingRestoreWarnings = emptyList()
            parseMessage = context.getString(R.string.backup_restore_canceled_message)
            persistSettings()
        },
        onConfirmRestore = {
            lessons = com.xtawa.classingtime.screen.applyImportedLessons(pendingRestoreLessons)
            persistLessons()
            warnings = pendingRestoreWarnings
            parseMessage = context.getString(R.string.backup_restore_success_message, pendingRestoreLessons.size)
            pendingRestoreLessons = emptyList()
            pendingRestoreWarnings = emptyList()
            showRestoreConfirmDialog = false
            persistSettings()
        },
        onCancelRestore = {
            showRestoreConfirmDialog = false
            pendingRestoreLessons = emptyList()
            pendingRestoreWarnings = emptyList()
            parseMessage = context.getString(R.string.backup_restore_canceled_message)
            persistSettings()
        },
        showClearAllConfirmDialog = showClearAllConfirmDialog,
        onDismissClearAll = { showClearAllConfirmDialog = false },
        onConfirmClearAll = {
            lessons = emptyList()
            persistLessons()
            showClearAllConfirmDialog = false
            parseMessage = context.getString(R.string.danger_clear_success_message)
            persistSettings()
        },
        onCancelClearAll = {
            showClearAllConfirmDialog = false
            parseMessage = context.getString(R.string.danger_clear_canceled_message)
            persistSettings()
        },
    )
}

private data class MobileContentDestination(
    val layer: MobileLayer,
    val settingsPage: SettingsPage,
    val showImportJsonPromptPage: Boolean,
)

private enum class ContentTransitionDirection {
    Forward,
    Backward,
    None,
}

private fun resolveContentTransitionDirection(
    initialState: MobileContentDestination,
    targetState: MobileContentDestination,
): ContentTransitionDirection {
    val initialDepth = initialState.transitionDepth()
    val targetDepth = targetState.transitionDepth()
    return when {
        targetDepth > initialDepth -> ContentTransitionDirection.Forward
        targetDepth < initialDepth -> ContentTransitionDirection.Backward
        else -> ContentTransitionDirection.None
    }
}

private fun MobileContentDestination.transitionDepth(): Int {
    return when (layer) {
        MobileLayer.Schedule -> 0
        MobileLayer.Dashboard -> 50
        MobileLayer.Calendar -> 100
        MobileLayer.Settings -> when (settingsPage) {
            SettingsPage.Main -> 200
            SettingsPage.Import -> if (showImportJsonPromptPage) 400 else 300
            else -> 300 + settingsPage.ordinal
        }
    }
}

private fun parseWeekStartDay(raw: String): DayOfWeek {
    return when (raw.uppercase()) {
        DayOfWeek.SATURDAY.name -> DayOfWeek.SATURDAY
        DayOfWeek.SUNDAY.name -> DayOfWeek.SUNDAY
        else -> DayOfWeek.MONDAY
    }
}

private enum class SyncIndicatorState {
    Idle,
    Syncing,
    Success,
    Failed,
}

private fun resolveBluetoothSyncIndicatorState(
    syncInProgress: Boolean,
    syncMessage: String,
    latestAck: WearSyncAckInfo?,
    wearConnectedCount: Int,
    wearConnectionMessage: String,
): SyncIndicatorState {
    if (syncInProgress) return SyncIndicatorState.Syncing
    if (wearConnectionMessage.containsWearConnectionCheckingKeyword()) return SyncIndicatorState.Idle
    if (wearConnectedCount <= 0) return SyncIndicatorState.Failed
    if (syncMessage.containsSyncFailureKeyword()) return SyncIndicatorState.Failed
    return when (latestAck?.success) {
        true -> SyncIndicatorState.Success
        false -> SyncIndicatorState.Failed
        null -> SyncIndicatorState.Idle
    }
}

private fun resolveCloudSyncIndicatorState(
    syncInProgress: Boolean,
    syncStatus: String,
): SyncIndicatorState {
    if (syncInProgress) return SyncIndicatorState.Syncing
    if (syncStatus.containsSyncFailureKeyword()) return SyncIndicatorState.Failed
    if (syncStatus.containsSyncSuccessKeyword()) return SyncIndicatorState.Success
    return SyncIndicatorState.Idle
}

private fun String.containsSyncFailureKeyword(): Boolean {
    return contains("fail", ignoreCase = true) ||
        contains("error", ignoreCase = true) ||
        contains("失败") ||
        contains("失敗")
}

private fun String.containsSyncSuccessKeyword(): Boolean {
    return contains("success", ignoreCase = true) ||
        contains("ok", ignoreCase = true) ||
        contains("成功")
}

private fun String.containsWearConnectionCheckingKeyword(): Boolean {
    return contains("检测中") ||
        contains("檢測中") ||
        contains("checking", ignoreCase = true)
}

@Composable
private fun SyncStatusGroup(
    bluetoothState: SyncIndicatorState,
    cloudState: SyncIndicatorState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SyncStatusBadge(
            baseIcon = Icons.Filled.Bluetooth,
            state = bluetoothState,
        )
        SyncStatusBadge(
            baseIcon = Icons.Filled.Cloud,
            state = cloudState,
        )
    }
}

@Composable
private fun SyncStatusBadge(
    baseIcon: ImageVector,
    state: SyncIndicatorState,
    modifier: Modifier = Modifier,
) {
    val containerColor = when (state) {
        SyncIndicatorState.Success -> MaterialTheme.colorScheme.primaryContainer
        SyncIndicatorState.Failed -> MaterialTheme.colorScheme.errorContainer
        SyncIndicatorState.Syncing -> MaterialTheme.colorScheme.tertiaryContainer
        SyncIndicatorState.Idle -> MaterialTheme.colorScheme.secondaryContainer
    }
    val baseIconTint = when (state) {
        SyncIndicatorState.Success -> MaterialTheme.colorScheme.primary
        SyncIndicatorState.Failed -> MaterialTheme.colorScheme.error
        SyncIndicatorState.Syncing -> MaterialTheme.colorScheme.tertiary
        SyncIndicatorState.Idle -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val badgeIcon = when (state) {
        SyncIndicatorState.Success -> Icons.Filled.Check
        SyncIndicatorState.Failed -> Icons.Filled.Close
        SyncIndicatorState.Syncing -> Icons.Filled.Sync
        SyncIndicatorState.Idle -> null
    }
    val badgeColor = when (state) {
        SyncIndicatorState.Success -> MaterialTheme.colorScheme.primary
        SyncIndicatorState.Failed -> MaterialTheme.colorScheme.error
        SyncIndicatorState.Syncing -> MaterialTheme.colorScheme.tertiary
        SyncIndicatorState.Idle -> Color.Transparent
    }
    val badgeIconTint = when (state) {
        SyncIndicatorState.Success -> MaterialTheme.colorScheme.onPrimary
        SyncIndicatorState.Failed -> MaterialTheme.colorScheme.onError
        SyncIndicatorState.Syncing -> MaterialTheme.colorScheme.onTertiary
        SyncIndicatorState.Idle -> Color.Transparent
    }
    val badgeBorder = when (state) {
        SyncIndicatorState.Idle -> null
        else -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface)
    }

    Surface(
        modifier = modifier.size(32.dp),
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = baseIcon,
                contentDescription = null,
                tint = baseIconTint,
            )
            if (badgeIcon != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = badgeColor,
                    border = badgeBorder,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = null,
                            tint = badgeIconTint,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
            }
        }
    }
}
