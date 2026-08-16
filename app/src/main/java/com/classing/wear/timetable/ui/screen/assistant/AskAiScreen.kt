package com.classing.wear.timetable.ui.screen.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.classing.wear.timetable.R
import com.classing.wear.timetable.account.WearAiApiClient
import com.classing.wear.timetable.account.WearAiConversation
import com.classing.wear.timetable.account.WearAiMessage
import com.classing.wear.timetable.account.WearAiModel
import com.classing.wear.timetable.account.WearDirectAccountSessionManager
import com.classing.wear.timetable.core.time.TimeProvider
import com.classing.wear.timetable.core.time.WeekCalculator
import com.classing.wear.timetable.domain.model.LessonOccurrence
import com.classing.wear.timetable.domain.repository.ScheduleRepository
import com.classing.wear.timetable.ui.component.screenPadding
import java.time.ZoneId
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun AskAiScreen(
    scheduleRepository: ScheduleRepository,
    timeProvider: TimeProvider,
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(context) { WearAiApiClient(context) }
    val today = timeProvider.today()
    val weekStart = WeekCalculator.weekStart(today)
    val semester by scheduleRepository.observeActiveSemester()
        .collectAsStateWithLifecycle(initialValue = null)
    val weekSchedule by scheduleRepository.observeWeekSchedule(weekStart)
        .collectAsStateWithLifecycle(initialValue = com.classing.wear.timetable.domain.model.WeekSchedule(0, emptyMap()))

    var loggedIn by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<WearAiModel>>(emptyList()) }
    var selectedModel by remember { mutableStateOf("deepseek-v4-flash") }
    var conversations by remember { mutableStateOf<List<WearAiConversation>>(emptyList()) }
    var messages by remember { mutableStateOf<List<WearAiMessage>>(emptyList()) }
    var conversationId by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    suspend fun accessToken(): String? {
        val session = WearDirectAccountSessionManager.ensureSession(context)
        loggedIn = session != null
        return session?.accessToken
    }

    suspend fun loadAssistant() {
        val token = accessToken() ?: return
        client.models(token)
            .onSuccess { (defaultModel, items) ->
                models = items
                if (items.none { it.id == selectedModel }) selectedModel = defaultModel
            }
            .onFailure { status = it.message.orEmpty() }
        client.conversations(token)
            .onSuccess { conversations = it }
            .onFailure { status = it.message.orEmpty() }
    }

    LaunchedEffect(Unit) { loadAssistant() }

    val lessons = remember(weekSchedule) {
        weekSchedule.days.values.flatten().sortedBy { it.startAt }
    }
    val todayLessons = remember(lessons, today) { lessons.filter { it.date == today } }
    val contextLabel = when {
        semester == null -> stringResource(R.string.ask_ai_context_no_schedule)
        todayLessons.isEmpty() -> stringResource(R.string.ask_ai_context_no_classes)
        else -> stringResource(
            R.string.ask_ai_context_classes,
            todayLessons.size,
            todayLessons.first().course.name,
        )
    }
    val quickPrompts = listOf(
        stringResource(R.string.ask_ai_prompt_next),
        stringResource(R.string.ask_ai_prompt_today),
        stringResource(R.string.ask_ai_prompt_free),
    )

    fun submit() {
        val submitted = question.trim()
        if (submitted.isBlank() || sending || lessons.isEmpty()) return
        scope.launch {
            val token = accessToken()
            if (token == null) {
                status = context.getString(R.string.ask_ai_sign_in_required)
                return@launch
            }
            sending = true
            status = ""
            val snapshot = if (conversationId.isBlank()) {
                buildWearTimetableSnapshot(today.toString(), weekSchedule.weekIndex, lessons)
            } else {
                null
            }
            client.chat(
                accessToken = token,
                conversationId = conversationId.takeIf { it.isNotBlank() },
                message = submitted,
                timetableSnapshot = snapshot,
                model = selectedModel,
            ).onSuccess { result ->
                conversationId = result.conversationId
                messages = messages +
                    WearAiMessage("local-user-${System.nanoTime()}", "USER", submitted) +
                    WearAiMessage("local-ai-${System.nanoTime()}", "ASSISTANT", result.reply)
                question = if (result.truncated) {
                    context.getString(R.string.ask_ai_continue_prompt)
                } else {
                    ""
                }
                status = if (result.truncated) {
                    context.getString(R.string.ask_ai_truncated)
                } else {
                    ""
                }
                client.conversations(token).onSuccess { conversations = it }
            }.onFailure { error ->
                status = error.message ?: context.getString(R.string.ask_ai_unavailable)
            }
            sending = false
        }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberScalingLazyListState(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.ask_ai_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
        }
        item { ContextAnchor(contextLabel) }

        if (!loggedIn) {
            item {
                AssistantCard {
                    Text(stringResource(R.string.ask_ai_sign_in_title), fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.ask_ai_sign_in_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onOpenAccount, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.ask_ai_open_account))
                    }
                }
            }
        } else if (lessons.isEmpty()) {
            item {
                AssistantCard {
                    Text(stringResource(R.string.ask_ai_no_schedule_title), fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.ask_ai_no_schedule_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            if (messages.isEmpty() && !sending) {
                item {
                    AssistantCard {
                        Text(stringResource(R.string.ask_ai_welcome), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.ask_ai_welcome_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(quickPrompts) { prompt ->
                    Button(
                        onClick = { question = prompt },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text(prompt, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            items(messages, key = { it.id }) { message ->
                if (message.role == "USER") {
                    Text(
                        text = message.content,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    AssistantCard {
                        Text(
                            text = stringResource(R.string.ask_ai_answer),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(message.content, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (sending) {
                item {
                    AssistantCard(
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                            Text(stringResource(R.string.ask_ai_reading_schedule))
                        }
                    }
                }
            }

            if (status.isNotBlank()) {
                item {
                    Text(
                        text = status,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.ask_ai_input_label)) },
                    enabled = !sending,
                    maxLines = 3,
                )
            }
            item {
                Button(
                    onClick = ::submit,
                    enabled = question.isNotBlank() && !sending,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.ask_ai_send))
                }
            }

            if (conversations.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.ask_ai_recent),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(conversations.take(3), key = { it.id }) { conversation ->
                    Card(
                        onClick = {
                            scope.launch {
                                val token = accessToken() ?: return@launch
                                status = context.getString(R.string.ask_ai_loading_conversation)
                                client.messages(token, conversation.id)
                                    .onSuccess {
                                        conversationId = conversation.id
                                        messages = it
                                        status = ""
                                    }
                                    .onFailure { status = it.message.orEmpty() }
                            }
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Text(
                            conversation.title,
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text(stringResource(R.string.detail_back))
            }
        }
    }
}

@Composable
private fun ContextAnchor(label: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AssistantCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

internal fun buildWearTimetableSnapshot(
    currentDate: String,
    currentWeek: Int,
    lessons: List<LessonOccurrence>,
): JSONObject = JSONObject()
    .put("currentDate", currentDate)
    .put("currentDayOfWeek", java.time.LocalDate.parse(currentDate).dayOfWeek.name)
    .put("currentWeek", currentWeek)
    .put("timezone", ZoneId.systemDefault().id)
    .put("weekNumberMode", "SEMESTER")
    .put("semesterWeekStartDate", lessons.minOfOrNull { it.date }?.minusWeeks((currentWeek - 1).coerceAtLeast(0).toLong())?.toString().orEmpty())
    .put("weekStartDay", WeekCalculator.weekStart(java.time.LocalDate.parse(currentDate)).dayOfWeek.name)
    .put("lessons", JSONArray().apply {
        lessons.forEach { lesson ->
            put(
                JSONObject()
                    .put("title", lesson.course.name)
                    .put("teacher", lesson.course.teacher)
                    .put("location", lesson.course.classroom)
                    .put("note", lesson.course.note)
                    .put("dayOfWeek", lesson.session.dayOfWeek.name)
                    .put("startTime", lesson.timeSlot.startTime.toString())
                    .put("endTime", lesson.timeSlot.endTime.toString())
                    .put("startWeek", lesson.session.weekRule.startWeek)
                    .put("endWeek", lesson.session.weekRule.endWeek)
                    .put("weekParity", lesson.session.weekRule.parity.name),
            )
        }
    })
