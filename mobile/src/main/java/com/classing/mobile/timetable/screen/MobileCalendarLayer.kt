package com.xtawa.classingtime.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun CalendarMonthLayer(
    contentPadding: PaddingValues,
    lessonsByDay: Map<DayOfWeek, List<LessonUi>>,
) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val monthFormatter = remember(locale) {
        if (locale.language.startsWith("zh")) {
            DateTimeFormatter.ofPattern("yyyy年M月", locale)
        } else {
            DateTimeFormatter.ofPattern("MMMM yyyy", locale)
        }
    }
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    }
    val today = remember { LocalDate.now() }
    var displayedMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDate by remember { mutableStateOf(today) }

    val firstDay = displayedMonth.atDay(1)
    val leadingBlank = firstDay.dayOfWeek.value - DayOfWeek.MONDAY.value
    val daysInMonth = displayedMonth.lengthOfMonth()
    val trailingBlank = (7 - ((leadingBlank + daysInMonth) % 7)) % 7
    val totalCells = leadingBlank + daysInMonth + trailingBlank
    val selectedLessons = lessonsByDay[selectedDate.dayOfWeek].orEmpty().sortedBy { it.startTime }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    text = String.format(locale, "%02d", displayedMonth.monthValue),
                    style = MaterialTheme.typography.displayLarge,
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
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    TextButton(
                        onClick = {
                            displayedMonth = displayedMonth.minusMonths(1)
                            if (!displayedMonth.contains(selectedDate)) {
                                selectedDate = displayedMonth.atDay(1)
                            }
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.calendar_prev_month),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    TextButton(
                        onClick = {
                            displayedMonth = displayedMonth.plusMonths(1)
                            if (!displayedMonth.contains(selectedDate)) {
                                selectedDate = displayedMonth.atDay(1)
                            }
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.calendar_next_month),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DayOfWeek.values().forEach { day ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                text = dayLabel(day, context),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                for (row in 0 until totalCells / 7) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (column in 0 until 7) {
                            val cellIndex = row * 7 + column
                            val dayNumber = cellIndex - leadingBlank + 1
                            if (dayNumber !in 1..daysInMonth) {
                                Box(modifier = Modifier.weight(1f).height(92.dp))
                            } else {
                                val date = displayedMonth.atDay(dayNumber)
                                val isSelected = date == selectedDate
                                val isToday = date == today
                                val dayLessons = lessonsByDay[date.dayOfWeek].orEmpty().sortedBy { it.startTime }
                                val borderColor = when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                }

                                Card(
                                    onClick = { selectedDate = date },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(92.dp),
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = borderColor,
                                    ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerLowest
                                        },
                                    ),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = dayNumber.toString(),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                        )
                                        dayLessons.take(2).forEach { lesson ->
                                            Text(
                                                text = "${lesson.startTime.format(clockFormatter)} ${lesson.title}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f)
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (dayLessons.size > 2) {
                                            Text(
                                                text = "+${dayLessons.size - 2}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.80f)
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
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

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.calendar_selected_date_title,
                        selectedDate.format(dateFormatter),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (selectedLessons.isEmpty()) {
                    Text(
                        text = stringResource(R.string.calendar_no_classes_on_date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    selectedLessons.forEach { lesson ->
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
                }
            }
        }
    }
}

private fun YearMonth.contains(date: LocalDate): Boolean {
    return date.year == year && date.month == month
}
