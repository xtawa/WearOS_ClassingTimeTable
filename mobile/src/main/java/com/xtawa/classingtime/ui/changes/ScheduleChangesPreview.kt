package com.xtawa.classingtime.ui.changes

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.xtawa.classingtime.ui.theme.ClassingTheme
import java.time.LocalDate

private val changesPreviewState = ScheduleChangesUiState(
    changes = listOf(
        ScheduleChangeUiModel(
            id = "move-physics",
            lessonId = "physics",
            title = "Physics moved",
            date = LocalDate.of(2026, 8, 18),
            dateLabel = "Aug 18, 2026",
            type = ScheduleChangeType.Moved,
            beforeLabel = "14:10–14:55 · A302",
            nowLabel = "15:05–15:50 · A205",
            contextLabel = "Effective for this occurrence",
        ),
        ScheduleChangeUiModel(
            id = "cancel-english",
            lessonId = "english",
            title = "English cancelled",
            date = LocalDate.of(2026, 8, 20),
            dateLabel = "Aug 20, 2026",
            type = ScheduleChangeType.Cancelled,
            beforeLabel = "10:20–11:05 · B103",
            nowLabel = "Cancelled",
            contextLabel = "The original occurrence remains in change history.",
        ),
    ),
)

@Preview(name = "Schedule changes", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ScheduleChangesPreview() {
    ClassingTheme {
        ScheduleChangesContent(
            state = changesPreviewState,
            onBack = {},
            onOpenChange = {},
        )
    }
}

@Preview(name = "Schedule changes · Large font", widthDp = 390, heightDp = 844, fontScale = 2f, showBackground = true)
@Composable
private fun ScheduleChangesLargeFontPreview() {
    ClassingTheme {
        ScheduleChangesContent(
            state = changesPreviewState,
            onBack = {},
            onOpenChange = {},
        )
    }
}
