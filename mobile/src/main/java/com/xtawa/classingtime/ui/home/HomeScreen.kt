package com.xtawa.classingtime.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import com.classing.client.exam.*
import com.classing.shared.exam.*
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.classing.shared.time.nextMinuteDelay
import com.xtawa.classingtime.screen.LessonUi
import com.xtawa.classingtime.screen.SystemCalendarEvent
import androidx.compose.ui.platform.LocalContext
import com.xtawa.classingtime.data.MobilePrefsStore
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun HomeScreen(
    contentPadding: PaddingValues,
    lessonsForDate: (LocalDate) -> List<LessonUi>,
    hasImportedSchedule: Boolean,
    prompts: List<String> = emptyList(),
    calendarEvents: List<SystemCalendarEvent> = emptyList(),
    onOpenAskClassing: (String) -> Unit,
    onCourseClick: (HomeCourseUiModel) -> Unit,
    onOpenTimetable: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var assistantFocused by remember { mutableStateOf(false) }
    var assistantQuery by remember { mutableStateOf("") }
    var assistantProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        com.xtawa.classingtime.metrics.ProductMetrics.record(context, com.xtawa.classingtime.metrics.ProductEvent.NEXT_CLASS_VIEWED)
        com.xtawa.classingtime.metrics.ProductMetrics.record(context, com.xtawa.classingtime.metrics.ProductEvent.ACTIVE_DAY)
        while (isActive) {
            val current = LocalDateTime.now()
            now = current
            delay(nextMinuteDelay(current).toMillis())
        }
    }

    val homeState = remember(now, lessonsForDate, hasImportedSchedule) {
        val settings = MobilePrefsStore.loadSettings(context)
        resolveHomeUiState(
            now = now,
            lessonsForDate = lessonsForDate,
            hasImportedSchedule = hasImportedSchedule,
        ).copy(freshnessText = com.xtawa.classingtime.widget.scheduleFreshnessText(context, settings.cloudSyncEnabled, settings.cloudLastSyncedAt))
    }

    var mode by remember { mutableStateOf(ExamStore.mode(context)) }
    val exams by remember { ExamStore.observe(context) }.collectAsState(initial = ExamStore.load(context))
    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())) {
        AcademicModeSwitch(mode, { mode = it; ExamStore.setMode(context, it); com.xtawa.classingtime.widget.NextClassWidget.refresh(context) }, Modifier.align(Alignment.CenterHorizontally))
        if (mode == AcademicMode.EXAM) ExamPanel(exams, onSave = { ExamStore.save(context, it) },
            onAsk = { onOpenAskClassing(if (java.util.Locale.getDefault().language == "zh") "我的下一场考试是什么？" else "What is my next exam?") }, modifier = Modifier.weight(1f))
        else HomeContent(
        state = homeState,
        calendarEvents = calendarEvents,
        assistantState = HomeAssistantUiState(
            focused = assistantFocused,
            query = assistantQuery,
            processing = assistantProcessing,
            prompts = prompts,
        ),
        contentPadding = PaddingValues(),
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
}
