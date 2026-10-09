package com.xtawa.classingtime.screen

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xtawa.classingtime.R
import com.xtawa.classingtime.account.*
import com.xtawa.classingtime.sync.AccountSessionManager
import com.xtawa.classingtime.ui.assistant.*
import java.io.File
import java.time.LocalDate
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun AskAiSettingsPage(
 contentPadding: PaddingValues, loggedIn: Boolean, member: Boolean, userId: String = "",
 lessons: List<LessonUi>, editableLessons: List<LessonUi> = lessons, exceptions: List<ScheduleExceptionUi> = emptyList(),
 currentDate: LocalDate, currentWeek: Int, timezone: String, weekNumberMode: WeekNumberMode,
 semesterWeekStartDate: LocalDate, weekStartDay: java.time.DayOfWeek,
 initialQuestion: String = "", onInitialQuestionConsumed: () -> Unit = {},
 onApplyCourseProposal: (JSONObject) -> Result<Unit> = { Result.failure(IllegalStateException("Course editing unavailable")) },
 onBack: () -> Unit, onOpenAccount: () -> Unit,
) {
 val context = LocalContext.current
 val scope = rememberCoroutineScope()
 val client = remember(context) { AiApiClient(appContext = context.applicationContext) }
 val cache = remember(context) { context.getSharedPreferences("ask_ai", 0) }
 var models by remember(userId) { mutableStateOf<List<AiModelOption>>(emptyList()) }
 var selectedModel by remember(userId) { mutableStateOf("") }
 var conversations by remember(userId) { mutableStateOf<List<AiConversationSummary>>(emptyList()) }
 var messages by remember(userId) { mutableStateOf<List<AiMessageSummary>>(emptyList()) }
 var conversationId by remember(userId) { mutableStateOf("") }
 // Navigation pre-fills a draft. No effect is allowed to submit it.
 var question by remember(userId) { mutableStateOf(initialQuestion) }
 LaunchedEffect(Unit) { onInitialQuestionConsumed() }
 var status by remember { mutableStateOf("") }
 var sending by remember { mutableStateOf(false) }
 var attachments by remember(userId) { mutableStateOf<List<AiAttachment>>(emptyList()) }
 val thumbnails = remember(userId) { mutableStateMapOf<String, Bitmap>() }
 var uploading by remember { mutableStateOf(false) }
 var recording by remember { mutableStateOf(false) }
 var transcribing by remember { mutableStateOf(false) }
 var transcriptionJob by remember { mutableStateOf<Job?>(null) }
 var subpage by rememberSaveable(userId) { mutableStateOf("") }
 var preferences by remember(userId) { mutableStateOf(runCatching { AiPreferences.fromJson(JSONObject(cache.getString("preferences:$userId", "{}").orEmpty())) }.getOrDefault(AiPreferences())) }
 var settingsReady by remember(userId) { mutableStateOf(false) }
 var preferencesSaving by remember { mutableStateOf(false) }
 var syncStatus by remember { mutableStateOf(context.getString(R.string.assistant_sync_pending)) }
 val preferenceLock = remember(userId) { Mutex() }
 var prompts by remember(userId) { mutableStateOf<List<String>>(emptyList()) }
 var refreshedOnEntry by remember(userId) { mutableStateOf(false) }
 var usage by remember { mutableStateOf<AiUsageSummary?>(null) }
 var proposal by remember { mutableStateOf<JSONObject?>(null) }
 var proposalFingerprint by remember { mutableStateOf("") }

 val snapshot = remember(lessons, editableLessons, exceptions, currentDate, currentWeek, timezone, weekNumberMode, semesterWeekStartDate, weekStartDay) {
  timetableSnapshot(lessons, currentDate, currentWeek, timezone, weekNumberMode, semesterWeekStartDate, weekStartDay).put("editableLessons", timetableSnapshot(editableLessons, currentDate, currentWeek, timezone, weekNumberMode, semesterWeekStartDate, weekStartDay).getJSONArray("lessons")).put("exceptions", JSONObject(buildScheduleBackupJson(editableLessons, exceptions, java.time.ZoneId.of(timezone), weekNumberMode, semesterWeekStartDate)).getJSONArray("exceptions"))
 }
 val fingerprint = remember(editableLessons, exceptions, timezone, weekNumberMode, semesterWeekStartDate, weekStartDay) {
  timetableFingerprint(editableLessons, timezone, weekNumberMode, semesterWeekStartDate, weekStartDay, exceptions)
 }
 val latestSnapshot by rememberUpdatedState(snapshot)
 val latestFingerprint by rememberUpdatedState(fingerprint)
 val applyProposal by rememberUpdatedState(onApplyCourseProposal)
 fun acceptPreferences(item: AiPreferences) {
  if (item.version < preferences.version) return
  preferences = item; cache.edit().putString("preferences:$userId", item.toJson().toString()).apply()
  syncStatus = context.getString(R.string.assistant_synced)
 }
 suspend fun loadPreferences(token: String) {
  preferenceLock.withLock {
   client.preferences(token).onSuccess { item ->
    acceptPreferences(item)
    val oldStars = cache.getStringSet("favorites:$userId", emptySet()).orEmpty()
    if (oldStars.isNotEmpty()) client.patchPreferences(token, JSONObject().put("favoriteImport", JSONArray(oldStars.toList()))).onSuccess { migrated ->
     acceptPreferences(migrated); cache.edit().remove("favorites:$userId").apply()
    }
   }.onFailure { syncStatus = context.getString(R.string.assistant_sync_pending) + " · " + it.message.orEmpty() }
  }
 }
 fun patchPreferences(patch: JSONObject) {
  scope.launch {
   preferencesSaving = true
   try { preferenceLock.withLock {
    val token = AccountSessionManager.ensureAccessToken(context) ?: error("Please sign in again")
    client.patchPreferences(token, patch).onSuccess { acceptPreferences(it) }.onFailure { syncStatus = context.getString(R.string.assistant_sync_pending) + " · " + it.message.orEmpty() }
   } } catch (e: CancellationException) { throw e } catch (e: Exception) { syncStatus = e.message.orEmpty() }
   finally { preferencesSaving = false }
  }
 }
 LaunchedEffect(loggedIn, member, userId) {
  if (loggedIn) {
   val token = AccountSessionManager.ensureAccessToken(context) ?: return@LaunchedEffect
   client.models(token).onSuccess { (default, items) -> models = items; selectedModel = default }.onFailure { status = it.message.orEmpty() }
   loadPreferences(token)
   preferences.defaultModel.takeIf { id -> models.any { it.id == id } }?.let { selectedModel = it }
   client.conversations(token).onSuccess { conversations = it }.onFailure { status = it.message.orEmpty() }
   settingsReady = true
  }
 }
 LaunchedEffect(settingsReady, fingerprint) {
  if (!settingsReady) return@LaunchedEffect
  val token = AccountSessionManager.ensureAccessToken(context) ?: return@LaunchedEffect
  val force = !refreshedOnEntry && preferences.autoRefreshPrompts
  refreshedOnEntry = true
  client.prompts(token, snapshot, force).onSuccess { prompts = it.prompts; cache.edit().putString("promptHash:$userId", fingerprint).putString("prompts:$userId", JSONArray(it.prompts).toString()).apply() }
   .onFailure { prompts = emptyList(); status = it.message.orEmpty() }
 }
 suspend fun loadThumbnails(token: String, items: List<AiAttachment>) {
  if (!preferences.imagePreviews) return
  for (item in items.filter { it.mimeType.startsWith("image/") }.take(20)) {
   if (!thumbnails.containsKey(item.attachmentId)) client.thumbnail(token, item).onSuccess {
    if (thumbnails.size >= 32) thumbnails.keys.firstOrNull()?.let { key -> thumbnails.remove(key) }
    thumbnails[item.attachmentId] = it
   }
  }
 }
 fun submitQuestion() {
  if (sending || uploading || recording || transcribing || question.isBlank()) return
  scope.launch {
   val token = AccountSessionManager.ensureAccessToken(context)
   if (token == null) { status = "登录状态已失效，请重新登录"; return@launch }
   sending = true; status = ""; proposal = null
   val sentQuestion = question.trim(); val sentAttachments = attachments; val sentSnapshot = latestSnapshot; val sentFingerprint = latestFingerprint
   val userMessage = AiMessageSummary("local-user-${System.nanoTime()}", "USER", sentQuestion, System.currentTimeMillis(), sentAttachments)
   val assistantId = "local-ai-${System.nanoTime()}"
   messages = messages + userMessage + AiMessageSummary(assistantId, "ASSISTANT", "", System.currentTimeMillis())
   question = ""; attachments = emptyList()
   try {
    client.chat(token, conversationId.takeIf { it.isNotBlank() }, sentQuestion, sentSnapshot, selectedModel, sentAttachments.map { it.attachmentId }, sentSnapshot,
     onDelta = { delta -> scope.launch { messages = messages.map { if (it.messageId == assistantId) it.copy(content = it.content + delta) else it } } },
     onReasoning = { delta -> scope.launch { messages = messages.map { if (it.messageId == assistantId && it.reasoning.length < 128 * 1024) it.copy(reasoning = it.reasoning + delta) else it } } }
    ).onSuccess { result ->
     conversationId = result.conversationId
     messages = messages.map { if (it.messageId == assistantId) it.copy(content = result.reply) else it }
     proposal = result.courseProposal; proposalFingerprint = sentFingerprint
     if (result.truncated) { question = "请从刚才中断的位置继续，不要重复已有内容。"; status = "回答达到长度上限，已准备好继续生成。 · " } else status = ""
     status += context.getString(R.string.ai_cost_points, result.costPoints)
     client.conversations(token).onSuccess { conversations = it }
    }.onFailure { status = it.message ?: "Ask Classing 暂时不可用"; question = sentQuestion; attachments = sentAttachments }
   } finally { sending = false }
  }
 }
 val submitLatest by rememberUpdatedState(::submitQuestion)
 val voiceAutoSend by rememberUpdatedState(preferences.voiceAutoSend)
 val voice = remember(context, userId) {
  AskAiVoiceInput(context, scope, onRecording = { recording = it }, onProcessing = { transcribing = it },
   onCloudAudio = { file ->
    transcribing = true
    transcriptionJob = scope.launch {
     try {
      val token = AccountSessionManager.ensureAccessToken(context) ?: error("Please sign in again")
      client.transcribe(token, file).onSuccess { result ->
       question = result.text; transcribing = false; status = context.getString(R.string.ai_cost_points, result.costPoints)
       if (voiceAutoSend) submitLatest()
      }.onFailure { status = it.message.orEmpty() }
     } catch (e: CancellationException) { throw e } catch (e: Exception) { status = e.message.orEmpty() }
     finally { file.delete(); transcribing = false; transcriptionJob = null }
    }
   }, onError = { status = it })
 }
 val lifecycle = LocalLifecycleOwner.current.lifecycle
 val settingsReadyLatest by rememberUpdatedState(settingsReady)
 DisposableEffect(voice, lifecycle) {
  val observer = LifecycleEventObserver { _, event ->
   if (event == Lifecycle.Event.ON_STOP) { voice.cancel(); transcriptionJob?.cancel() }
   if (event == Lifecycle.Event.ON_RESUME && settingsReadyLatest) scope.launch { AccountSessionManager.ensureAccessToken(context)?.let { loadPreferences(it) } }
  }
  lifecycle.addObserver(observer)
  onDispose { lifecycle.removeObserver(observer); voice.cancel(); transcriptionJob?.cancel() }
 }
 val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> status = context.getString(if (granted) R.string.assistant_voice_hold else R.string.assistant_mic_denied) }
 val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
  if (uris.isNotEmpty()) scope.launch {
   uploading = true
   try {
    val token = AccountSessionManager.ensureAccessToken(context) ?: error("Please sign in again")
    for (uri in uris.take((4 - attachments.size).coerceAtLeast(0))) {
     val mime = context.contentResolver.getType(uri).orEmpty(); var name = "attachment"
     context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) name = cursor.getString(0).orEmpty().ifBlank { name } }
     val temp = File.createTempFile("ask-upload-", ".tmp", context.cacheDir)
     try {
      var preview: Bitmap? = null
      withContext(Dispatchers.IO) {
       if (mime.startsWith("image/")) { val bytes = prepareTimetablePhoto(context, uri); temp.writeBytes(bytes); preview = client.thumbnail(bytes); name = name.substringBeforeLast('.', name) + ".jpg" }
       else context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { out ->
        val buffer = ByteArray(8192); var total = 0
        while (true) { val n = input.read(buffer); if (n < 0) break; total += n; require(total <= 20 * 1024 * 1024) { "Maximum file size is 20 MB" }; out.write(buffer, 0, n) }
       } } ?: error("Could not open file")
      }
      client.uploadAttachment(token, temp, name, if (mime.startsWith("image/")) "image/jpeg" else mime.ifBlank { "application/octet-stream" }).onSuccess {
       attachments = attachments + it; preview?.let { image -> if (thumbnails.size >= 32) thumbnails.keys.firstOrNull()?.let { key -> thumbnails.remove(key) }; thumbnails[it.attachmentId] = image }; status = context.getString(R.string.assistant_file_retention)
      }.onFailure { status = it.message.orEmpty() }
     } finally { temp.delete() }
    }
   } catch (e: CancellationException) { throw e } catch (e: Exception) { status = e.message.orEmpty() } finally { uploading = false }
  }
 }
 BackHandler(subpage.isNotBlank()) { subpage = "" }
 LaunchedEffect(subpage) {
  if (subpage == "usage") AccountSessionManager.ensureAccessToken(context)?.let { client.usage(it).onSuccess { usage = it }.onFailure { status = it.message.orEmpty() } }
  if (subpage == "settings") AccountSessionManager.ensureAccessToken(context)?.let { loadPreferences(it) }
 }
 when (subpage) {
  "settings" -> AskAiPreferencesPage(contentPadding, preferences, models, preferencesSaving, syncStatus, { subpage = "" }, ::patchPreferences)
  "usage" -> AskAiUsagePage(contentPadding, usage, status, { subpage = "" })
  else -> {
   val today = lessons.filter { it.dayOfWeek == currentDate.dayOfWeek }
   val uiState = AssistantUiState(loggedIn = loggedIn, member = member, hasSchedule = lessons.isNotEmpty(), contextLabel = "$currentDate · ${today.size}",
    question = question, sending = sending, status = status, selectedModelId = selectedModel,
    models = models.map { AssistantModelUiModel(it.id, it.name, it.description, it.id in preferences.favoriteModels) },
    attachments = attachments.map { AssistantAttachmentUiModel(it.attachmentId, it.name, thumbnails[it.attachmentId]) },
    uploading = uploading, recording = recording, transcribing = transcribing,
    prompts = prompts, showImagePreviews = preferences.imagePreviews, showReasoning = preferences.showReasoning, showTimestamps = preferences.showTimestamps,
    conversations = conversations.map { AssistantConversationUiModel(it.conversationId, it.title) },
    messages = messages.map { AssistantMessageUiModel(it.messageId, if (it.role == "USER") AssistantMessageRole.User else AssistantMessageRole.Assistant, it.content,
     it.attachments.map { a -> AssistantAttachmentUiModel(a.attachmentId, a.name, thumbnails[a.attachmentId]) }, it.createdAt, it.reasoning) })
   AssistantContent(state = uiState, contentPadding = contentPadding, onBack = onBack, onOpenAccount = onOpenAccount,
    onQuestionChange = { question = it }, onSubmit = ::submitQuestion, onSelectModel = { selectedModel = it },
    onOpenSettings = { subpage = "settings" }, onOpenUsage = { subpage = "usage" },
    onNewConversation = { conversationId = ""; messages = emptyList(); status = ""; proposal = null },
    onOpenConversation = { id -> scope.launch {
     val token = AccountSessionManager.ensureAccessToken(context) ?: return@launch
     status = "正在读取对话…"
     client.messages(token, id).onSuccess { conversationId = id; messages = it; proposal = null; status = ""; loadThumbnails(token, it.flatMap { m -> m.attachments }) }.onFailure { status = it.message.orEmpty() }
    } },
    onToggleStar = { id -> patchPreferences(JSONObject().put(if (id in preferences.favoriteModels) "favoriteRemove" else "favoriteAdd", id)) },
    onAttach = { filePicker.launch(arrayOf("application/pdf", "image/*", "audio/*", "text/*", "application/json", "application/ogg")) },
    onRemoveAttachment = { id -> scope.launch { AccountSessionManager.ensureAccessToken(context)?.let { token -> client.deleteAttachment(token, id).onSuccess { attachments = attachments.filterNot { it.attachmentId == id }; thumbnails.remove(id) }.onFailure { status = it.message.orEmpty() } } } },
    onVoiceStart = { if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) voice.start() else micPermission.launch(Manifest.permission.RECORD_AUDIO) },
    onVoiceFinish = { cancelled -> if (cancelled) { transcriptionJob?.cancel(); transcribing = false }; voice.finish(cancelled) },
    assistantMessage = { MarkdownText(it.substringBefore("```classing-course-actions")) },
    courseProposal = {
     proposal?.let { proposed ->
      Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
       Text(stringResource(R.string.assistant_proposal_title), style = MaterialTheme.typography.titleMedium)
       val actions = proposed.optJSONArray("actions")
       if (actions != null) for (i in 0 until actions.length()) {
        val action = actions.getJSONObject(i); val lesson = action.getJSONObject("lesson")
        Text((if (action.optString("operation") == "create") "+ " else "↻ ") + lesson.optString("title") + " · " + lesson.optInt("dayOfWeek") + " · " + lesson.optString("startTime") + "–" + lesson.optString("endTime"))
       }
       Button(onClick = {
        if (proposalFingerprint != latestFingerprint) status = context.getString(R.string.assistant_proposal_stale)
        else applyProposal(proposed).onSuccess { status = context.getString(R.string.assistant_proposal_applied); proposal = null }.onFailure { status = it.message.orEmpty() }
       }, enabled = !sending) { Text(stringResource(R.string.assistant_proposal_apply)) }
      } }
     }
    })
  }
 }
}

internal fun timetableFingerprint(lessons: List<LessonUi>, timezone: String, mode: WeekNumberMode, startDate: LocalDate, startDay: java.time.DayOfWeek, exceptions: List<ScheduleExceptionUi> = emptyList()): String {
 val data = lessons.sortedBy { it.id }.joinToString("\n") { it.toString() } + "|$timezone|$mode|$startDate|$startDay|" + exceptions.sortedBy { it.id }.joinToString("\n") { it.toString() }
 return MessageDigest.getInstance("SHA-256").digest(data.toByteArray()).joinToString("") { "%02x".format(it) }
}

internal data class AskAiScheduleContext(
    val currentDate: String,
    val currentDayOfWeek: String,
    val currentWeek: Int,
    val timezone: String,
    val weekNumberMode: String,
    val semesterWeekStartDate: String,
    val weekStartDay: String,
)

internal fun buildAskAiScheduleContext(
    currentDate: LocalDate,
    currentWeek: Int,
    timezone: String,
    weekNumberMode: WeekNumberMode,
    semesterWeekStartDate: LocalDate,
    weekStartDay: java.time.DayOfWeek,
): AskAiScheduleContext = AskAiScheduleContext(
    currentDate = currentDate.toString(),
    currentDayOfWeek = currentDate.dayOfWeek.name,
    currentWeek = currentWeek,
    timezone = timezone,
    weekNumberMode = weekNumberMode.name,
    semesterWeekStartDate = semesterWeekStartDate.toString(),
    weekStartDay = weekStartDay.name,
)

internal fun timetableSnapshot(
    lessons: List<LessonUi>,
    currentDate: LocalDate,
    currentWeek: Int,
    timezone: String,
    weekNumberMode: WeekNumberMode,
    semesterWeekStartDate: LocalDate,
    weekStartDay: java.time.DayOfWeek,
): JSONObject {
    val scheduleContext = buildAskAiScheduleContext(
        currentDate = currentDate,
        currentWeek = currentWeek,
        timezone = timezone,
        weekNumberMode = weekNumberMode,
        semesterWeekStartDate = semesterWeekStartDate,
        weekStartDay = weekStartDay,
    )
    return JSONObject()
        .put("currentDate", scheduleContext.currentDate)
        .put("currentDayOfWeek", scheduleContext.currentDayOfWeek)
        .put("currentWeek", scheduleContext.currentWeek)
        .put("timezone", scheduleContext.timezone)
        .put("weekNumberMode", scheduleContext.weekNumberMode)
        .put("semesterWeekStartDate", scheduleContext.semesterWeekStartDate)
        .put("weekStartDay", scheduleContext.weekStartDay)
        .put("lessons", JSONArray().apply {
            lessons.forEach { lesson ->
                put(
                    JSONObject()
                        .put("id", lesson.id)
                        .put("title", lesson.title)
                        .put("teacher", lesson.teacher)
                        .put("location", lesson.location)
                        .put("note", lesson.note)
                        .put("dayOfWeek", lesson.dayOfWeek.name)
                        .put("startTime", lesson.startTime.toString())
                        .put("endTime", lesson.endTime.toString())
                        .put("startWeek", lesson.startWeek)
                        .put("endWeek", lesson.endWeek)
                        .put("weekParity", lesson.weekParity.name),
                )
            }
        })
}
