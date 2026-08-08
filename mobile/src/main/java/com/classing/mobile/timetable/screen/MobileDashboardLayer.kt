package com.xtawa.classingtime.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.classing.shared.time.nextMinuteDelay
import com.classing.shared.ui.heatmap.HeatmapLessonInput
import com.classing.shared.ui.heatmap.buildHeatmapCells
import com.xtawa.classingtime.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun DashboardLayer(
    contentPadding: PaddingValues,
    lessons: List<LessonUi>,
    visibleDays: List<DayOfWeek>,
    lessonsByDay: Map<DayOfWeek, List<LessonUi>>,
    currentWeekLessonsByDay: Map<DayOfWeek, List<LessonUi>>,
    onOpenAskAi: () -> Unit,
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val current = LocalDateTime.now()
            now = current
            delay(nextMinuteDelay(current).toMillis())
        }
    }

    val today = now.toLocalDate()
    val defaultSelectedDay = remember(visibleDays, today.dayOfWeek) {
        if (visibleDays.contains(today.dayOfWeek)) today.dayOfWeek else visibleDays.firstOrNull() ?: DayOfWeek.MONDAY
    }
    var selectedDay by remember(visibleDays, today.dayOfWeek) { mutableStateOf(defaultSelectedDay) }

    LaunchedEffect(visibleDays, defaultSelectedDay) {
        if (!visibleDays.contains(selectedDay)) {
            selectedDay = defaultSelectedDay
        }
    }

    val visibleLessons = remember(lessons, visibleDays) {
        lessons.filter { visibleDays.contains(it.dayOfWeek) }
    }
    val heatmapCells = remember(visibleLessons) {
        buildHeatmapCells(
            visibleLessons.map { lesson ->
                HeatmapLessonInput(
                    dayOfWeek = lesson.dayOfWeek,
                    startTime = lesson.startTime,
                    endTime = lesson.endTime,
                )
            },
        )
    }
    val dayCounts = remember(visibleDays, lessonsByDay) {
        visibleDays.associateWith { day -> lessonsByDay[day].orEmpty().size }
    }
    val activeDayCount = dayCounts.count { it.value > 0 }
    val busiestDay = dayCounts.maxWithOrNull(
        compareBy<Map.Entry<DayOfWeek, Int>> { it.value }.thenByDescending { it.key.value },
    )?.takeIf { it.value > 0 }
    val todayLessons = remember(currentWeekLessonsByDay, today.dayOfWeek) {
        currentWeekLessonsByDay[today.dayOfWeek].orEmpty().sortedBy { it.startTime }
    }
    val remainingTodayLessons = remember(todayLessons, today, now) {
        todayLessons.filter { lesson ->
            LocalDateTime.of(today, lesson.endTime).isAfter(now)
        }
    }
    val selectedDayLessons = remember(lessonsByDay, selectedDay) {
        lessonsByDay[selectedDay].orEmpty().sortedBy { it.startTime }
    }
    val currentLesson = remember(todayLessons, today, now) {
        todayLessons.firstOrNull { lesson ->
            val startAt = LocalDateTime.of(today, lesson.startTime)
            val endAt = LocalDateTime.of(today, lesson.endTime)
            !now.isBefore(startAt) && now.isBefore(endAt)
        }
    }
    val nextTodayLesson = remember(todayLessons, today, now) {
        todayLessons.firstOrNull { lesson ->
            LocalDateTime.of(today, lesson.startTime).isAfter(now)
        }
    }
    val nextLesson = remember(currentWeekLessonsByDay, now) {
        resolveNextLessonForBoard(
            lessonsForDate = { date: LocalDate -> currentWeekLessonsByDay[date.dayOfWeek].orEmpty() },
            now = now,
        )
    }
    val statusText = when {
        currentLesson != null -> stringResource(R.string.dashboard_status_in_progress, currentLesson.title)
        nextTodayLesson != null -> stringResource(
            R.string.dashboard_status_next,
            nextTodayLesson.title,
            nextTodayLesson.startTime.format(clockFormatter),
        )
        todayLessons.isNotEmpty() -> stringResource(R.string.dashboard_status_done)
        else -> stringResource(R.string.dashboard_status_free)
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
        Column(
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = formatDateHeader(today),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.layer_heatmap),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.dashboard_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(onClick = onOpenAskAi, modifier = Modifier.fillMaxWidth()) {
            Text("Ask AI · 课表助手")
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.dashboard_summary_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashboardMetricCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.today_classes_title),
                        value = todayLessons.size.toString(),
                    )
                    DashboardMetricCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.dashboard_today_remaining_count_title),
                        value = remainingTodayLessons.size.toString(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashboardMetricCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.dashboard_active_days_title),
                        value = activeDayCount.toString(),
                    )
                    DashboardMetricCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.dashboard_busiest_day_title),
                        value = busiestDay?.let { "${dayLabel(it.key, context)} · ${it.value}" }
                            ?: stringResource(R.string.dashboard_busiest_day_empty),
                    )
                }
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.dashboard_status_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        DashboardNextLessonCard(nextLesson = nextLesson, hasSchedule = lessons.isNotEmpty(), now = now)

        DashboardSectionTitle(title = stringResource(R.string.dashboard_today_remaining_title))
        if (remainingTodayLessons.isEmpty()) {
            DashboardEmptyCard(message = stringResource(R.string.dashboard_today_remaining_empty))
        } else {
            remainingTodayLessons.forEach { lesson ->
                LessonCard(lesson = lesson)
            }
        }

        DashboardSectionTitle(title = stringResource(R.string.day_filter_title))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visibleDays) { day ->
                val count = lessonsByDay[day].orEmpty().size
                FilterChip(
                    selected = day == selectedDay,
                    onClick = { selectedDay = day },
                    label = { Text(stringResource(R.string.day_chip_label, dayLabel(day, context), count)) },
                )
            }
        }

        DashboardSectionTitle(
            title = stringResource(
                R.string.dashboard_day_schedule_title,
                dayLabel(selectedDay, context),
                selectedDayLessons.size,
            ),
        )
        if (selectedDayLessons.isEmpty()) {
            DashboardEmptyCard(message = stringResource(R.string.dashboard_day_schedule_empty, dayLabel(selectedDay, context)))
        } else {
            selectedDayLessons.forEach { lesson ->
                LessonCard(lesson = lesson)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.heatmap_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.heatmap_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (heatmapCells.isEmpty()) {
                    DashboardEmptyCard(message = stringResource(R.string.heatmap_empty_desc))
                } else {
                    CourseHeatmapGrid(
                        cells = heatmapCells,
                        cellSize = 16.dp,
                        cellSpacing = 4.dp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.heatmap_legend_less),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        repeat(4) { level ->
                            val color = when (level) {
                                0 -> MaterialTheme.colorScheme.surfaceContainerLow
                                1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.60f)
                            }
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(color, RoundedCornerShape(4.dp)),
                            )
                        }
                        Text(
                            text = stringResource(R.string.heatmap_legend_more),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardNextLessonCard(
    nextLesson: UpcomingLessonForBoard?,
    hasSchedule: Boolean,
    now: LocalDateTime,
) {
    val countdown = when {
        nextLesson == null -> ""
        !now.isBefore(nextLesson.startAt) && now.isBefore(nextLesson.endAt) ->
            stringResource(R.string.schedule_next_lesson_countdown_in_progress)
        else -> {
            val minutes = java.time.Duration.between(now, nextLesson.startAt).toMinutes().coerceAtLeast(0L)
            when {
                minutes <= 0L -> stringResource(R.string.schedule_next_lesson_countdown_soon)
                minutes >= 60L -> stringResource(
                    R.string.schedule_next_lesson_countdown_in_hours_minutes,
                    minutes / 60L,
                    minutes % 60L,
                )
                else -> stringResource(R.string.schedule_next_lesson_countdown_in_minutes, minutes)
            }
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f))) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.schedule_next_lesson_title), color = MaterialTheme.colorScheme.primary)
            if (nextLesson == null) {
                Text(
                    if (hasSchedule) stringResource(R.string.schedule_next_lesson_empty)
                    else stringResource(R.string.schedule_next_lesson_no_data),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(nextLesson.lesson.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatDateHeader(nextLesson.startAt.toLocalDate())} · ${nextLesson.lesson.startTime.format(clockFormatter)}-${nextLesson.lesson.endTime.format(clockFormatter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (countdown.isNotBlank()) Text(countdown, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun DashboardSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun DashboardMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DashboardEmptyCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Text(
            text = message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
