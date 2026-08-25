package com.classing.wear.timetable.account

import android.content.Context
import com.classing.wear.timetable.security.ClientIntegrity
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class WearAiModel(val id: String, val name: String, val description: String)
data class WearAiConversation(val id: String, val title: String, val updatedAt: Long)
data class WearAiMessage(val id: String, val role: String, val content: String)
data class WearAiChatResult(val conversationId: String, val reply: String, val truncated: Boolean)

class WearAiApiException(
    val statusCode: Int,
    val errorCode: String,
    message: String,
) : IllegalStateException(message)

class WearAiApiClient(
    context: Context,
    private val baseUrl: String = WearQrAuthApiClient.BASE_URL,
) {
    private val appContext = context.applicationContext

    suspend fun models(accessToken: String): Result<Pair<String, List<WearAiModel>>> =
        request("GET", "/api/v1/ai/models", accessToken).map {
            MIMO_FLASH_MODEL_ID to listOf(
                WearAiModel(
                    id = MIMO_FLASH_MODEL_ID,
                    name = "Flash",
                    description = "Fast responses for everyday timetable questions",
                ),
                WearAiModel(
                    id = MIMO_PRO_MODEL_ID,
                    name = "Pro",
                    description = "Higher quality for complex timetable reasoning",
                ),
            )
        }

    suspend fun conversations(accessToken: String): Result<List<WearAiConversation>> =
        request("GET", "/api/v1/ai/conversations?limit=8", accessToken).map { body ->
            body.optJSONArray("conversations").toObjects { item ->
                WearAiConversation(
                    id = item.optString("conversationId"),
                    title = item.optString("title"),
                    updatedAt = item.optLong("updatedAt"),
                )
            }
        }

    suspend fun messages(accessToken: String, conversationId: String): Result<List<WearAiMessage>> =
        request("GET", "/api/v1/ai/conversations/$conversationId/messages", accessToken).map { body ->
            body.optJSONArray("messages").toObjects { item ->
                WearAiMessage(
                    id = item.optString("messageId"),
                    role = item.optString("role"),
                    content = item.optString("content"),
                )
            }
        }

    suspend fun chat(
        accessToken: String,
        conversationId: String?,
        message: String,
        timetableSnapshot: JSONObject?,
        model: String,
    ): Result<WearAiChatResult> = withContext(Dispatchers.IO) {
        runCatching {
            ClientIntegrity.ensureTrusted(appContext, baseUrl).getOrThrow()
            val body = JSONObject()
                .put("clientRequestId", UUID.randomUUID().toString())
                .put("message", message)
                .put("model", model)
            if (conversationId.isNullOrBlank()) {
                body.put("timetableSnapshot", timetableSnapshot ?: error("timetable required"))
            } else {
                body.put("conversationId", conversationId)
            }
            val connection = open("POST", "/api/v1/ai/chat", accessToken, body)
            try {
                val status = connection.responseCode
                if (status !in 200..299) throw apiError(connection, status)
                var activeConversationId = conversationId.orEmpty()
                var event = ""
                var truncated = false
                val reply = StringBuilder()
                connection.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        when {
                            line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                            line.startsWith("data:") -> {
                                val data = JSONObject(line.removePrefix("data:").trim())
                                when (event) {
                                    "conversation" -> activeConversationId = data.optString("conversationId", activeConversationId)
                                    "delta" -> reply.append(data.optString("text"))
                                    "done" -> truncated = data.optBoolean("truncated", false)
                                    "error" -> throw WearAiApiException(
                                        statusCode = 502,
                                        errorCode = data.optString("code"),
                                        message = data.optString("message", "Ask Classing failed"),
                                    )
                                }
                                event = ""
                            }
                        }
                    }
                }
                WearAiChatResult(activeConversationId, reply.toString(), truncated)
            } finally {
                connection.disconnect()
            }
        }
    }

    private suspend fun request(method: String, path: String, accessToken: String): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            runCatching {
                ClientIntegrity.ensureTrusted(appContext, baseUrl).getOrThrow()
                val connection = open(method, path, accessToken, null)
                try {
                    val status = connection.responseCode
                    if (status !in 200..299) throw apiError(connection, status)
                    JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                } finally {
                    connection.disconnect()
                }
            }
        }

    private fun open(
        method: String,
        path: String,
        accessToken: String,
        body: JSONObject?,
    ): HttpURLConnection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = 10_000
        readTimeout = 200_000
        doInput = true
        setRequestProperty("Authorization", "Bearer $accessToken")
        setRequestProperty("Accept", "application/json, text/event-stream")
        setRequestProperty("User-Agent", "Classing-WearOS")
        ClientIntegrity.applyHeaders(this, appContext)
        if (body != null) {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            OutputStreamWriter(outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
        }
    }

    private fun apiError(connection: HttpURLConnection, status: Int): WearAiApiException {
        val raw = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val body = runCatching { JSONObject(raw) }.getOrNull()
        return WearAiApiException(
            statusCode = status,
            errorCode = body?.optString("code").orEmpty(),
            message = body?.optString("message").orEmpty().ifBlank { "Ask Classing request failed" },
        )
    }

    private fun <T> JSONArray?.toObjects(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) optJSONObject(index)?.let { add(transform(it)) }
        }
    }

    private companion object {
        const val MIMO_FLASH_MODEL_ID = "mimo-v2.5"
        const val MIMO_PRO_MODEL_ID = "mimo-v2.5-pro"
    }
}
