package com.xtawa.classingtime.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.classing.shared.time.nextMinuteDelay
import com.xtawa.classingtime.screen.LessonUi
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun HomeScreen(
    contentPadding: PaddingValues,
    lessonsForDate: (LocalDate) -> List<LessonUi>,
    hasImportedSchedule: Boolean,
    onOpenAskClassing: (String) -> Unit,
    onCourseClick: (HomeCourseUiModel) -> Unit,
    onOpenTimetable: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var assistantFocused by remember { mutableStateOf(false) }
    var assistantQuery by remember { mutableStateOf("") }
    var assistantProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val current = LocalDateTime.now()
            now = current
            delay(nextMinuteDelay(current).toMillis())
        }
    }

    val homeState = remember(now, lessonsForDate, hasImportedSchedule) {
        resolveHomeUiState(
            now = now,
            lessonsForDate = lessonsForDate,
            hasImportedSchedule = hasImportedSchedule,
        )
    }

    HomeContent(
        state = homeState,
        assistantState = HomeAssistantUiState(
            focused = assistantFocused,
            query = assistantQuery,
            processing = assistantProcessing,
        ),
        contentPadding = contentPadding,
        onAssistantFocusedChange = { assistantFocused = it },
        onQueryChange = { assistantQuery = it },
        onSubmitQuery = { query ->
            if (query.isNotBlank() && !assistantProcessing) {
                assistantProcessing = true
                onOpenAskClassing(query)
                assistantProcessing = false
            }
        },
        onCourseClick = onCourseClick,
        onOpenTimetable = onOpenTimetable,
        onOpenSettings = onOpenSettings,
    )
}
