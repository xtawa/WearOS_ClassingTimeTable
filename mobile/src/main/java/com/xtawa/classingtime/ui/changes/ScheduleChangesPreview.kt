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
            title = "物理调课",
            date = LocalDate.of(2026, 8, 18),
            dateLabel = "2026 年 8 月 18 日",
            type = ScheduleChangeType.Moved,
            beforeLabel = "14:10–14:55 · A302",
            nowLabel = "15:05–15:50 · A205",
            contextLabel = "本次课程生效",
        ),
        ScheduleChangeUiModel(
            id = "cancel-english",
            lessonId = "english",
            title = "英语取消",
            date = LocalDate.of(2026, 8, 20),
            dateLabel = "2026 年 8 月 20 日",
            type = ScheduleChangeType.Cancelled,
            beforeLabel = "10:20–11:05 · B103",
            nowLabel = "已取消",
            contextLabel = "原课程会保留在调课记录中。",
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
