package com.classing.wear.timetable.ui.state

import com.classing.wear.timetable.domain.model.LessonOccurrence
import com.classing.wear.timetable.domain.model.NextLessonHint
import com.classing.wear.timetable.domain.model.SyncState
import com.classing.shared.ui.heatmap.HeatmapCell

data class HomeUiState(
    val isLoading: Boolean = true,
    val hasSchedule: Boolean = false,
    val dateLabel: String = "",
    val weekLabel: String = "",
    val syncState: SyncState = SyncState.Idle,
    val nextLesson: NextLessonHint = NextLessonHint(null, null),
    val todayLessons: List<LessonOccurrence> = emptyList(),
    val heatmapCells: List<HeatmapCell> = emptyList(),
    val errorMessage: String? = null,
)
