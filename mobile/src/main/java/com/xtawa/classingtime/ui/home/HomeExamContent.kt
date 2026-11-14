package com.xtawa.classingtime.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.classing.client.exam.*
import com.classing.shared.exam.*
import com.xtawa.classingtime.R
import com.xtawa.classingtime.ui.components.*
import com.xtawa.classingtime.ui.theme.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** Course page surfaces, typography and spacing; storage/editor remain shared with Wear. */
@Composable
internal fun HomeExamContent(exams: List<Exam>, onSave: (List<Exam>) -> Unit, onAsk: () -> Unit, onSettings: () -> Unit,
    modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Instant.now()) }
    var editor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Exam?>(null) }
    var deleting by remember { mutableStateOf<Exam?>(null) }
    var showFinished by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = Instant.now() } }
    val upcoming = exams.filter { !it.completed && it.endAt > now.toEpochMilli() }.sortedBy { it.startAt }
    val finished = exams.filter { it.completed || it.endAt <= now.toEpochMilli() }.sortedByDescending { it.startAt }
    val primary = nextExam(exams, now)
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = ClassingSpacing.referenceScreenInset,
        vertical = ClassingSpacing.sm), verticalArrangement = Arrangement.spacedBy(ClassingSpacing.lg)) {
        item {
            ClassingPageHeader(title = stringResource(R.string.exam_home_title),
                supportingText = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                action = { IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.home_open_settings)) } })
        }
        item {
            ClassingInformationIsland(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = .08f)) {
                if (primary == null) {
                    Text(stringResource(R.string.exam_home_empty), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.exam_home_empty_hint), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(stringResource(R.string.exam_home_next), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Text(primary.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(Instant.ofEpochMilli(primary.startAt).atZone(ZoneId.of(primary.timezone)).format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = ClassingTimeHeroStyle)
                    Text(examSummary(primary, now), style = MaterialTheme.typography.bodyMedium)
                    ExamMeta(primary)
                    TextButton(onClick = { editing = primary; editor = true }) { Text(stringResource(R.string.exam_home_edit)) }
                }
            }
        }
        items(upcoming.filterNot { it.id == primary?.id }, key = { it.id }) { exam ->
            ExamCourseRow(exam, now, { editing = exam; editor = true }, { deleting = exam })
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { editing = null; editor = true }, Modifier.weight(1f)) { Text(stringResource(R.string.exam_home_add)) }
            OutlinedButton(onClick = onAsk) { Text(stringResource(R.string.assistant_title)) }
        } }
        if (primary != null) item { TextButton(onClick = { deleting = primary }) { Text(stringResource(R.string.exam_home_delete_next)) } }
        if (finished.isNotEmpty()) item {
            TextButton(onClick = { showFinished = !showFinished }) {
                Text(stringResource(if (showFinished) R.string.exam_hide_finished else R.string.exam_show_finished, finished.size))
            }
        }
        if (showFinished) items(finished, key = { "past:${it.id}" }) { exam ->
            ExamCourseRow(exam, now, { editing = exam; editor = true }, { deleting = exam })
        }
        if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
        item { Spacer(Modifier.height(ClassingSpacing.xl)) }
    }
    if (editor) ExamEditor(editing, onDismiss = { editor = false }, onSave = { value ->
        editing?.let { original -> check(exams.firstOrNull { it.id == original.id } == original) { examText("考试已更新，请重新打开", "Exam changed. Reopen it.") } }
        onSave(exams.filterNot { it.id == value.id } + value)
        editor = false
    })
    deleting?.let { exam -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.exam_home_delete)) },
        text = { Text(exam.title) }, confirmButton = { TextButton(onClick = {
            runCatching {
                check(exams.firstOrNull { it.id == exam.id } == exam) { examText("考试已更新，请重新打开", "Exam changed. Reopen it.") }
                onSave(exams.filterNot { it.id == exam.id })
            }.onFailure { error = it.message.orEmpty() }
            deleting = null
        }) { Text(stringResource(R.string.exam_home_delete)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.assistant_cancel_voice)) } }) }
}

@Composable
private fun ExamCourseRow(exam: Exam, now: Instant, onEdit: () -> Unit, onDelete: () -> Unit) {
    ClassingInformationIsland(modifier = Modifier.bitsReveal()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.md)) {
            Text(Instant.ofEpochMilli(exam.startAt).atZone(ZoneId.of(exam.timezone)).format(DateTimeFormatter.ofPattern("MM/dd\nHH:mm")),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(exam.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(examSummary(exam, now), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ExamMeta(exam)
        Row {
            TextButton(onClick = onEdit) { Text(stringResource(R.string.exam_home_edit)) }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.exam_home_delete)) }
        }
    }
}

@Composable
private fun ExamMeta(exam: Exam) {
    val end = Instant.ofEpochMilli(exam.endAt).atZone(ZoneId.of(exam.timezone)).format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))
    Text("→ $end · ${exam.timezone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (exam.location.isNotBlank()) Text(exam.location, style = MaterialTheme.typography.bodyMedium)
    if (exam.note.isNotBlank()) Text(exam.note, style = MaterialTheme.typography.bodyMedium)
    if (exam.completed) Text(stringResource(R.string.exam_home_completed), style = MaterialTheme.typography.labelMedium)
}
