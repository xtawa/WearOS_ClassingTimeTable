package com.xtawa.classingtime.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xtawa.classingtime.account.AiApiClient
import com.xtawa.classingtime.account.AiConversationSummary
import com.xtawa.classingtime.account.AiMessageSummary
import com.xtawa.classingtime.account.AiModelOption
import com.xtawa.classingtime.sync.AccountSessionManager
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
    var question by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            AccountSessionManager.ensureAccessToken(context)?.let { token ->
                client.models(token).onSuccess { (defaultModel, items) ->
                    models = items
                    if (items.none { it.id == selectedModel }) selectedModel = defaultModel
                }.onFailure { status = it.message.orEmpty() }
                client.conversations(token).onSuccess { conversations = it }.onFailure { status = it.message.orEmpty() }
            }
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 返回") }
        Text("Ask AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("基于当前课表提问。新对话会将此课表快照随首个问题发送。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            !loggedIn -> AccessCard("请先登录后使用 Ask AI", "去登录", onOpenAccount)
            else -> {
                if (!member) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("免费账户每月可使用 500 点 AI 额度。", fontWeight = FontWeight.SemiBold)
                            Text("永久额度仅限有效会员使用；会员过期后将冻结，续费即可恢复。", color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
                if (lessons.isEmpty() && conversationId.isBlank()) Text("导入课表后可新建对话；已有对话仍可继续读取。", color = MaterialTheme.colorScheme.error)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("选择模型", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            models.forEach { item ->
                                FilterChip(
                                    selected = selectedModel == item.id,
                                    onClick = { selectedModel = item.id },
                                    label = { Text(item.name.removePrefix("DeepSeek ")) },
                                )
                            }
                        }
                        models.firstOrNull { it.id == selectedModel }?.let { Text(it.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                OutlinedTextField(question, { question = it }, modifier = Modifier.fillMaxWidth(), label = { Text("关于课表的问题") }, minLines = 3, enabled = !sending)
                Button(enabled = !sending && question.isNotBlank() && selectedModel.isNotBlank() && (conversationId.isNotBlank() || lessons.isNotEmpty()), onClick = {
                    scope.launch {
                        val token = AccountSessionManager.ensureAccessToken(context)
                        if (token == null) { status = "登录状态已失效，请重新登录"; return@launch }
                        sending = true; status = "正在询问…"
                        val snapshot = if (conversationId.isBlank()) timetableSnapshot(
                            lessons = lessons,
                            currentDate = currentDate,
                            currentWeek = currentWeek,
                            timezone = timezone,
                            weekNumberMode = weekNumberMode,
                            semesterWeekStartDate = semesterWeekStartDate,
                            weekStartDay = weekStartDay,
                        ) else null
                        val sentQuestion = question.trim()
                        client.chat(token, conversationId.takeIf { it.isNotBlank() }, sentQuestion, snapshot, selectedModel)
                            .onSuccess { result ->
                                conversationId = result.conversationId
                                messages = messages + AiMessageSummary("local-user-${System.nanoTime()}", "USER", sentQuestion, System.currentTimeMillis()) + AiMessageSummary("local-ai-${System.nanoTime()}", "ASSISTANT", result.reply, System.currentTimeMillis())
                                if (result.truncated) {
                                    question = "请从刚才中断的位置继续，不要重复已有内容。"
                                    status = "回答达到长度上限，已准备好继续生成。"
                                } else {
                                    question = ""; status = ""
                                }
                                client.conversations(token).onSuccess { conversations = it }
                            }
                            .onFailure { status = it.message ?: "Ask AI 暂时不可用" }
                        sending = false
                    }
                }) { Text(if (sending) "发送中…" else "发送") }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("过往对话", fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { conversationId = ""; messages = emptyList(); status = "" }) { Text("新对话") }
                        }
                        if (conversations.isEmpty()) Text("暂无历史对话", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        conversations.forEach { item ->
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val token = AccountSessionManager.ensureAccessToken(context) ?: return@launch
                                        conversationId = item.conversationId
                                        status = "正在读取对话…"
                                        client.messages(token, item.conversationId)
                                            .onSuccess { messages = it; status = "" }
                                            .onFailure { status = it.message ?: "读取对话失败" }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(item.title, modifier = Modifier.fillMaxWidth()) }
                        }
                    }
                }
                messages.forEach { item ->
                    Card(colors = CardDefaults.cardColors(containerColor = if (item.role == "USER") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (item.role == "USER") "你" else "Ask AI", fontWeight = FontWeight.SemiBold)
                            if (item.role == "USER") Text(item.content) else MarkdownText(item.content)
                        }
                    }
                }
            }
        }
        if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun AccessCard(message: String, action: String, onClick: () -> Unit) {
    Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(message); Button(onClick = onClick) { Text(action) } } }
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
    val context = buildAskAiScheduleContext(currentDate, currentWeek, timezone, weekNumberMode, semesterWeekStartDate, weekStartDay)
    return JSONObject()
        .put("currentDate", context.currentDate)
        .put("currentDayOfWeek", context.currentDayOfWeek)
        .put("currentWeek", context.currentWeek)
        .put("timezone", context.timezone)
        .put("weekNumberMode", context.weekNumberMode)
        .put("semesterWeekStartDate", context.semesterWeekStartDate)
        .put("weekStartDay", context.weekStartDay)
        .put("lessons", JSONArray().apply {
        lessons.forEach { lesson -> put(JSONObject().put("title", lesson.title).put("teacher", lesson.teacher).put("location", lesson.location).put("note", lesson.note).put("dayOfWeek", lesson.dayOfWeek.name).put("startTime", lesson.startTime.toString()).put("endTime", lesson.endTime.toString()).put("startWeek", lesson.startWeek).put("endWeek", lesson.endWeek).put("weekParity", lesson.weekParity.name)) }
        })
}
