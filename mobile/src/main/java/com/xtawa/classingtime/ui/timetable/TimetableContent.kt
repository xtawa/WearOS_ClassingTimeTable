package com.xtawa.classingtime.ui.timetable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xtawa.classingtime.R
import com.xtawa.classingtime.ui.theme.ClassingMotion
import com.xtawa.classingtime.ui.theme.ClassingRadii
import com.xtawa.classingtime.ui.theme.ClassingSpacing
import java.time.Duration
import java.time.format.DateTimeFormatter

private val timetableClockFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun TimetableContent(
    state: TimetableUiState,
    contentPadding: PaddingValues = PaddingValues(),
    onBack: () -> Unit,
    onSelectDate: (TimetableDayUiModel) -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenChanges: () -> Unit,
    onOpenCourse: (String) -> Unit,
    onLongPressCourse: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding),
    ) {
        TimetableHeader(
            weekLabel = state.weekLabel,
            scheduleChangeCount = state.scheduleChangeCount,
            onBack = onBack,
            onOpenCalendar = onOpenCalendar,
            onOpenChanges = onOpenChanges,
        )
        WeekContextStrip(
            days = state.days,
            selectedDate = state.selectedDate,
            onSelectDate = onSelectDate,
        )
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                val direction = if (targetState.selectedDate.isAfter(initialState.selectedDate)) 1 else -1
                (
                    slideInHorizontally(
                        animationSpec = tween(ClassingMotion.ContentReveal),
                        initialOffsetX = { width -> direction * width / 5 },
                    ) + fadeIn(tween(ClassingMotion.ContentReveal))
                    ).togetherWith(
                    slideOutHorizontally(
                        animationSpec = tween(ClassingMotion.Exit),
                        targetOffsetX = { width -> -direction * width / 7 },
                    ) + fadeOut(tween(ClassingMotion.Exit)),
                ).using(SizeTransform(clip = false))
            },
            contentKey = { it.selectedDate },
            label = "timetable_day_change",
        ) { dayState ->
            TimetableDayContent(
                state = dayState,
                onOpenCourse = onOpenCourse,
                onLongPressCourse = onLongPressCourse,
            )
        }
    }
}

@Composable
private fun TimetableHeader(
    weekLabel: String,
    scheduleChangeCount: Int,
    onBack: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenChanges: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ClassingSpacing.referenceScreenInset,
                end = ClassingSpacing.sm,
                top = ClassingSpacing.md,
                bottom = ClassingSpacing.sm,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.xs),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(ClassingSpacing.minimumTouchTarget),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.timetable_back_home),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(ClassingSpacing.xxs)) {
                Text(
                    text = stringResource(R.string.timetable_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = weekLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row {
            IconButton(onClick = onOpenChanges) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = if (scheduleChangeCount > 0) {
                        stringResource(R.string.timetable_open_changes_count, scheduleChangeCount)
                    } else {
                        stringResource(R.string.timetable_open_changes)
                    },
                    tint = if (scheduleChangeCount > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = onOpenCalendar) {
                Icon(
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = stringResource(R.string.timetable_open_calendar),
                )
            }
        }
    }
}

@Composable
private fun WeekContextStrip(
    days: List<TimetableDayUiModel>,
    selectedDate: java.time.LocalDate,
    onSelectDate: (TimetableDayUiModel) -> Unit,
) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = days.size) },
        contentPadding = PaddingValues(horizontal = ClassingSpacing.referenceScreenInset),
        horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.xs),
    ) {
        items(days, key = { it.date }) { day ->
            val selected = day.date == selectedDate
            val dayDescription = stringResource(
                R.string.timetable_day_description,
                day.dayLabel,
                day.dateLabel,
                day.courseCount,
                if (day.isToday) stringResource(R.string.timetable_today_suffix) else "",
            )
            Surface(
                modifier = Modifier
                    .width(if (largeText) 68.dp else 54.dp)
                    .selectable(
                        selected = selected,
                        onClick = { onSelectDate(day) },
                        role = Role.Tab,
                    )
                    .semantics {
                        contentDescription = dayDescription
                        collectionItemInfo = CollectionItemInfo(
                            rowIndex = 0,
                            rowSpan = 1,
                            columnIndex = days.indexOf(day),
                            columnSpan = 1,
                        )
                    },
                shape = RoundedCornerShape(ClassingRadii.medium),
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                contentColor = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
            ) {
                Column(
                    modifier = Modifier.padding(vertical = ClassingSpacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ClassingSpacing.xxs),
                ) {
                    Text(
                        text = day.dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = day.dateLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(
                                color = when {
                                    selected -> MaterialTheme.colorScheme.surface
                                    day.courseCount > 0 -> MaterialTheme.colorScheme.primary
                                    else -> Color.Transparent
                                },
                                shape = CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimetableDayContent(
    state: TimetableUiState,
    onOpenCourse: (String) -> Unit,
    onLongPressCourse: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ClassingSpacing.referenceScreenInset,
            end = ClassingSpacing.referenceScreenInset,
            top = ClassingSpacing.lg,
            bottom = ClassingSpacing.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(ClassingSpacing.sm),
    ) {
        item {
            DaySummaryIsland(state = state)
        }
        if (state.courses.isEmpty()) {
            item {
                EmptyDayIsland(hasImportedSchedule = state.hasImportedSchedule)
            }
        } else {
            items(state.courses, key = { it.id }) { course ->
                TimetableCourseRow(
                    course = course,
                    onOpenCourse = { onOpenCourse(course.id) },
                    onLongPressCourse = { onLongPressCourse(course.id) },
                )
            }
        }
    }
}

@Composable
private fun DaySummaryIsland(state: TimetableUiState) {
    val first = state.courses.firstOrNull()
    val last = state.courses.lastOrNull()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ClassingRadii.large),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(ClassingSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(ClassingSpacing.xs),
        ) {
            Text(
                text = state.selectedDateLabel,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            val summary = when {
                state.courses.isEmpty() && state.hasImportedSchedule -> stringResource(R.string.timetable_clear_day)
                state.courses.isEmpty() -> stringResource(R.string.timetable_setup_day)
                else -> stringResource(
                    R.string.timetable_summary_count_time,
                    state.courses.size,
                    first?.startTime?.format(timetableClockFormatter).orEmpty(),
                    last?.endTime?.format(timetableClockFormatter).orEmpty(),
                )
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyDayIsland(hasImportedSchedule: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = ClassingSpacing.xl, bottom = ClassingSpacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(ClassingSpacing.sm),
    ) {
        Text(
            text = stringResource(
                if (hasImportedSchedule) R.string.timetable_focus_space else R.string.timetable_build_week,
            ),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = if (hasImportedSchedule) {
                stringResource(R.string.timetable_focus_hint)
            } else {
                stringResource(R.string.timetable_setup_hint)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimetableCourseRow(
    course: TimetableCourseUiModel,
    onOpenCourse: () -> Unit,
    onLongPressCourse: () -> Unit,
) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val isCurrent = course.status == TimetableCourseStatus.Current
    val statusAlpha = if (course.status == TimetableCourseStatus.Past) 0.56f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(statusAlpha),
        horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.sm),
    ) {
        Column(
            modifier = Modifier.width(if (largeText) 72.dp else 52.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = course.startTime.format(timetableClockFormatter),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrent) course.accent else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = course.endTime.format(timetableClockFormatter),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(if (isCurrent) 14.dp else 10.dp)
                    .background(course.accent, CircleShape),
            )
            Spacer(
                modifier = Modifier
                    .width(2.dp)
                    .height(if (isCurrent) ClassingSpacing.xxxl else ClassingSpacing.xxl)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
        Surface(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(
                    onClick = onOpenCourse,
                    onLongClick = onLongPressCourse,
                    role = Role.Button,
                    onLongClickLabel = stringResource(R.string.timetable_edit_course),
                ),
            shape = RoundedCornerShape(if (isCurrent) ClassingRadii.large else ClassingRadii.medium),
            color = if (isCurrent) course.accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
            tonalElevation = if (isCurrent) 2.dp else 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(ClassingSpacing.md),
                verticalArrangement = Arrangement.spacedBy(ClassingSpacing.xs),
            ) {
                if (isCurrent) {
                    Text(
                        text = stringResource(R.string.timetable_now),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = course.accent,
                    )
                }
                Text(
                    text = course.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                CourseMetadata(icon = Icons.Rounded.LocationOn, text = course.location)
                CourseMetadata(icon = Icons.Rounded.Person, text = course.teacher)
                val minutes = Duration.between(course.startTime, course.endTime).toMinutes()
                Text(
                    text = stringResource(R.string.home_time_minutes, minutes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CourseMetadata(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String?,
) {
    if (text.isNullOrBlank()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
