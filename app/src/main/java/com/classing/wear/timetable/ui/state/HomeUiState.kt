package com.classing.wear.timetable.ui.state

import com.classing.wear.timetable.domain.model.LessonOccurrence
import com.classing.wear.timetable.domain.model.NextLessonHint
import com.classing.wear.timetable.domain.model.SyncState

data class HomeUiState(
    val isLoading: Boolean = true,
    val dateLabel: String = "",
    val weekLabel: String = "",
    val syncState: SyncState = SyncState.Idle,
    val nextLesson: NextLessonHint = NextLessonHint(null, null),
    val todayLessons: List<LessonOccurrence> = emptyList(),
    val errorMessage: String? = null,
)
