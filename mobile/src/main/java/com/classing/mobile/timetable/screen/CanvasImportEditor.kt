package com.xtawa.classingtime.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.classing.client.lms.*
import com.classing.client.schedule.ScheduleRuleJson
import com.classing.shared.schedule.*
import com.xtawa.classingtime.R
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable internal fun CanvasImportEditor(onParsed: (BackupRestorePayload) -> Unit) {
    var origin by remember { mutableStateOf("") }; var token by remember { mutableStateOf("") }
    var snapshot by remember { mutableStateOf<CanvasSnapshot?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope(); val context = LocalContext.current
    Text(stringResource(R.string.lms_notice))
    OutlinedTextField(origin, { origin = it }, label = { Text(stringResource(R.string.lms_origin)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(token, { token = it }, label = { Text(stringResource(R.string.lms_token)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
    Button(enabled = !busy && origin.isNotBlank() && token.isNotBlank(), onClick = { scope.launch {
        busy = true; error = ""; snapshot = null; selected = emptySet()
        try { snapshot = CanvasReadonlyClient().load(origin, token, LocalDate.now(), LocalDate.now().plusDays(120)) }
        catch(e: CancellationException) { throw e } catch(e: Exception) { error = e.message.orEmpty() }
        finally { token = ""; busy = false }
    } }) { Text(stringResource(R.string.lms_read)) }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    snapshot?.let { data ->
        Text(stringResource(R.string.lms_select_meetings))
        data.meetings.forEach { meeting -> Row { Checkbox(meeting.id in selected, { selected = if(it) selected + meeting.id else selected - meeting.id }); Text("${meeting.title} · ${meeting.start.atZone(ZoneId.systemDefault())} · ${meeting.room}") } }
        Text(stringResource(R.string.lms_assignments, data.assignments.size))
        data.assignments.forEach { Text("${it.title} · ${it.due?.atZone(ZoneId.systemDefault()) ?: "—"}") }
        data.warnings.forEach { Text(it) }
        Button(enabled = selected.isNotEmpty(), onClick = {
            runCatching { data.meetings.filter { it.id in selected }.map { meeting ->
                val start = meeting.start.atZone(ZoneId.systemDefault()); val end = meeting.end.atZone(ZoneId.systemDefault())
                require(start.toLocalDate() == end.toLocalDate()) { "Overnight meeting needs manual entry" }
                LessonUi("canvas-${meeting.id}", meeting.title, null, meeting.room, null, start.dayOfWeek, start.toLocalTime(), end.toLocalTime(),
                    scheduleRuleJson = ScheduleRuleJson.encode(ScheduleRule(RepeatKind.DATES, start.toLocalDate(), start.toLocalDate(), dates=setOf(start.toLocalDate()), courseGroupId="canvas-${meeting.courseId}")))
            } }.onSuccess { lessons -> onParsed(BackupRestorePayload(lessons, emptyList(), null, null, data.warnings + context.getString(R.string.lms_review_notice))) }
                .onFailure { error = context.getString(R.string.rule_validation_failed) }
        }) { Text(stringResource(R.string.onboarding_import_now)) }
    }
}
