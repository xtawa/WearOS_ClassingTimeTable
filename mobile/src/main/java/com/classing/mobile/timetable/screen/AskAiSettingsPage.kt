package com.xtawa.classingtime.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
    var selectedModel by remember { mutableStateOf("deepseek-v4-flash") }
    var conversations by remember { mutableStateOf<List<AiConversationSummary>>(emptyList()) }
    var messages by remember { mutableStateOf<List<AiMessageSummary>>(emptyList()) }
    var conversationId by remember { mutableStateOf("") }
    var question by remember(initialQuestion) { mutableStateOf(initialQuestion) }
    var autoSubmitPending by remember(initialQuestion) { mutableStateOf(initialQuestion.isNotBlank()) }
    var status by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    LaunchedEffect(loggedIn) {
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
        models = models.map { AssistantModelUiModel(it.id, it.name, it.description) },
        conversations = conversations.map { AssistantConversationUiModel(it.conversationId, it.title) },
        messages = messages.map {
            AssistantMessageUiModel(
                id = it.messageId,
                role = if (it.role == "USER") AssistantMessageRole.User else AssistantMessageRole.Assistant,
                content = it.content,
            )
        },
    )

    fun submitQuestion() {
        if (sending || question.isBlank()) return
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
            client.chat(
                token,
                conversationId.takeIf { it.isNotBlank() },
                sentQuestion,
                snapshot,
                selectedModel,
            ).onSuccess { result ->
                conversationId = result.conversationId
                messages = messages +
                    AiMessageSummary(
                        "local-user-${System.nanoTime()}",
                        "USER",
                        sentQuestion,
                        System.currentTimeMillis(),
                    ) +
                    AiMessageSummary(
                        "local-ai-${System.nanoTime()}",
                        "ASSISTANT",
                        result.reply,
                        System.currentTimeMillis(),
                    )
                if (result.truncated) {
                    question = "请从刚才中断的位置继续，不要重复已有内容。"
                    status = "回答达到长度上限，已准备好继续生成。"
                } else {
                    question = ""
                    status = ""
                }
                client.conversations(token).onSuccess { conversations = it }
            }.onFailure {
                status = it.message ?: "Ask Classing 暂时不可用"
            }
            sending = false
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
