package com.xtawa.classingtime.screen

import androidx.compose.foundation.layout.PaddingValues
import android.Manifest
import android.content.pm.PackageManager
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import com.xtawa.classingtime.account.AiAttachment
import com.xtawa.classingtime.ui.assistant.AssistantAttachmentUiModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.xtawa.classingtime.R
import com.xtawa.classingtime.account.AiApiClient
import com.xtawa.classingtime.account.AiConversationSummary
import com.xtawa.classingtime.account.AiMessageSummary
import com.xtawa.classingtime.account.AiModelOption
import com.xtawa.classingtime.sync.AccountSessionManager
import com.xtawa.classingtime.ui.assistant.AssistantContent
import com.xtawa.classingtime.ui.assistant.AssistantConversationUiModel
import com.xtawa.classingtime.ui.assistant.AssistantMessageRole
import com.xtawa.classingtime.ui.assistant.AssistantMessageUiModel
import com.xtawa.classingtime.ui.assistant.AssistantModelUiModel
import com.xtawa.classingtime.ui.assistant.AssistantUiState
import java.time.LocalDate
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun AskAiSettingsPage(
    contentPadding: PaddingValues,
    loggedIn: Boolean,
    member: Boolean,
    userId: String = "",
    lessons: List<LessonUi>,
    currentDate: LocalDate,
    currentWeek: Int,
    timezone: String,
    weekNumberMode: WeekNumberMode,
    semesterWeekStartDate: LocalDate,
    weekStartDay: java.time.DayOfWeek,
    initialQuestion: String = "",
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(context) { AiApiClient(appContext = context.applicationContext) }
    var models by remember { mutableStateOf<List<AiModelOption>>(emptyList()) }
    var selectedModel by remember { mutableStateOf("") }
    var conversations by remember { mutableStateOf<List<AiConversationSummary>>(emptyList()) }
    var messages by remember { mutableStateOf<List<AiMessageSummary>>(emptyList()) }
    var conversationId by remember { mutableStateOf("") }
    var question by remember(initialQuestion) { mutableStateOf(initialQuestion) }
    var autoSubmitPending by remember(initialQuestion) { mutableStateOf(initialQuestion.isNotBlank()) }
    var status by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var attachments by remember(userId) { mutableStateOf<List<AiAttachment>>(emptyList()) }
    var uploading by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    var transcriptionJob by remember { mutableStateOf<Job?>(null) }
    val prefs = remember(context) { context.getSharedPreferences("ask_ai", 0) }
    val favoritesKey = "favorites:$userId"
    var stars by remember(userId) { mutableStateOf(prefs.getStringSet(favoritesKey, emptySet()).orEmpty().toSet()) }


    LaunchedEffect(loggedIn, member, userId) {
        if (loggedIn) {
            AccountSessionManager.ensureAccessToken(context)?.let { token ->
                client.models(token).onSuccess { (defaultModel, items) ->
                    models = items
                    if (items.none { it.id == selectedModel }) selectedModel = defaultModel
                }.onFailure { status = it.message.orEmpty() }
                client.conversations(token)
                    .onSuccess { conversations = it }
                    .onFailure { status = it.message.orEmpty() }
            }
        }
    }

    val todayLessons = remember(lessons, currentDate) {
        lessons.filter { it.dayOfWeek == currentDate.dayOfWeek }.sortedBy { it.startTime }
    }
    val dayLabel = currentDate.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercaseChar() }
    val contextLabel = if (todayLessons.isEmpty()) {
        "$dayLabel · No classes"
    } else {
        "$dayLabel · ${todayLessons.size} classes · ${todayLessons.first().title} first"
    }
    val uiState = AssistantUiState(
        loggedIn = loggedIn,
        member = member,
        hasSchedule = lessons.isNotEmpty(),
        contextLabel = contextLabel,
        question = question,
        sending = sending,
        status = status,
        selectedModelId = selectedModel,
        models = models.map { AssistantModelUiModel(it.id, it.name, it.description, it.id in stars) },
        attachments = attachments.map { AssistantAttachmentUiModel(it.attachmentId, it.name) },
        uploading = uploading, recording = recording, transcribing = transcribing,
        conversations = conversations.map { AssistantConversationUiModel(it.conversationId, it.title) },
        messages = messages.map {
            AssistantMessageUiModel(
                id = it.messageId,
                role = if (it.role == "USER") AssistantMessageRole.User else AssistantMessageRole.Assistant,
                content = it.content + if (it.attachments.isNotEmpty()) "\n" + it.attachments.joinToString(" · ") { a -> "📎 " + a.name } else "",
            )
        },
    )

    fun submitQuestion() {
        if (sending || uploading || recording || question.isBlank()) return
        autoSubmitPending = false
        scope.launch {
            val token = AccountSessionManager.ensureAccessToken(context)
            if (token == null) {
                status = "登录状态已失效，请重新登录"
                return@launch
            }
            sending = true
            status = ""
            val snapshot = if (conversationId.isBlank()) {
                timetableSnapshot(
                    lessons = lessons,
                    currentDate = currentDate,
                    currentWeek = currentWeek,
                    timezone = timezone,
                    weekNumberMode = weekNumberMode,
                    semesterWeekStartDate = semesterWeekStartDate,
                    weekStartDay = weekStartDay,
                )
            } else {
                null
            }
            val sentQuestion = question.trim()
            val sentAttachments = attachments
            client.chat(
                token,
                conversationId.takeIf { it.isNotBlank() },
                sentQuestion,
                snapshot,
                selectedModel,
                sentAttachments.map { it.attachmentId },
            ).onSuccess { result ->
                conversationId = result.conversationId
                attachments = emptyList()
                messages = messages +
                    AiMessageSummary(
                        "local-user-${System.nanoTime()}",
                        "USER",
                        sentQuestion,
                        System.currentTimeMillis(),
                        sentAttachments,
                    ) +
                    AiMessageSummary(
                        "local-ai-${System.nanoTime()}",
                        "ASSISTANT",
                        result.reply,
                        System.currentTimeMillis(),
                    )
                if (result.truncated) {
                    question = "请从刚才中断的位置继续，不要重复已有内容。"
                    status = "回答达到长度上限，已准备好继续生成。 · ${context.getString(R.string.ai_cost_points, result.costPoints)}"
                } else {
                    question = ""
                    status = context.getString(R.string.ai_cost_points, result.costPoints)
                }
                client.conversations(token).onSuccess { conversations = it }
            }.onFailure {
                status = it.message ?: "Ask Classing 暂时不可用"
            }
            sending = false
        }
    }

    val submitLatest by rememberUpdatedState(::submitQuestion)
    val voice = remember(context, userId) {
        AskAiVoiceInput(context, scope, onRecording = { recording = it }, onProcessing = { transcribing = it }, onText = { text -> question = text; submitLatest() },
            onCloudAudio = { file ->
                transcribing = true
                transcriptionJob = scope.launch {
                    try {
                        val token = AccountSessionManager.ensureAccessToken(context) ?: error("Please sign in again")
                        client.transcribe(token, file).onSuccess { text -> question = text; transcribing = false; submitLatest() }
                            .onFailure { status = it.message.orEmpty() }
                    } finally { file.delete(); transcribing = false; transcriptionJob = null }
                }
            }, onError = { status = it })
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(voice, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { voice.cancel(); transcriptionJob?.cancel() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); voice.cancel(); transcriptionJob?.cancel() }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = context.getString(if (granted) R.string.assistant_voice_hold else R.string.assistant_mic_denied)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            uploading = true
            try {
                val token = AccountSessionManager.ensureAccessToken(context) ?: error("Please sign in again")
                for (uri in uris.take(4 - attachments.size)) {
                    val mime = context.contentResolver.getType(uri).orEmpty()
                    var name = "attachment"
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) name = cursor.getString(0).orEmpty().ifBlank { name }
                    }
                    val temp = File.createTempFile("ask-upload-", ".tmp", context.cacheDir)
                    try {
                        withContext(Dispatchers.IO) {
                            if (mime.startsWith("image/")) {
                                temp.writeBytes(prepareTimetablePhoto(context, uri)); name = name.substringBeforeLast('.', name) + ".jpg"
                            } else {
                                context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { out ->
                                    val buffer = ByteArray(8192); var total = 0
                                    while (true) { val n = input.read(buffer); if (n < 0) break; total += n; require(total <= 20 * 1024 * 1024) { "Maximum file size is 20 MB" }; out.write(buffer, 0, n) }
                                } } ?: error("Could not open file")
                            }
                        }
                        client.uploadAttachment(token, temp, name, if (mime.startsWith("image/")) "image/jpeg" else mime.ifBlank { "application/octet-stream" })
                            .onSuccess { attachments = attachments + it; status = context.getString(R.string.assistant_file_retention) }
                            .onFailure { status = it.message.orEmpty() }
                    } finally { temp.delete() }
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { status = e.message.orEmpty() }
            finally { uploading = false }
        }
    }

    LaunchedEffect(autoSubmitPending, loggedIn, models, lessons) {
        if (autoSubmitPending && loggedIn && models.isNotEmpty() && lessons.isNotEmpty()) {
            submitQuestion()
        }
    }

    AssistantContent(
        state = uiState,
        contentPadding = contentPadding,
        onBack = onBack,
        onOpenAccount = onOpenAccount,
        onQuestionChange = { question = it },
        onSubmit = ::submitQuestion,
        onSelectModel = { selectedModel = it },
        onNewConversation = {
            conversationId = ""
            messages = emptyList()
            status = ""
        },
        onOpenConversation = { selectedConversationId ->
            scope.launch {
                val token = AccountSessionManager.ensureAccessToken(context) ?: return@launch
                conversationId = selectedConversationId
                status = "正在读取对话…"
                client.messages(token, selectedConversationId)
                    .onSuccess {
                        messages = it
                        status = ""
                    }
                    .onFailure { status = it.message ?: "读取对话失败" }
            }
        },
        onToggleStar = { id ->
            stars = if (id in stars) stars - id else stars + id
            prefs.edit().putStringSet(favoritesKey, stars).apply()
        },
        onAttach = { filePicker.launch(arrayOf("application/pdf", "image/*", "audio/*", "text/*", "application/json", "application/ogg")) },
        onRemoveAttachment = { id ->
            scope.launch { AccountSessionManager.ensureAccessToken(context)?.let { token ->
                client.deleteAttachment(token, id).onSuccess { attachments = attachments.filterNot { it.attachmentId == id } }.onFailure { status = it.message.orEmpty() }
            } }
        },
        onVoiceStart = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) voice.start()
            else micPermission.launch(Manifest.permission.RECORD_AUDIO)
        },
        onVoiceFinish = { cancelled ->
            if (cancelled) { transcriptionJob?.cancel(); transcribing = false }
            voice.finish(cancelled)
        },
        assistantMessage = { MarkdownText(it) },
    )
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
