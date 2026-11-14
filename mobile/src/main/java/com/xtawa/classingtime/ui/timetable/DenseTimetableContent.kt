package com.xtawa.classingtime.ui.timetable

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xtawa.classingtime.R
import com.xtawa.classingtime.screen.LessonUi
import com.xtawa.classingtime.ui.components.*
import com.xtawa.classingtime.ui.theme.*
import java.time.*
import java.time.format.DateTimeFormatter

/** One native view used by both the dense dashboard and full timetable route. */
@Composable
internal fun DenseTimetableContent(
    lessonsForDate: (LocalDate) -> List<LessonUi>,
    firstDay: DayOfWeek = DayOfWeek.MONDAY,
    contentPadding: PaddingValues = PaddingValues(),
    onBack: (() -> Unit)? = null,
    onOpenLesson: (LessonUi, LocalDate) -> Unit,
    onLongPressLesson: (LessonUi) -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    onAsk: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    val today = LocalDate.now()
    val dates = denseWeekDates(today.plusWeeks(weekOffset.toLong()), firstDay)
    val month = DateTimeFormatter.ofPattern("MM/dd")
    val day = DateTimeFormatter.ofPattern("EEE")
    Column(modifier.fillMaxSize().padding(contentPadding).padding(horizontal = ClassingSpacing.sm)) {
        ClassingPageHeader(title = stringResource(R.string.timetable_title),
            supportingText = "${dates.first().format(month)}–${dates.last().format(month)}",
            onBack = onBack, backLabel = stringResource(R.string.timetable_back_home),
            action = { Row(content = actions) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { weekOffset-- }) { Text(stringResource(R.string.schedule_previous_week)) }
            TextButton(onClick = { weekOffset = 0 }, enabled = weekOffset != 0) { Text(stringResource(R.string.schedule_current_week)) }
            TextButton(onClick = { weekOffset++ }) { Text(stringResource(R.string.schedule_next_week)) }
        }
        AnimatedContent(targetState = dates, modifier = Modifier.weight(1f),
            transitionSpec = { ClassingBitsTransitions.horizontal(if (targetState.first() > initialState.first()) 1 else -1,
                ValueAnimator.areAnimatorsEnabled()) }, label = "dense_week") { week ->
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Keep all seven columns on ordinary screens; allow readable horizontal scroll with large text.
                val columnWidth = if (LocalDensity.current.fontScale > 1.25f) 80.dp else ((maxWidth - 18.dp) / 7).coerceAtLeast(44.dp)
                val horizontal = rememberScrollState()
                Column(Modifier.horizontalScroll(horizontal)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        week.forEach { date ->
                            Surface(Modifier.width(columnWidth), shape = RoundedCornerShape(12.dp),
                                color = if (date == today) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(date.format(day), style = MaterialTheme.typography.labelSmall)
                                    Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    Row(Modifier.weight(1f).verticalScroll(rememberScrollState()).semantics {
                        collectionInfo = CollectionInfo(1, 7)
                    }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        week.forEachIndexed { index, date ->
                            val lessons = denseDayLessons(lessonsForDate(date))
                            Column(Modifier.width(columnWidth).bitsReveal(index), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Spacer(Modifier.height(6.dp))
                                if (lessons.isEmpty()) Text(stringResource(R.string.schedule_dense_empty),
                                    modifier = Modifier.padding(4.dp), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                lessons.forEach { lesson -> DenseCourseCell(lesson, date,
                                    onClick = { onOpenLesson(lesson, date) }, onLongClick = { onLongPressLesson(lesson) }) }
                                Spacer(Modifier.height(24.dp))
                            }
                        }
                    }
                }
            }
        }
        if (onAsk != null) TextButton(onClick = onAsk, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.assistant_title))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DenseCourseCell(lesson: LessonUi, date: LocalDate, onClick: () -> Unit, onLongClick: () -> Unit) {
    val accent = classingCourseAccent(lesson.title)
    val clock = DateTimeFormatter.ofPattern("HH:mm")
    Surface(Modifier.fillMaxWidth().heightIn(min = 80.dp).combinedClickable(onClick = onClick,
        onLongClick = onLongClick, role = Role.Button).semantics(mergeDescendants = true) {
        contentDescription = "${date} · ${lesson.title} · ${lesson.startTime.format(clock)}–${lesson.endTime.format(clock)} · ${lesson.location.orEmpty()}"
    }, shape = RoundedCornerShape(10.dp), color = accent.copy(alpha = .12f), border = BorderStroke(1.dp, accent.copy(alpha = .28f))) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(lesson.title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            Text("${lesson.startTime.format(clock)}\n${lesson.endTime.format(clock)}", style = MaterialTheme.typography.labelSmall)
            lesson.location?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
    }
}
