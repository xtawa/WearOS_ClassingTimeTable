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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
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


// Grid metrics for the weekly board. The row height fits a two-line course name plus a
// classroom line. Day columns are measured at layout time, so the value below is only the
// point where a column stops being readable and the board falls back to scrolling.
private val WeekGridGutterWidth = 54.dp
private val WeekGridMinColumnWidth = 64.dp
private val WeekGridHeaderHeight = 34.dp
private val WeekGridRowHeight = 74.dp

// Formats the visible week range above the grid, for example "08.10 - 08.16".
private val boardWeekRangeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM.dd")

// Course blocks are tinted from this fixed accent set so the same course keeps the same color
// across weeks without storing a color on the lesson.
private val WeekGridAccents = listOf(
    Color(0xFF8C9BFF),
    Color(0xFF4FD8C4),
    Color(0xFFFFB454),
    Color(0xFFFF8FAE),
    Color(0xFF66D19E),
    Color(0xFFB79BFF),
)

private fun courseAccentColor(key: String): Color {
    if (key.isBlank()) return WeekGridAccents.first()
    val hash = key.hashCode()
    val normalized = if (hash == Int.MIN_VALUE) 0 else if (hash < 0) -hash else hash
    return WeekGridAccents[normalized % WeekGridAccents.size]
}

/**
 * The default schedule board. The oversized ghost wordmark that used to sit here was removed:
 * the app bar already carries the brand and the week number, so the first thing on screen is
 * now the next lesson, followed by the weekday grid and the per day breakdown.
 */
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
    val today = LocalDate.now()
    val todayDay = today.dayOfWeek
    val weekStart = today.minusDays((todayDay.value - 1).toLong())
    val weekRangeLabel = weekStart.format(boardWeekRangeFormatter) +
        " - " +
        weekStart.plusDays(6).format(boardWeekRangeFormatter)

    // The countdown has to stay truthful without burning battery, so the clock is re-read on a
    // slow tick rather than on every recomposition.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = LocalDateTime.now()
        }
    }
    val nextLesson = resolveNextLessonForBoard(lessonsForDate, now)

    val prioritizedDays = remember(visibleDays, todayDay) {
        if (visibleDays.contains(todayDay)) {
            listOf(todayDay) + visibleDays.filterNot { it == todayDay }
        } else {
            visibleDays
        }
    }
    // Every distinct start time across the visible days becomes one grid row, so the grid keeps
    // its shape no matter how irregular the periods are.
    val slotStarts = remember(visibleDays, lessonsByDay) {
        visibleDays
            .flatMap { day -> lessonsByDay[day].orEmpty() }
            .map { lesson -> lesson.startTime }
            .distinct()
            .sorted()
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MobileNextLessonHeroCard(
                nextLesson = nextLesson,
                hasSchedule = hasSchedule,
                now = now,
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = weekRangeLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.week_long_press_hint),
                        style = MaterialTheme.typography.labelSmall,
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
        item {
            WeekGridBoard(
                visibleDays = visibleDays,
                lessonsByDay = lessonsByDay,
                slotStarts = slotStarts,
                todayDay = todayDay,
                onLongPressLesson = onLongPressLesson,
            )
        }
        items(prioritizedDays) { day ->
            val lessons = lessonsByDay[day].orEmpty().sortedBy { it.startTime }
            val isEmpty = lessons.isEmpty()
            val isToday = day == todayDay
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isToday) {
                            Box(
                                modifier = Modifier
                                    .size(width = 3.dp, height = 14.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(999.dp),
                                    ),
                            )
                        }
                        Text(
                            text = stringResource(R.string.day_header_title, dayLabel(day, context), lessons.size),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isToday) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    if (isEmpty) {
                        Text(
                            text = stringResource(R.string.no_classes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        lessons.forEach { lesson ->
                            val lessonSummary = stringResource(
                                R.string.lesson_summary_format,
                                dayLabel(lesson.dayOfWeek, context),
                                lesson.startTime.format(clockFormatter),
                                lesson.endTime.format(clockFormatter),
                                lesson.title,
                            )
                            val editLessonLabel = stringResource(R.string.lesson_edit_dialog_title)
                            val accent = courseAccentColor(lesson.title)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics {
                                        contentDescription = lessonSummary
                                        onLongClick(label = editLessonLabel) {
                                            onLongPressLesson(lesson)
                                            true
                                        }
                                    }
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
                                            color = accent,
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
                                            .size(width = 3.dp, height = 30.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(accent, RoundedCornerShape(999.dp)),
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

/**
 * The weekly grid board: weekdays run along the x axis, periods along the y axis, and each
 * course is a tinted block. The board scrolls horizontally so a six or seven day week still
 * keeps a readable column width on a phone.
 */
@Composable
private fun WeekGridBoard(
    visibleDays: List<DayOfWeek>,
    lessonsByDay: Map<DayOfWeek, List<LessonUi>>,
    slotStarts: List<LocalTime>,
    todayDay: DayOfWeek,
    onLongPressLesson: (LessonUi) -> Unit,
) {
    val context = LocalContext.current
    val horizontalScrollState = rememberScrollState()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        if (slotStarts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 28.dp, height = 3.dp)
                        .background(
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(999.dp),
                        ),
                )
                Text(
                    text = stringResource(R.string.no_classes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.schedule_next_lesson_no_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Card
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Day columns stretch to fill the width that is actually available, so a wide
            // screen no longer leaves dead space beside a grid frozen at 96 dp per day.
            // Horizontal scrolling only engages when the visible days genuinely cannot fit
            // at the minimum readable column width.
            val fittedColumnWidth = if (visibleDays.isEmpty()) {
                WeekGridMinColumnWidth
            } else {
                (maxWidth - WeekGridGutterWidth) / visibleDays.size
            }
            val needsHorizontalScroll = fittedColumnWidth < WeekGridMinColumnWidth
            val columnWidth = if (needsHorizontalScroll) {
                WeekGridMinColumnWidth
            } else {
                fittedColumnWidth
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (needsHorizontalScroll) {
                            Modifier.horizontalScroll(horizontalScrollState)
                        } else {
                            Modifier
                        },
                    )
                    .padding(vertical = 8.dp),
            ) {
                // Time gutter. Period index on top, start and end time below, all in the monospace
                // label role so the numbers line up row to row.
                Column {
                    Box(modifier = Modifier.size(width = WeekGridGutterWidth, height = WeekGridHeaderHeight))
                    slotStarts.forEachIndexed { index, start ->
                        val slotEnd = visibleDays
                            .flatMap { day -> lessonsByDay[day].orEmpty() }
                            .filter { lesson -> lesson.startTime == start }
                            .maxByOrNull { lesson -> lesson.endTime }
                            ?.endTime
                        Column(
                            modifier = Modifier
                                .size(width = WeekGridGutterWidth, height = WeekGridRowHeight)
                                .padding(end = 6.dp),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = start.format(clockFormatter),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (slotEnd != null) {
                                Text(
                                    text = slotEnd.format(clockFormatter),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }
                visibleDays.forEach { day ->
                    val isToday = day == todayDay
                    val dayLessons = lessonsByDay[day].orEmpty()
                    Column(
                        modifier = Modifier
                            .width(columnWidth)
                            .background(
                                color = if (isToday) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(12.dp),
                            ),
                    ) {
                        Box(
                            modifier = Modifier.size(width = columnWidth, height = WeekGridHeaderHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = dayLabel(day, context),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isToday) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                        slotStarts.forEach { start ->
                            val matches = dayLessons.filter { lesson -> lesson.startTime == start }
                            WeekGridCell(
                                lessons = matches,
                                columnWidth = columnWidth,
                                onLongPressLesson = onLongPressLesson,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One cell of the weekly grid. An empty slot stays a faint placeholder so the grid keeps its
 * rhythm; a filled slot shows the course name, the classroom and its start time. Long press
 * still opens the same edit flow as the list rows, and overlapping courses are surfaced with a
 * counter instead of being hidden.
 */
@Composable
private fun WeekGridCell(
    lessons: List<LessonUi>,
    columnWidth: Dp,
    onLongPressLesson: (LessonUi) -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(width = columnWidth, height = WeekGridRowHeight)
            .padding(4.dp),
    ) {
        val lesson = lessons.firstOrNull()
        if (lesson == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp),
                    ),
            )
        } else {
            val accent = courseAccentColor(lesson.title)
            val lessonSummary = stringResource(
                R.string.lesson_summary_format,
                dayLabel(lesson.dayOfWeek, context),
                lesson.startTime.format(clockFormatter),
                lesson.endTime.format(clockFormatter),
                lesson.title,
            )
            val editLessonLabel = stringResource(R.string.lesson_edit_dialog_title)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = accent.copy(alpha = 0.18f), shape = RoundedCornerShape(12.dp))
                    .semantics {
                        contentDescription = lessonSummary
                        onLongClick(label = editLessonLabel) {
                            onLongPressLesson(lesson)
                            true
                        }
                    }
                    .pointerInput(lesson.id) {
                        detectTapGestures(
                            onLongPress = { onLongPressLesson(lesson) },
                        )
                    }
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = lesson.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!lesson.location.isNullOrBlank()) {
                    Text(
                        text = lesson.location,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = lesson.startTime.format(clockFormatter),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                    )
                    if (lessons.size > 1) {
                        Text(
                            text = "+" + (lessons.size - 1).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The hero card that now opens the schedule board. It answers the only question that matters
 * when the app is opened between classes: what is next, where, and how long until it starts.
 */
@Composable
private fun MobileNextLessonHeroCard(
    nextLesson: UpcomingLessonForBoard?,
    hasSchedule: Boolean,
    now: LocalDateTime,
) {
    val context = LocalContext.current
    val inProgress = nextLesson != null &&
        !now.isBefore(nextLesson.startAt) &&
        now.isBefore(nextLesson.endAt)
    val countdown = if (nextLesson == null) {
        ""
    } else if (inProgress) {
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
    val accent = if (nextLesson == null) {
        MaterialTheme.colorScheme.outline
    } else {
        courseAccentColor(nextLesson.lesson.title)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 68.dp)
                    .background(accent, RoundedCornerShape(999.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.schedule_next_lesson_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                    if (countdown.isNotBlank()) {
                        Text(
                            text = countdown,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (inProgress) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                accent
                            },
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(
                                    color = if (inProgress) {
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                                    } else {
                                        accent.copy(alpha = 0.16f)
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                )
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
                if (nextLesson == null) {
                    Text(
                        text = if (hasSchedule) {
                            stringResource(R.string.schedule_next_lesson_empty)
                        } else {
                            stringResource(R.string.schedule_next_lesson_no_data)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = nextLesson.lesson.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!nextLesson.lesson.location.isNullOrBlank()) {
                        Text(
                            text = nextLesson.lesson.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val dayText = dayLabel(nextLesson.startAt.dayOfWeek, context)
                    val timeRange = stringResource(
                        R.string.time_range_text,
                        nextLesson.lesson.startTime.format(clockFormatter),
                        nextLesson.lesson.endTime.format(clockFormatter),
                    )
                    Text(
                        text = dayText + "  " + timeRange,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                text = stringResource(R.string.import_page_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
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
