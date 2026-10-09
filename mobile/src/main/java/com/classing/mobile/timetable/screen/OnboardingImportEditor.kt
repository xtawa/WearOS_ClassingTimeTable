package com.xtawa.classingtime.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xtawa.classingtime.R
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun OnboardingImportEditor(target: OnboardingImportTarget, staged: BackupRestorePayload?, onParsed: (BackupRestorePayload) -> Unit, parseFile: suspend (Uri, OnboardingImportTarget) -> Result<BackupRestorePayload>) {
 val scope = rememberCoroutineScope()
 var busy by remember { mutableStateOf(false) }; var status by remember { mutableStateOf("") }
 var title by remember { mutableStateOf("") }; var room by remember { mutableStateOf("") }
 var start by remember { mutableStateOf("09:00") }; var end by remember { mutableStateOf("10:00") }
 var day by remember { mutableStateOf(1) }; var expanded by remember { mutableStateOf(false) }
 val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
  if (uri != null) scope.launch {
   busy = true
   try { parseFile(uri, target).onSuccess { result -> require(result.baseLessons.isNotEmpty()); onParsed(result); status = "✓ ${result.baseLessons.size}" }
    .onFailure { status = it.message.orEmpty() } }
   catch (e: CancellationException) { throw e } catch (e: Exception) { status = e.message.orEmpty() }
   finally { busy = false }
  }
 }
 if (target == OnboardingImportTarget.MANUAL_ENTRY) {
  OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.appearance_preview_course)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
  OutlinedTextField(room, { room = it }, label = { Text(stringResource(R.string.appearance_preview_room)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
  Box { OutlinedButton(onClick = { expanded = true }) { Text(DayOfWeek.of(day).name) }
   DropdownMenu(expanded, { expanded = false }) { for (d in 1..7) DropdownMenuItem(text = { Text(DayOfWeek.of(d).name) }, onClick = { day = d; expanded = false }) }
  }
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
   OutlinedTextField(start, { start = it }, label = { Text("HH:mm →") }, singleLine = true, modifier = Modifier.weight(1f))
   OutlinedTextField(end, { end = it }, label = { Text("→ HH:mm") }, singleLine = true, modifier = Modifier.weight(1f))
  }
  Button(onClick = {
   runCatching {
    val from = LocalTime.parse(start); val to = LocalTime.parse(end); require(to > from && title.isNotBlank())
    val lesson = LessonUi(UUID.randomUUID().toString(), title.trim(), null, room.takeIf { it.isNotBlank() }, null, DayOfWeek.of(day), from, to)
    onParsed(BackupRestorePayload(staged?.baseLessons.orEmpty() + lesson, emptyList(), null, null, emptyList())); title = ""; status = "✓"
   }.onFailure { status = it.message ?: "HH:mm" }
  }, enabled = title.isNotBlank()) { Text(stringResource(R.string.onboarding_add_course)) }
 } else {
  Button(onClick = { picker.launch(if (target == OnboardingImportTarget.ICS) arrayOf("text/calendar", "text/plain", "application/octet-stream") else arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !busy) {
   Text(stringResource(R.string.onboarding_import_now))
  }
 }
 if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
 if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
 staged?.let { data ->
  Text(stringResource(R.string.onboarding_import_done, data.baseLessons.size), style = MaterialTheme.typography.titleSmall)
  data.baseLessons.take(6).forEach { Text("${it.title} · ${it.dayOfWeek.name} · ${it.startTime}–${it.endTime}", style = MaterialTheme.typography.bodySmall) }
  data.warnings.take(3).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
 }
}
