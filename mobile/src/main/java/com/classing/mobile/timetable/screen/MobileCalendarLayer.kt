package com.xtawa.classingtime.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xtawa.classingtime.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
internal fun CalendarMonthLayer(
    contentPadding: PaddingValues,
    lessonsByDay: Map<DayOfWeek, List<LessonUi>>,
) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val today = LocalDate.now()
    val currentMonth = YearMonth.from(today)
    val monthFormatter = remember(locale) { DateTimeFormatter.ofPattern("yyyy-MM", locale) }
    val dayTitleFormatter = remember(locale) { DateTimeFormatter.ofPattern("MM-dd", locale) }
    var displayedMonth by remember { mutableStateOf(currentMonth) }

    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val offsetToWeekStart = (7 + (today.dayOfWeek.value - firstDayOfWeek.value)) % 7
    val weekStart = today.minusDays(offsetToWeekStart.toLong())
    val weekEnd = weekStart.plusDays(6)

    val monthDates = remember(displayedMonth) {
        (1..displayedMonth.lengthOfMonth()).map { displayedMonth.atDay(it) }
    }
    val isCurrentDisplayedMonth = displayedMonth == currentMonth
    val upcomingDates = if (isCurrentDisplayedMonth) {
        monthDates.filter { !it.isBefore(today) }
    } else {
        monthDates
    }
    val pastDates = if (isCurrentDisplayedMonth) {
        monthDates.filter { it.isBefore(today) }
    } else {
        emptyList()
    }

    var expandedState by remember(displayedMonth, weekStart, weekEnd) {
        mutableStateOf(monthDates.associateWith { date -> isWithinWeek(date, weekStart, weekEnd) })
    }
    var pastDaysExpanded by remember(displayedMonth, today) { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text(
                        text = String.format(locale, "%02d", displayedMonth.monthValue),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.90f),
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = displayedMonth.atDay(1).format(monthFormatter),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { displayedMonth = displayedMonth.minusMonths(1) },
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Text(text = stringResource(R.string.calendar_prev_month))
                    }
                    Button(
                        onClick = { displayedMonth = displayedMonth.plusMonths(1) },
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Text(text = stringResource(R.string.calendar_next_month))
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            ) {
                Text(
                    text = stringResource(R.string.calendar_timeline_hint),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(upcomingDates, key = { it.toString() }) { date ->
            val dayLessons = lessonsByDay[date.dayOfWeek].orEmpty().sortedBy { it.startTime }
            val expanded = expandedState[date] ?: false
            TimelineDayCard(
                date = date,
                today = today,
                weekStart = weekStart,
                weekEnd = weekEnd,
                dayTitle = stringResource(
                    R.string.calendar_timeline_day_title,
                    dayLabel(date.dayOfWeek, context),
                    date.format(dayTitleFormatter),
                ),
                dayLessons = dayLessons,
                expanded = expanded,
                onToggle = {
                    val current = expandedState[date] ?: false
                    expandedState = expandedState + (date to !current)
                },
            )
        }

        if (pastDates.isNotEmpty()) {
            item(key = "past-days-group") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.calendar_past_days_group_title, pastDates.size),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            TextButton(onClick = { pastDaysExpanded = !pastDaysExpanded }) {
                                Text(
                                    text = if (pastDaysExpanded) {
                                        stringResource(R.string.calendar_collapse_day)
                                    } else {
                                        stringResource(R.string.calendar_expand_day)
                                    },
                                )
                            }
                        }

                        if (pastDaysExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                pastDates.asReversed().forEach { date ->
                                    val dayLessons = lessonsByDay[date.dayOfWeek].orEmpty().sortedBy { it.startTime }
                                    val expanded = expandedState[date] ?: false
                                    TimelineDayCard(
                                        date = date,
                                        today = today,
                                        weekStart = weekStart,
                                        weekEnd = weekEnd,
                                        dayTitle = stringResource(
                                            R.string.calendar_timeline_day_title,
                                            dayLabel(date.dayOfWeek, context),
                                            date.format(dayTitleFormatter),
                                        ),
                                        dayLessons = dayLessons,
                                        expanded = expanded,
                                        onToggle = {
                                            val current = expandedState[date] ?: false
                                            expandedState = expandedState + (date to !current)
                                        },
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

@Composable
private fun TimelineDayCard(
    date: LocalDate,
    today: LocalDate,
    weekStart: LocalDate,
    weekEnd: LocalDate,
    dayTitle: String,
    dayLessons: List<LessonUi>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val inCurrentWeek = isWithinWeek(date, weekStart, weekEnd)
    val isToday = date == today

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (expanded) {
                MaterialTheme.colorScheme.surfaceContainerLowest
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = BorderStroke(
            width = if (isToday) 1.2.dp else 1.dp,
            color = if (isToday) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = CircleShape,
                        color = when {
                            isToday -> MaterialTheme.colorScheme.primary
                            inCurrentWeek -> MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                    ) {}
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = dayTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (inCurrentWeek) {
                            Text(
                                text = stringResource(R.string.calendar_this_week_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                TextButton(onClick = onToggle) {
                    Text(
                        text = if (expanded) {
                            stringResource(R.string.calendar_collapse_day)
                        } else {
                            stringResource(R.string.calendar_expand_day)
                        },
                    )
                }
            }

            if (expanded) {
                if (dayLessons.isEmpty()) {
                    Text(
                        text = stringResource(R.string.calendar_no_classes_on_date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        dayLessons.forEach { lesson ->
                            TimelineLessonRow(lesson = lesson)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineLessonRow(lesson: LessonUi) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = lesson.startTime.format(clockFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = lesson.endTime.format(clockFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = lesson.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!lesson.location.isNullOrBlank()) {
                    Text(
                        text = lesson.location,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun isWithinWeek(date: LocalDate, weekStart: LocalDate, weekEnd: LocalDate): Boolean {
    return !date.isBefore(weekStart) && !date.isAfter(weekEnd)
}
