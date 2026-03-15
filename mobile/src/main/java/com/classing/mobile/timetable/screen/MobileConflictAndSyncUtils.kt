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


internal fun detectLessonConflicts(lessons: List<LessonUi>): List<LessonConflict> {
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

internal fun findConflictsWithExisting(candidate: LessonUi, existing: List<LessonUi>): List<LessonUi> {
    return existing
        .asSequence()
        .filter { it.dayOfWeek == candidate.dayOfWeek && lessonsOverlap(it, candidate) }
        .sortedBy { it.startTime }
        .toList()
}

internal fun lessonsOverlap(first: LessonUi, second: LessonUi): Boolean {
    if (first.dayOfWeek != second.dayOfWeek) return false
    return first.startTime < second.endTime && second.startTime < first.endTime
}

internal fun formatLessonConflict(conflict: LessonConflict, context: Context): String {
    return context.getString(
        R.string.import_conflict_item,
        formatLessonSummary(conflict.first, context),
        formatLessonSummary(conflict.second, context),
    )
}

internal fun formatLessonSummary(lesson: LessonUi, context: Context): String {
    return context.getString(
        R.string.lesson_summary_format,
        dayLabel(lesson.dayOfWeek, context),
        lesson.startTime.format(clockFormatter),
        lesson.endTime.format(clockFormatter),
        lesson.title,
    )
}

internal fun buildScheduleBackupJson(lessons: List<LessonUi>, zoneId: ZoneId): String {
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

internal suspend fun fetchConnectedWearNodeCount(context: Context): Result<Int> {
    return runCatching {
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        nodes.size
    }
}

internal suspend fun syncLessonsToWear(
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

internal suspend fun syncLessonsViaWearOsApp(
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

internal fun findWearOsCompanionInfo(context: Context): WearOsCompanionInfo? {
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

internal fun isLeVersion(versionName: String): Boolean {
    return Regex("(^|[._-])le($|[._-])", RegexOption.IGNORE_CASE).containsMatchIn(versionName)
}

internal fun WearOsCompanionInfo.toDisplayLabel(): String {
    val variant = if (isChinaOrLe) "CN/LE" else "Global"
    return if (versionName.isBlank()) {
        "$packageName [$variant]"
    } else {
        "$packageName $versionName [$variant]"
    }
}

internal suspend fun handleStartedWearSync(
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

internal suspend fun awaitWearSyncAck(
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

internal fun formatWearSyncAckMessage(context: Context, ack: WearSyncAckInfo): String {
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

internal fun resolveWearSyncSourceLabel(context: Context, source: String): String {
    return when (source.uppercase()) {
        WearDataLayerContracts.SOURCE_WEARABLE_API -> context.getString(R.string.settings_wear_sync_mode_wearable_api)
        WearDataLayerContracts.SOURCE_WEAROS_APP -> context.getString(R.string.settings_wear_sync_mode_wearos_app)
        else -> source.ifBlank { "unknown" }
    }
}

internal fun parseJsonToLessons(raw: String, context: Context): JsonParseOutcome {
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



