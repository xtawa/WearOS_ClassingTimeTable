package com.xtawa.classingtime.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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

/**
 * The calendar sub view. The month grid comes first and the day timeline follows it, so the
 * month is scannable before any day is expanded. Every callback the timetable relies on is
 * unchanged.
 */
@Composable
internal fun CalendarMonthLayer(
    contentPadding: PaddingValues,
    occurrenceProvider: (LocalDate) -> List<EffectiveLessonOccurrence>,
    cancelledExceptionProvider: (LocalDate) -> List<ScheduleExceptionUi>,
    onBackToTimetable: () -> Unit,
    onEditOccurrence: (EffectiveLessonOccurrence, LocalDate) -> Unit,
    onAddMakeUpLesson: (LocalDate) -> Unit,
    onRestoreOriginal: (String, LocalDate) -> Unit,
) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val today = LocalDate.now()
    val currentMonth = YearMonth.from(today)
    val monthFormatter = remember(locale) { DateTimeFormatter.ofPattern("yyyy-MM", locale) }
    val dayTitleFormatter = remember(locale) { DateTimeFormatter.ofPattern("MM-dd", locale) }
    var displayedMonth by remember { mutableStateOf(currentMonth) }
    var selectedDate by remember { mutableStateOf(today) }

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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = displayedMonth.atDay(1).format(monthFormatter),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = String.format(locale, "%02d", displayedMonth.monthValue),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CalendarNavButton(
                        label = stringResource(R.string.calendar_prev_month),
                        onClick = { displayedMonth = displayedMonth.minusMonths(1) },
                    )
                    CalendarNavButton(
                        label = stringResource(R.string.calendar_next_month),
                        onClick = { displayedMonth = displayedMonth.plusMonths(1) },
                    )
                    Button(
                        onClick = onBackToTimetable,
                        modifier = Modifier.heightIn(min = 44.dp),
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.calendar_back_to_timetable),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        item(key = "month-grid-$displayedMonth") {
            MonthDateGrid(
                displayedMonth = displayedMonth,
                firstDayOfWeek = firstDayOfWeek,
                today = today,
                selectedDate = selectedDate,
                occurrenceProvider = occurrenceProvider,
                onSelect = { date ->
                    selectedDate = date
                    expandedState = expandedState + (date to true)
                    if (date.isBefore(today)) pastDaysExpanded = true
                },
            )
        }

        item {
            Text(
                text = stringResource(R.string.calendar_timeline_hint),
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(upcomingDates, key = { it.toString() }) { date ->
            val dayLessons = occurrenceProvider(date).sortedBy { it.lesson.startTime }
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
                onEditOccurrence = onEditOccurrence,
                onAddMakeUpLesson = onAddMakeUpLesson,
                cancelledExceptions = cancelledExceptionProvider(date),
                onRestoreOriginal = onRestoreOriginal,
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
                                    val dayLessons = occurrenceProvider(date).sortedBy { it.lesson.startTime }
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
                                        onEditOccurrence = onEditOccurrence,
                                        onAddMakeUpLesson = onAddMakeUpLesson,
                                        cancelledExceptions = cancelledExceptionProvider(date),
                                        onRestoreOriginal = onRestoreOriginal,
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
private fun CalendarNavButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(999.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun MonthDateGrid(
    displayedMonth: YearMonth,
    firstDayOfWeek: DayOfWeek,
    today: LocalDate,
    selectedDate: LocalDate,
    occurrenceProvider: (LocalDate) -> List<EffectiveLessonOccurrence>,
    onSelect: (LocalDate) -> Unit,
) {
    val cells = buildMonthGridDates(displayedMonth, firstDayOfWeek)
    val dateSemanticsFormatter = remember { DateTimeFormatter.ISO_LOCAL_DATE }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (week + List(7 - week.size) { null }).forEach { date ->
                        val isSelected = date == selectedDate
                        val isToday = date == today
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .then(
                                    if (date != null) {
                                        Modifier
                                            .semantics {
                                                contentDescription = date.format(dateSemanticsFormatter)
                                                selected = isSelected
                                            }
                                            .clickable(role = Role.Button) { onSelect(date) }
                                    } else {
                                        Modifier
                                    },
                                ),
                            shape = CircleShape,
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                isToday -> MaterialTheme.colorScheme.surfaceContainerHighest
                                else -> Color.Transparent
                            },
                        ) {
                            Box(Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                                if (date != null) {
                                    val hasLessons = occurrenceProvider(date).isNotEmpty()
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Text(
                                            text = date.dayOfMonth.toString(),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isToday || isSelected) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Normal
                                            },
                                            color = when {
                                                isSelected -> MaterialTheme.colorScheme.primary
                                                isToday -> MaterialTheme.colorScheme.onSurface
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .background(
                                                    color = if (hasLessons) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        Color.Transparent
                                                    },
                                                    shape = CircleShape,
                                                ),
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

internal fun buildMonthGridDates(displayedMonth: YearMonth, firstDayOfWeek: DayOfWeek): List<LocalDate?> {
    val leading = (7 + displayedMonth.atDay(1).dayOfWeek.value - firstDayOfWeek.value) % 7
    val cells: List<LocalDate?> = List(leading) { null } +
        (1..displayedMonth.lengthOfMonth()).map(displayedMonth::atDay)
    val trailing = (7 - cells.size % 7) % 7
    return cells + List(trailing) { null }
}

@Composable
private fun TimelineDayCard(
    date: LocalDate,
    today: LocalDate,
    weekStart: LocalDate,
    weekEnd: LocalDate,
    dayTitle: String,
    dayLessons: List<EffectiveLessonOccurrence>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEditOccurrence: (EffectiveLessonOccurrence, LocalDate) -> Unit,
    onAddMakeUpLesson: (LocalDate) -> Unit,
    cancelledExceptions: List<ScheduleExceptionUi>,
    onRestoreOriginal: (String, LocalDate) -> Unit,
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
                TextButton(onClick = { onAddMakeUpLesson(date) }) {
                    Text(text = stringResource(R.string.calendar_add_makeup_lesson))
                }
                if (dayLessons.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 16.dp, height = 3.dp)
                                .background(
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(999.dp),
                                ),
                        )
                        Text(
                            text = stringResource(R.string.calendar_no_classes_on_date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        dayLessons.forEach { occurrence ->
                            TimelineLessonRow(
                                occurrence = occurrence,
                                onClick = { onEditOccurrence(occurrence, date) },
                            )
                            if (occurrence.origin == EffectiveLessonOrigin.RESCHEDULED && occurrence.sourceLessonId != null) {
                                TextButton(onClick = { onRestoreOriginal(occurrence.sourceLessonId, date) }) {
                                    Text(stringResource(R.string.calendar_restore_original))
                                }
                            }
                        }
                    }
                }
                cancelledExceptions.forEach { exception ->
                    val lessonId = exception.lessonId ?: return@forEach
                    TextButton(onClick = { onRestoreOriginal(lessonId, date) }) {
                        Text(stringResource(R.string.calendar_restore_cancelled_original))
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineLessonRow(
    occurrence: EffectiveLessonOccurrence,
    onClick: () -> Unit,
) {
    val lesson = occurrence.lesson
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
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
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 30.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                        RoundedCornerShape(999.dp),
                    ),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = lesson.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
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
                val badge = when (occurrence.origin) {
                    EffectiveLessonOrigin.BASE -> null
                    EffectiveLessonOrigin.RESCHEDULED -> stringResource(R.string.calendar_occurrence_rescheduled)
                    EffectiveLessonOrigin.MAKE_UP -> stringResource(R.string.calendar_occurrence_makeup)
                }
                if (badge != null) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private fun isWithinWeek(date: LocalDate, weekStart: LocalDate, weekEnd: LocalDate): Boolean {
    return !date.isBefore(weekStart) && !date.isAfter(weekEnd)
}
