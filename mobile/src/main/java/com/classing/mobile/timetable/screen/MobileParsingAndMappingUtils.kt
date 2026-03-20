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


internal fun parseLessonArray(raw: String): JSONArray? {
    return runCatching { JSONArray(raw) }.getOrNull()
        ?: runCatching {
            val root = JSONObject(raw)
            root.optJSONArray("courses")
                ?: root.optJSONArray("lessons")
                ?: root.optJSONArray("events")
        }.getOrNull()
}

internal fun parseJsonDayOfWeek(raw: Any?): DayOfWeek? {
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

internal fun parseFlexibleTime(raw: String?): LocalTime? {
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

internal fun parseToLessons(
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

internal fun LessonUi.toPersistedLesson(): PersistedLesson {
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

internal fun PersistedLesson.toLessonUi(): LessonUi {
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

internal fun CourseDraft.toLessonUi(index: Int, zoneId: ZoneId, untitled: String): LessonUi? {
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

internal fun parseManualTime(raw: String): LocalTime? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    return runCatching {
        LocalTime.parse(trimmed, DateTimeFormatter.ofPattern("H:mm"))
    }.getOrNull()
}

internal fun dayLabel(dayOfWeek: DayOfWeek, context: Context): String {
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

internal fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

internal fun MobileLayer.labelRes(): Int {
    return when (this) {
        MobileLayer.Schedule -> R.string.layer_dashboard
        MobileLayer.Calendar -> R.string.layer_calendar
        MobileLayer.Settings -> R.string.layer_settings
    }
}

internal val clockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
internal fun MobileTimetablePreview() {
    MobileTimetableScreen()
}


