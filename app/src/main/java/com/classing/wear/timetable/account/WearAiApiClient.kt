package com.classing.wear.timetable.account

import android.content.Context
import com.classing.client.byok.*
import com.classing.shared.ai.*
import kotlinx.coroutines.CancellationException
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
data class WearAiChatResult(val conversationId: String, val reply: String, val truncated: Boolean, val costPoints: Int, val byok: Boolean = false)

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

    suspend fun checkByokPro(accessToken: String): Result<Boolean> = request("GET", "/api/v1/ai/usage/me", accessToken).map {
        it.getJSONObject("usage").optBoolean("isMember")
    }
    private suspend fun privateConfig(token: String): ByokConfig? {
        val session=WearDirectAccountSessionManager.ensureSession(appContext) ?: return null
        val config=ByokStore.load(appContext,session.userId)
        if(!config.enabled) return null
        check(checkByokPro(session.accessToken).getOrThrow()) { "BYOK requires Pro" }
        return config
    }
    private suspend fun <T> safe(block: suspend () -> T): Result<T> = try { Result.success(block()) }
        catch(e: CancellationException) {throw e} catch(e: Exception) {Result.failure(e)}

    suspend fun models(accessToken: String): Result<Pair<String,List<WearAiModel>>> = safe {
        privateConfig(accessToken)?.let { c -> c.chatId to c.profiles.filter {it.role==ByokRole.CHAT}.map {WearAiModel(it.id,it.name,"BYOK · ${it.model}")} }
            ?: request("GET", "/api/v1/ai/models", accessToken).getOrThrow().let { body ->
                val items=body.optJSONArray("models").toObjects {WearAiModel(it.optString("id"),it.optString("name"),it.optString("description"))}.filter {it.id.isNotBlank()&&it.name.isNotBlank()}
                body.optString("defaultModel").takeIf {id->items.any{it.id==id}}.orEmpty().ifBlank {items.firstOrNull()?.id.orEmpty()} to items
            }
    }
    suspend fun conversations(accessToken: String): Result<List<WearAiConversation>> = safe {
        privateConfig(accessToken)?.let { c ->
            val local=ByokStore.history(appContext,c.ownerId)
            if(local.length()==0) emptyList() else listOf(WearAiConversation("byok-local",local.optJSONObject(0)?.optString("content")?.take(40).orEmpty(),0))
        } ?: request("GET", "/api/v1/ai/conversations?limit=8", accessToken).getOrThrow().optJSONArray("conversations").toObjects {
            WearAiConversation(it.optString("conversationId"),it.optString("title"),it.optLong("updatedAt"))
        }
    }
    suspend fun messages(accessToken: String, conversationId: String): Result<List<WearAiMessage>> = safe {
        privateConfig(accessToken)?.let { c ->
            require(conversationId=="byok-local")
            ByokStore.history(appContext,c.ownerId).toObjects {WearAiMessage(it.optString("id"),it.getString("role").uppercase(),it.getString("content"))}
        } ?: request("GET", "/api/v1/ai/conversations/$conversationId/messages", accessToken).getOrThrow().optJSONArray("messages").toObjects {
            WearAiMessage(it.optString("messageId"),it.optString("role"),it.optString("content"))
        }
    }

    suspend fun chat(
        accessToken: String,
        conversationId: String?,
        message: String,
        timetableSnapshot: JSONObject?,
        model: String,
    ): Result<WearAiChatResult> = withContext(Dispatchers.IO) {
        safe {
            val config=privateConfig(accessToken)
            if(config!=null) {
                require(model==config.chatId) { "Choose the current BYOK model" }
                val history=if(conversationId=="byok-local") ByokStore.history(appContext,config.ownerId) else JSONArray()
                history.put(JSONObject().put("id",UUID.randomUUID().toString()).put("role","user").put("content",message))
                val reply=ByokProvider().chat(config.profile(config.chatId),history,timetableSnapshot ?: JSONObject(),readOnly=true)
                history.put(JSONObject().put("id",UUID.randomUUID().toString()).put("role","assistant").put("content",reply))
                val saved=runCatching {ByokStore.saveHistory(appContext,config.ownerId,history)}.isSuccess
                return@safe WearAiChatResult("byok-local",if(saved) reply else reply+"\n\n[Could not save this answer locally]",false,0,true)
            }
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
            timetableSnapshot?.let { body.put("currentTimetableSnapshot", it) }
            val connection = open("POST", "/api/v1/ai/chat", accessToken, body)
            try {
                val status = connection.responseCode
                if (status !in 200..299) throw apiError(connection, status)
                var activeConversationId = conversationId.orEmpty()
                var event = ""
                var truncated = false
				var costPoints = 0
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
                                    "done" -> {
                                        truncated = data.optBoolean("truncated", false)
                                        costPoints = data.optInt("costPoints", 0)
                                    }
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
                WearAiChatResult(activeConversationId, reply.toString(), truncated, costPoints)
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

}
