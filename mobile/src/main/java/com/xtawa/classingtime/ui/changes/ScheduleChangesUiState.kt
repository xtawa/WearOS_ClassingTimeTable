package com.xtawa.classingtime.ui.changes

import androidx.compose.runtime.Immutable
import java.time.LocalDate

internal enum class ScheduleChangeType {
    Moved,
    Cancelled,
    Added,
}

@Immutable
internal data class ScheduleChangeUiModel(
    val id: String,
    val lessonId: String?,
    val title: String,
    val date: LocalDate,
    val dateLabel: String,
    val type: ScheduleChangeType,
    val beforeLabel: String?,
    val nowLabel: String?,
    val contextLabel: String?,
)

@Immutable
internal data class ScheduleChangesUiState(
    val changes: List<ScheduleChangeUiModel>,
)
