package com.xtawa.classingtime.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xtawa.classingtime.R
import com.xtawa.classingtime.ui.components.ClassingBitsLoadingDots
import java.time.DayOfWeek
import androidx.compose.ui.platform.LocalContext
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun OnboardingImportEditor(target: OnboardingImportTarget, staged: BackupRestorePayload?, onParsed: (BackupRestorePayload) -> Unit, parseFile: suspend (Uri, OnboardingImportTarget) -> Result<BackupRestorePayload>, sessionId: String = "", semesterStart: java.time.LocalDate = java.time.LocalDate.now()) {
 val scope = rememberCoroutineScope()
 var busy by remember { mutableStateOf(false) }; var status by remember { mutableStateOf("") }
 var title by remember { mutableStateOf("") }; var room by remember { mutableStateOf("") }
 var start by remember { mutableStateOf("09:00") }; var end by remember { mutableStateOf("10:00") }
 var day by remember { mutableStateOf(1) }; var expanded by remember { mutableStateOf(false) }
 var editing by remember { mutableStateOf<LessonUi?>(null) }
 var naturalText by remember { mutableStateOf("") }
 val context = LocalContext.current
 val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
  if (uri != null) scope.launch {
   busy = true
   try { parseFile(uri, target).onSuccess { result -> require(result.baseLessons.isNotEmpty()); onParsed(result); status = "✓ ${result.baseLessons.size}" }
    .onFailure { status = it.message.orEmpty() } }
   catch (e: CancellationException) { throw e } catch (e: Exception) { status = e.message.orEmpty() }
   finally { busy = false }
  }
 }
 if (target == OnboardingImportTarget.CANVAS) {
  CanvasImportEditor(onParsed)
 } else if (target == OnboardingImportTarget.AI_TEXT) {
  OutlinedTextField(naturalText, { naturalText = it }, label = { Text(stringResource(R.string.ai_text_input)) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
  Button(enabled = !busy && naturalText.isNotBlank(), onClick = { scope.launch {
   busy = true
   try { onParsed(importOnboardingText(context, naturalText)); status = "✓" }
   catch(e: CancellationException) { throw e } catch(e: Exception) { status = e.message.orEmpty() }
   finally { busy = false }
  } }) { Text(stringResource(R.string.onboarding_import_now)) }
 } else if (target == OnboardingImportTarget.MANUAL_ENTRY) {
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
    onParsed(BackupRestorePayload(staged?.baseLessons.orEmpty() + lesson, staged?.exceptions.orEmpty(), null, null, staged?.warnings.orEmpty())); title = ""; status = "✓"
   }.onFailure { status = it.message ?: "HH:mm" }
  }, enabled = title.isNotBlank()) { Text(stringResource(R.string.onboarding_add_course)) }
 } else {
  Button(onClick = { picker.launch(when (target) {
   OnboardingImportTarget.ICS -> arrayOf("text/calendar", "text/plain", "application/octet-stream")
   OnboardingImportTarget.AI_DOCUMENT -> arrayOf("image/*", "application/pdf")
   else -> arrayOf("application/json", "text/plain", "application/octet-stream")
  }) }, enabled = !busy) {
   Text(stringResource(R.string.onboarding_import_now))
  }
 }
 AnimatedVisibility(visible = busy) {
  Row(
   modifier = Modifier.fillMaxWidth(),
   verticalAlignment = Alignment.CenterVertically,
   horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
   ClassingBitsLoadingDots()
   Text(stringResource(R.string.onboarding_import_processing), style = MaterialTheme.typography.bodySmall)
  }
 }
 if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
 staged?.let { data ->
  Text(stringResource(R.string.onboarding_import_done, data.baseLessons.size), style = MaterialTheme.typography.titleSmall)
  val conflicts = detectLessonConflicts(data.baseLessons, data.weekNumberMode ?: WeekNumberMode.SEMESTER, data.semesterWeekStartDate ?: semesterStart)
  if (conflicts.isNotEmpty()) Text(context.getString(R.string.import_conflict_dialog_message, conflicts.size), color = MaterialTheme.colorScheme.error)
  data.baseLessons.forEach { lesson ->
   TextButton(onClick = { editing = lesson }) { Text("${lesson.title} · ${lesson.dayOfWeek.name} · ${lesson.startTime}–${lesson.endTime}") }
  }
  data.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
 }
 editing?.let { lesson -> LessonEditDialog(LessonEditContext(lesson, null, setOf(LessonEditScope.WholeLesson)),
  onDismiss = { editing = null },
  onSave = { updated, _ -> staged?.let { onParsed(it.copy(baseLessons = it.baseLessons.map { old -> if (old.id == updated.id) updated else old })) }; com.xtawa.classingtime.metrics.ProductMetrics.record(context, com.xtawa.classingtime.metrics.ProductEvent.IMPORT_CORRECTED, count = 1, sessionId = sessionId); editing = null },
  onDelete = { staged?.let { onParsed(it.copy(baseLessons = it.baseLessons.filterNot { it.id == lesson.id }, exceptions = it.exceptions.filterNot { it.lessonId == lesson.id })) }; editing = null }) }
}
