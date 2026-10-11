package com.classing.client.exam

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.focusable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.classing.shared.exam.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun examText(zh: String, en: String): String = if (Locale.getDefault().language == "zh") zh else en

/** A single-select capsule; selected state and role are exposed to TalkBack. */
@Composable
fun AcademicModeSwitch(mode: AcademicMode, onMode: (AcademicMode) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)
        .padding(4.dp).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
        AcademicMode.values().forEach { item ->
            val color by animateColorAsState(if (item == mode) MaterialTheme.colorScheme.secondaryContainer else
                MaterialTheme.colorScheme.surfaceVariant, label = "academicMode")
            Box(Modifier.clip(RoundedCornerShape(50)).background(color)
                .selectable(item == mode, role = Role.Tab, onClick = { onMode(item) })
                .padding(horizontal = if (compact) 12.dp else 22.dp, vertical = if (compact) 8.dp else 10.dp), contentAlignment = Alignment.Center) {
                Text(if (item == AcademicMode.SCHEDULE) examText("课表", "Schedule") else examText("考试", "Exam"))
            }
        }
    }
}

fun examSummary(exam: Exam, now: Instant = Instant.now()): String {
    val time = Instant.ofEpochMilli(exam.startAt).atZone(ZoneId.of(exam.timezone))
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
    val phase = when (exam.phase(now)) {
        ExamPhase.IN_PROGRESS -> examText("正在考试", "In progress")
        ExamPhase.FINISHED -> examText("已结束", "Finished")
        ExamPhase.UPCOMING -> {
            val minutes = Duration.between(now, Instant.ofEpochMilli(exam.startAt)).toMinutes().coerceAtLeast(0)
            if (minutes >= 1440) examText("${minutes / 1440} 天后", "In ${minutes / 1440} days")
            else examText("${minutes / 60} 小时 ${minutes % 60} 分后", "In ${minutes / 60}h ${minutes % 60}m")
        }
    }
    return "$time · $phase"
}

@Composable
fun ExamPanel(exams: List<Exam>, onSave: ((List<Exam>) -> Unit)? = null, onAsk: (() -> Unit)? = null,
              round: Boolean = false, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Instant.now()) }
    var editing by remember { mutableStateOf<Exam?>(null) }
    var editor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Exam?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val rotaryScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(round) { if (round) focusRequester.requestFocus() }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = Instant.now() } }
    LazyColumn(modifier.fillMaxSize().then(if (round) Modifier.onRotaryScrollEvent {
        rotaryScope.launch { listState.scrollBy(it.verticalScrollPixels) }; true
    }.focusRequester(focusRequester).focusable() else Modifier), state = listState,
        contentPadding = PaddingValues(horizontal = if (round) 24.dp else 20.dp,
        vertical = if (round) 20.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onSave != null) item {
            Button(onClick = { editing = null; editor = true }, modifier = Modifier.fillMaxWidth()) {
                Text(examText("添加考试", "Add exam"))
            }
        }
        if (exams.isEmpty()) item {
            Text(examText("暂无考试\n添加名称、时间和地点，考试会保存在本机并随课表同步。",
                "No exams yet\nAdd a title, time and location. Exams are cached offline and sync with your schedule."))
        }
        items(exams.sortedWith(compareBy<Exam> { it.completed || it.endAt <= now.toEpochMilli() }.thenBy { it.startAt }), key = { it.id }) { exam ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(exam.title, style = MaterialTheme.typography.titleMedium)
                    Text(examSummary(exam, now))
                    Text(Instant.ofEpochMilli(exam.endAt).atZone(ZoneId.of(exam.timezone))
                        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) + " · " + exam.timezone,
                        style = MaterialTheme.typography.bodySmall)
                    if (exam.location.isNotBlank()) Text(exam.location)
                    if (exam.note.isNotBlank()) Text(exam.note)
                    if (exam.completed) Text(examText("已完成", "Completed"))
                    if (onSave != null) Row {
                        TextButton(onClick = { editing = exam; editor = true }) { Text(examText("编辑", "Edit")) }
                        TextButton(onClick = { deleting = exam }) { Text(examText("删除", "Delete")) }
                    }
                }
            }
        }
        if (onAsk != null) item {
            OutlinedButton(onClick = onAsk, modifier = Modifier.fillMaxWidth()) { Text(examText("询问 AskAI", "AskAI")) }
        }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        if (round) item { Spacer(Modifier.height(36.dp)) }
    }
    if (editor && onSave != null) ExamEditor(editing, onDismiss = { editor = false }, onSave = { value ->
        // Read the latest list captured by the current composition; preserve unrelated records.
        editing?.let { original -> require(exams.firstOrNull { it.id == original.id } == original) { "Exam changed. Reopen the editor." } }
        onSave(exams.filterNot { it.id == value.id } + value)
        editor = false
    })
    deleting?.let { exam -> AlertDialog(onDismissRequest = { deleting = null },
        title = { Text(examText("删除考试？", "Delete exam?")) }, text = { Text(exam.title) },
        confirmButton = { TextButton(onClick = {
            runCatching { onSave?.invoke(exams.filterNot { it.id == exam.id }) }
                .onFailure { error = it.message }
            deleting = null
        }) { Text(examText("删除", "Delete")) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(examText("取消", "Cancel")) } }) }
}

@Composable
private fun ExamEditor(existing: Exam?, onDismiss: () -> Unit, onSave: (Exam) -> Unit) {
    val initial = remember { Instant.ofEpochMilli(existing?.startAt ?: (System.currentTimeMillis() + 86400000))
        .atZone(ZoneId.of(existing?.timezone ?: ZoneId.systemDefault().id)).let {
            if (existing == null) it.withSecond(0).withNano(0) else it
        } }
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var start by remember { mutableStateOf(initial.toLocalDateTime().toString()) }
    var end by remember { mutableStateOf(existing?.let { Instant.ofEpochMilli(it.endAt).atZone(ZoneId.of(it.timezone))
        .toLocalDateTime().toString() } ?: initial.plusHours(2).toLocalDateTime().withSecond(0).withNano(0).toString()) }
    var zone by remember { mutableStateOf(existing?.timezone ?: ZoneId.systemDefault().id) }
    var location by remember { mutableStateOf(existing?.location.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var minutes by remember { mutableStateOf((existing?.reminderMinutes ?: 10).toString()) }
    var completed by remember { mutableStateOf(existing?.completed ?: false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(examText("考试", "Exam")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text(examText("名称", "Title")) }, singleLine = true)
            ExamDateTimeField(examText("开始时间", "Starts"), start, { start = it })
            ExamDateTimeField(examText("结束时间", "Ends"), end, { end = it })
            OutlinedTextField(zone, { zone = it }, label = { Text(examText("时区（Asia/Shanghai）", "Time zone (Asia/Shanghai)")) })
            OutlinedTextField(location, { location = it }, label = { Text(examText("地点", "Location")) })
            OutlinedTextField(note, { note = it }, label = { Text(examText("备注", "Note")) })
            OutlinedTextField(minutes, { minutes = it }, label = { Text(examText("提前提醒（分钟，0 为准点）", "Reminder (minutes before, 0 at start)")) })
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(completed, { completed = it }); Text(examText("已完成", "Completed")) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        runCatching {
            val z = ZoneId.of(zone.trim())
            val exam = Exam(existing?.id ?: UUID.randomUUID().toString(), title.trim(),
                Exam.editInstant(LocalDateTime.parse(start.trim()), z, existing?.startAt, existing?.timezone).toEpochMilli(),
                Exam.editInstant(LocalDateTime.parse(end.trim()), z, existing?.endAt, existing?.timezone).toEpochMilli(), z.id,
                location.trim(), note.trim(), existing?.courseId, minutes.toInt(), completed)
            onSave(exam)
        }.onFailure { error = examText("请检查时间、时区和输入：", "Check time, zone and fields: ") + it.message }
    }) { Text(examText("保存", "Save")) } }, dismissButton = {
        TextButton(onClick = onDismiss) { Text(examText("取消", "Cancel")) }
    })
}

@Composable
private fun ExamDateTimeField(label: String, value: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val dateTime = LocalDateTime.parse(value)
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
            android.app.DatePickerDialog(context, { _, year, month, day ->
                android.app.TimePickerDialog(context, { _, hour, minute ->
                    onChange(LocalDateTime.of(year, month + 1, day, hour, minute).toString())
                }, dateTime.hour, dateTime.minute, android.text.format.DateFormat.is24HourFormat(context)).show()
            }, dateTime.year, dateTime.monthValue - 1, dateTime.dayOfMonth).show()
        }) { Text(dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))) }
    }
}
