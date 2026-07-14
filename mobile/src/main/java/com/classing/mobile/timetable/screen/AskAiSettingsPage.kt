package com.xtawa.classingtime.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.xtawa.classingtime.account.AiUsageSummary
import com.xtawa.classingtime.sync.AccountSessionManager
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun AskAiSettingsPage(
    contentPadding: PaddingValues,
    loggedIn: Boolean,
    member: Boolean,
    lessons: List<LessonUi>,
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { AiApiClient() }
    var usage by remember { mutableStateOf<AiUsageSummary?>(null) }
    var conversationId by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    LaunchedEffect(loggedIn, member) {
        if (loggedIn && member) {
            AccountSessionManager.ensureAccessToken(context)?.let { token ->
                client.usage(token).onSuccess { usage = it }.onFailure { status = it.message.orEmpty() }
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
            !member -> AccessCard("Ask AI 为会员功能，请开通会员后使用。", "开通会员", onOpenAccount)
            lessons.isEmpty() -> Text("导入课表后即可开始提问。", color = MaterialTheme.colorScheme.error)
            else -> {
                usage?.let { item ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Text("本月已用 ${item.used}${if (item.limit < 0) " 次（不限量）" else " / ${item.limit} 次"}", modifier = Modifier.padding(14.dp))
                    }
                }
                if (answer.isNotBlank()) Card { Text(answer, modifier = Modifier.padding(14.dp)) }
                OutlinedTextField(question, { question = it }, modifier = Modifier.fillMaxWidth(), label = { Text("关于课表的问题") }, minLines = 3, enabled = !sending)
                Button(enabled = !sending && question.isNotBlank(), onClick = {
                    scope.launch {
                        val token = AccountSessionManager.ensureAccessToken(context)
                        if (token == null) { status = "登录状态已失效，请重新登录"; return@launch }
                        sending = true; status = "正在询问…"
                        val snapshot = if (conversationId.isBlank()) timetableSnapshot(lessons) else null
                        client.chat(token, conversationId.takeIf { it.isNotBlank() }, question.trim(), snapshot)
                            .onSuccess { (id, reply) -> conversationId = id; answer = reply; question = ""; status = ""; client.usage(token).onSuccess { usage = it } }
                            .onFailure { status = it.message ?: "Ask AI 暂时不可用" }
                        sending = false
                    }
                }) { Text(if (sending) "发送中…" else "发送") }
            }
        }
        if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun AccessCard(message: String, action: String, onClick: () -> Unit) {
    Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(message); Button(onClick = onClick) { Text(action) } } }
}

private fun timetableSnapshot(lessons: List<LessonUi>): JSONObject = JSONObject().put("lessons", JSONArray().apply {
    lessons.forEach { lesson -> put(JSONObject().put("title", lesson.title).put("teacher", lesson.teacher).put("location", lesson.location).put("note", lesson.note).put("dayOfWeek", lesson.dayOfWeek.name).put("startTime", lesson.startTime.toString()).put("endTime", lesson.endTime.toString()).put("startWeek", lesson.startWeek).put("endWeek", lesson.endWeek).put("weekParity", lesson.weekParity.name)) }
})
