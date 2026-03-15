package com.xtawa.classingtime.screen

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xtawa.classingtime.R

@Composable
internal fun MobileDialogs(
    context: Context,
    showImportConflictDialog: Boolean,
    pendingImportConflicts: List<LessonConflict>,
    onDismissImportConflict: () -> Unit,
    onConfirmImportConflict: () -> Unit,
    onCancelImportConflict: () -> Unit,
    showManualConflictDialog: Boolean,
    pendingManualLesson: LessonUi?,
    pendingManualConflicts: List<LessonUi>,
    onDismissManualConflict: () -> Unit,
    onConfirmManualConflict: (LessonUi) -> Unit,
    onCancelManualConflict: () -> Unit,
    editingLesson: LessonUi?,
    onDismissEditLesson: () -> Unit,
    onSaveEditLesson: (LessonUi, ChangeScope) -> Unit,
    onDeleteEditLesson: (LessonUi, ChangeScope) -> Unit,
    showRestoreConfirmDialog: Boolean,
    pendingRestoreLessons: List<LessonUi>,
    pendingRestoreWarnings: List<String>,
    currentLessonsCount: Int,
    onDismissRestore: () -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    showClearAllConfirmDialog: Boolean,
    onDismissClearAll: () -> Unit,
    onConfirmClearAll: () -> Unit,
    onCancelClearAll: () -> Unit,
) {
    if (showImportConflictDialog && pendingImportConflicts.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = onDismissImportConflict,
            title = { Text(stringResource(R.string.import_conflict_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.import_conflict_dialog_message, pendingImportConflicts.size),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    pendingImportConflicts.take(5).forEach { conflict ->
                        Text(
                            text = formatLessonConflict(conflict, context),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (pendingImportConflicts.size > 5) {
                        Text(
                            text = stringResource(R.string.import_conflict_more, pendingImportConflicts.size - 5),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmImportConflict) {
                    Text(stringResource(R.string.import_conflict_continue_button))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelImportConflict) {
                    Text(stringResource(R.string.import_conflict_cancel_button))
                }
            },
        )
    }

    if (showManualConflictDialog && pendingManualLesson != null && pendingManualConflicts.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = onDismissManualConflict,
            title = { Text(stringResource(R.string.manual_conflict_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.manual_conflict_dialog_message, pendingManualConflicts.size),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(
                            R.string.manual_conflict_new_lesson_label,
                            formatLessonSummary(pendingManualLesson, context),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    pendingManualConflicts.take(5).forEach { conflictLesson ->
                        Text(
                            text = stringResource(
                                R.string.manual_conflict_existing_lesson_label,
                                formatLessonSummary(conflictLesson, context),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (pendingManualConflicts.size > 5) {
                        Text(
                            text = stringResource(R.string.manual_conflict_more, pendingManualConflicts.size - 5),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onConfirmManualConflict(pendingManualLesson) }) {
                    Text(stringResource(R.string.manual_conflict_continue_button))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelManualConflict) {
                    Text(stringResource(R.string.manual_conflict_cancel_button))
                }
            },
        )
    }

    val targetLesson = editingLesson
    if (targetLesson != null) {
        LessonEditDialog(
            lesson = targetLesson,
            onDismiss = onDismissEditLesson,
            onSave = onSaveEditLesson,
            onDelete = { scope -> onDeleteEditLesson(targetLesson, scope) },
        )
    }

    if (showRestoreConfirmDialog && pendingRestoreLessons.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = onDismissRestore,
            title = { Text(stringResource(R.string.backup_restore_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(
                            R.string.backup_restore_dialog_message,
                            currentLessonsCount,
                            pendingRestoreLessons.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    pendingRestoreLessons.take(5).forEach { lesson ->
                        Text(
                            text = formatLessonSummary(lesson, context),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (pendingRestoreLessons.size > 5) {
                        Text(
                            text = stringResource(R.string.backup_restore_dialog_more, pendingRestoreLessons.size - 5),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (pendingRestoreWarnings.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.backup_restore_warning_title),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        pendingRestoreWarnings.take(3).forEach { warning ->
                            Text(
                                text = stringResource(R.string.status_warning_prefix, warning),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmRestore) {
                    Text(stringResource(R.string.backup_restore_confirm_button))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelRestore) {
                    Text(stringResource(R.string.backup_restore_cancel_button))
                }
            },
        )
    }

    if (showClearAllConfirmDialog) {
        AlertDialog(
            onDismissRequest = onDismissClearAll,
            title = { Text(stringResource(R.string.danger_clear_dialog_title)) },
            text = {
                Text(
                    text = stringResource(R.string.danger_clear_dialog_message, currentLessonsCount),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmClearAll) {
                    Text(stringResource(R.string.danger_clear_confirm_button))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelClearAll) {
                    Text(stringResource(R.string.danger_clear_cancel_button))
                }
            },
        )
    }
}
