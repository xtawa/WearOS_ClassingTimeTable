package com.xtawa.classingtime.account

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AiUsageSummary(
    val limit: Int,
    val used: Int,
    val reserved: Int,
    val creditBalance: Int,
    val creditAvailable: Int,
    val creditFrozen: Boolean,
    val isMember: Boolean,
    val resetAt: Long,
)

data class AiModelOption(val id: String, val name: String, val description: String)
data class AiConversationSummary(val conversationId: String, val title: String, val updatedAt: Long)
data class AiMessageSummary(val messageId: String, val role: String, val content: String, val createdAt: Long)
data class AiChatResult(val conversationId: String, val reply: String, val truncated: Boolean)

class AiApiClient(private val baseUrl: String = AccountApiClient.BASE_URL) {
    suspend fun usage(accessToken: String): Result<AiUsageSummary> = request("GET", "/api/v1/ai/usage/me", accessToken).map { body ->
        val usage = body.optJSONObject("usage") ?: body
        AiUsageSummary(
            usage.optInt("limit"),
            usage.optInt("used"),
            usage.optInt("reserved"),
            usage.optInt("creditBalance"),
            usage.optInt("creditAvailable"),
            usage.optBoolean("creditFrozen"),
            usage.optBoolean("isMember"),
            usage.optLong("resetAt"),
        )
    }

    suspend fun models(accessToken: String): Result<Pair<String, List<AiModelOption>>> = request("GET", "/api/v1/ai/models", accessToken).map { body ->
        body.optString("defaultModel", "deepseek-v4-flash") to body.optJSONArray("models").toObjects { item ->
            AiModelOption(item.optString("id"), item.optString("name"), item.optString("description"))
        }
    }

    suspend fun conversations(accessToken: String): Result<List<AiConversationSummary>> = request("GET", "/api/v1/ai/conversations?limit=30", accessToken).map { body ->
        body.optJSONArray("conversations").toObjects { item ->
            AiConversationSummary(item.optString("conversationId"), item.optString("title"), item.optLong("updatedAt"))
        }
    }

    suspend fun messages(accessToken: String, conversationId: String): Result<List<AiMessageSummary>> = request("GET", "/api/v1/ai/conversations/$conversationId/messages", accessToken).map { body ->
        body.optJSONArray("messages").toObjects { item ->
            AiMessageSummary(item.optString("messageId"), item.optString("role"), item.optString("content"), item.optLong("createdAt"))
        }
    }

    suspend fun chat(accessToken: String, conversationId: String?, message: String, timetableSnapshot: JSONObject?, model: String): Result<AiChatResult> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("clientRequestId", java.util.UUID.randomUUID().toString()).put("message", message).put("model", model)
            if (!conversationId.isNullOrBlank()) body.put("conversationId", conversationId)
            if (conversationId.isNullOrBlank()) body.put("timetableSnapshot", timetableSnapshot ?: throw IllegalArgumentException("timetable required"))
            val connection = open("POST", "/api/v1/ai/chat", accessToken, body)
            try {
                val code = connection.responseCode
                if (code !in 200..299) throw apiError(connection, code)
                var currentConversationId = conversationId.orEmpty()
                val reply = StringBuilder()
                var truncated = false
                var event = ""
                connection.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.forEach { line ->
                    when {
                        line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                        line.startsWith("data:") -> {
                            val data = JSONObject(line.removePrefix("data:").trim())
                            when (event) {
                                "conversation" -> currentConversationId = data.optString("conversationId", currentConversationId)
                                "delta" -> reply.append(data.optString("text"))
                                "done" -> truncated = data.optBoolean("truncated", false)
                                "error" -> throw AccountApiException(502, data.optString("code"), message = data.optString("message", "Ask AI failed"))
                            }
                            event = ""
                        }
                    }
                } }
                AiChatResult(currentConversationId, reply.toString(), truncated)
            } finally { connection.disconnect() }
        }
    }

    private suspend fun request(method: String, path: String, token: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = open(method, path, token, null)
            try {
                val code = connection.responseCode
                if (code !in 200..299) throw apiError(connection, code)
                JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            } finally { connection.disconnect() }
        }
    }

    private fun open(method: String, path: String, token: String, body: JSONObject?): HttpURLConnection =
        (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 10_000; readTimeout = 200_000; doInput = true
            setRequestProperty("Authorization", "Bearer $token"); setRequestProperty("Accept", "application/json, text/event-stream")
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json; charset=utf-8"); OutputStreamWriter(outputStream, Charsets.UTF_8).use { it.write(body.toString()) } }
        }

    private fun apiError(connection: HttpURLConnection, status: Int): AccountApiException {
        val raw = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val body = runCatching { JSONObject(raw) }.getOrNull()
        return AccountApiException(status, body?.optString("code").orEmpty(), message = body?.optString("message").orEmpty().ifBlank { "Ask AI request failed" })
    }

    private fun <T> JSONArray?.toObjects(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return buildList { for (index in 0 until length()) optJSONObject(index)?.let { add(transform(it)) } }
    }
}
