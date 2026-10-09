package com.xtawa.classingtime.account

import android.content.Context
import android.util.Base64
import com.xtawa.classingtime.security.ClientIntegrity
import java.io.OutputStreamWriter
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
    val transcriptionPoints: Int = 0,
    val suggestionPoints: Int = 0,
    val transcriptionSeconds: Int = 0,
)

data class AiModelOption(val id: String, val name: String, val description: String)
data class AiConversationSummary(val conversationId: String, val title: String, val updatedAt: Long)
data class AiAttachment(val attachmentId: String, val name: String, val mimeType: String, val sizeBytes: Long, val expiresAt: Long)
data class AiMessageSummary(val messageId: String, val role: String, val content: String, val createdAt: Long, val attachments: List<AiAttachment> = emptyList(), val reasoning: String = "")
data class AiChatResult(val conversationId: String, val reply: String, val truncated: Boolean, val costPoints: Int, val courseProposal: JSONObject? = null)
data class AiPhotoImportResult(val timetable: JSONObject, val costPoints: Int, val response: String = "", val reasoning: String = "")

class AiApiClient(
    private val baseUrl: String = AccountApiClient.BASE_URL,
    appContext: Context? = null,
) {
    private val appContext = appContext?.applicationContext
    private val http = OkHttpClient.Builder().callTimeout(200, TimeUnit.SECONDS).build()

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
            body.optJSONObject("helpers")?.optInt("transcriptionPoints") ?: 0,
            body.optJSONObject("helpers")?.optInt("suggestionPoints") ?: 0,
            body.optJSONObject("helpers")?.optInt("transcriptionSeconds") ?: 0,
        )
    }

    suspend fun photoImport(accessToken: String, jpeg: ByteArray, onReply: (String) -> Unit = {}, onReasoning: (String) -> Unit = {}): Result<AiPhotoImportResult> = apiResult {
        require(jpeg.size <= 2_500_000) { "Photo is too large" }
        val payload = JSONObject().put("clientRequestId", java.util.UUID.randomUUID().toString()).put("imageBase64", Base64.encodeToString(jpeg, Base64.NO_WRAP))
        val done = executeEvents(jsonRequest("POST", "/api/v1/ai/photo-import?stream=1", accessToken, payload)) { event, data ->
            when (event) { "delta" -> onReply(data.optString("text")); "reasoning" -> onReasoning(data.optString("text")) }
        }
        AiPhotoImportResult(done.getJSONObject("timetable"), done.optInt("costPoints"), done.optString("response"), done.optString("reasoning"))
    }

    suspend fun models(accessToken: String): Result<Pair<String, List<AiModelOption>>> = request("GET", "/api/v1/ai/models", accessToken).map { body ->
        val items = body.optJSONArray("models").toObjects { item ->
            AiModelOption(item.optString("id"), item.optString("name"), item.optString("description"))
        }.filter { it.id.isNotBlank() && it.name.isNotBlank() }
        body.optString("defaultModel").takeIf { default -> items.any { it.id == default } }.orEmpty().ifBlank {
            items.firstOrNull()?.id.orEmpty()
        } to items
    }

    suspend fun conversations(accessToken: String): Result<List<AiConversationSummary>> = request("GET", "/api/v1/ai/conversations?limit=30", accessToken).map { body ->
        body.optJSONArray("conversations").toObjects { item ->
            AiConversationSummary(item.optString("conversationId"), item.optString("title"), item.optLong("updatedAt"))
        }
    }

    suspend fun messages(accessToken: String, conversationId: String): Result<List<AiMessageSummary>> = request("GET", "/api/v1/ai/conversations/$conversationId/messages", accessToken).map { body ->
        body.optJSONArray("messages").toObjects { item ->
            AiMessageSummary(item.optString("messageId"), item.optString("role"), item.optString("content"), item.optLong("createdAt"), item.optJSONArray("attachments").toObjects(::parseAttachment))
        }
    }

    suspend fun chat(accessToken: String, conversationId: String?, message: String, timetableSnapshot: JSONObject?, model: String, attachmentIds: List<String> = emptyList(), currentTimetable: JSONObject? = timetableSnapshot, onDelta: (String) -> Unit = {}, onReasoning: (String) -> Unit = {}): Result<AiChatResult> = apiResult {
        val body = JSONObject().put("clientRequestId", java.util.UUID.randomUUID().toString()).put("message", message).put("model", model).put("attachmentIds", JSONArray(attachmentIds))
        if (!conversationId.isNullOrBlank()) body.put("conversationId", conversationId)
        if (conversationId.isNullOrBlank()) body.put("timetableSnapshot", timetableSnapshot ?: error("Timetable required"))
        if (currentTimetable != null) body.put("supportsTimetableActions", true).put("currentTimetableSnapshot", currentTimetable)
        var currentId = conversationId.orEmpty()
        val reply = StringBuilder()
        val done = executeEvents(jsonRequest("POST", "/api/v1/ai/chat", accessToken, body)) { event, data ->
            when (event) {
                "conversation" -> currentId = data.optString("conversationId", currentId)
                "delta" -> { val delta = data.optString("text"); reply.append(delta); onDelta(delta) }
                "reasoning" -> onReasoning(data.optString("text"))
            }
        }
        AiChatResult(currentId, done.optString("reply", reply.toString()), done.optBoolean("truncated"), done.optInt("costPoints"), done.optJSONObject("courseProposal"))
    }

    suspend fun preferences(token: String): Result<AiPreferences> = request("GET", "/api/v1/ai/preferences", token).map { AiPreferences.fromJson(it.getJSONObject("preferences")) }
    suspend fun patchPreferences(token: String, patch: JSONObject): Result<AiPreferences> = request("PATCH", "/api/v1/ai/preferences", token, patch).map { AiPreferences.fromJson(it.getJSONObject("preferences")) }
    suspend fun prompts(token: String, snapshot: JSONObject, force: Boolean): Result<AiPromptSuggestions> = request("POST", "/api/v1/ai/prompts", token, JSONObject().put("timetableSnapshot", snapshot).put("force", force)).map { body ->
        val items = body.optJSONArray("prompts")
        AiPromptSuggestions(buildList { if (items != null) for (i in 0 until items.length()) add(items.getString(i)) }, body.optBoolean("cached"), body.optInt("costPoints"))
    }
    suspend fun thumbnail(token: String, attachment: AiAttachment): Result<Bitmap> = apiResult {
        require(attachment.mimeType.startsWith("image/") && attachment.sizeBytes <= 5 * 1024 * 1024)
        val request = jsonRequest("GET", "/api/v1/ai/attachments/${attachment.attachmentId}", token)
        // Body consumption stays cancellable, including large image downloads.
        val bytes = executeBytes(request, 5 * 1024 * 1024)
        thumbnail(bytes)
    }
    fun thumbnail(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 192) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Invalid image")
    }

    suspend fun uploadAttachment(token: String, file: File, name: String, mime: String): Result<AiAttachment> =
        multipart(token, "/api/v1/ai/attachments", file, name, mime).map {
            parseAttachment(it.getJSONObject("attachment"))
        }

    suspend fun transcribe(token: String, file: File): Result<AiTranscription> =
        multipart(token, "/api/v1/ai/transcriptions", file, "voice.mp3", "audio/mpeg").map { AiTranscription(it.getString("text"), it.optInt("costPoints")) }

    suspend fun deleteAttachment(token: String, id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            appContext?.let { ClientIntegrity.ensureTrusted(it, baseUrl).getOrThrow() }
            val builder = Request.Builder().url(baseUrl + "/api/v1/ai/attachments/$id").delete().header("Authorization", "Bearer $token")
            appContext?.let { ClientIntegrity.requestHeaders(it).forEach { (k,v) -> builder.header(k,v) } }
            execute(builder.build()).use { require(it.isSuccessful) { "Could not remove attachment" } }
            Result.success(Unit)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    }

    private fun parseAttachment(item: JSONObject) = AiAttachment(
        item.getString("attachmentId"), item.getString("name"), item.getString("mimeType"), item.getLong("sizeBytes"), item.getLong("expiresAt"),
    )

    private suspend fun multipart(token: String, path: String, file: File, name: String, mime: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            appContext?.let { ClientIntegrity.ensureTrusted(it, baseUrl).getOrThrow() }
            val body = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("file", name, file.asRequestBody(mime.toMediaType())).build()
            val builder = Request.Builder().url(baseUrl + path).post(body).header("Authorization", "Bearer $token")
            appContext?.let { ClientIntegrity.requestHeaders(it).forEach { (k,v) -> builder.header(k,v) } }
            Result.success(executeJSON(builder.build()))
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    }

    // Keep cancellation attached to the call until the entire body has been read.
    private suspend fun executeJSON(request: Request): JSONObject = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val json = response.use {
                        val result = JSONObject(it.body?.string().orEmpty())
                        if (!it.isSuccessful) throw AccountApiException(it.code, result.optString("code"), message = result.optString("message"))
                        result
                    }
                    if (continuation.isActive) continuation.resume(json)
                } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
            }
        })
    }

    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { response.close() }
            }
        })
    }

    private suspend fun <T> apiResult(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try { Result.success(block()) } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    }
    private suspend fun jsonRequest(method: String, path: String, token: String, body: JSONObject? = null): Request {
        appContext?.let { ClientIntegrity.ensureTrusted(it, baseUrl).getOrThrow() }
        val builder = Request.Builder().url(baseUrl + path).header("Authorization", "Bearer $token")
        appContext?.let { ClientIntegrity.requestHeaders(it).forEach { (k, v) -> builder.header(k, v) } }
        return builder.method(method, body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
    }
    private suspend fun request(method: String, path: String, token: String, body: JSONObject? = null): Result<JSONObject> = apiResult { executeJSON(jsonRequest(method, path, token, body)) }

    private suspend fun executeEvents(request: Request, onEvent: (String, JSONObject) -> Unit): JSONObject = readResponse(request) { response ->
        if (!response.isSuccessful) throw responseError(response)
        var event = ""; var done: JSONObject? = null
        response.body?.charStream()?.buffered()?.useLines { lines -> lines.forEach { line ->
            when {
                line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                line.startsWith("data:") -> {
                    val data = JSONObject(line.removePrefix("data:").trim())
                    if (event == "error") throw AccountApiException(502, data.optString("code"), message = data.optString("message", "Ask AI failed"))
                    if (event == "done") done = data else onEvent(event, data)
                }
            }
        } }
        done ?: error("Response interrupted. Please retry.")
    }
    private suspend fun executeBytes(request: Request, limit: Int): ByteArray = readResponse(request) { response ->
        if (!response.isSuccessful) throw responseError(response)
        response.body!!.byteStream().use { input ->
            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= limit); out.write(buffer, 0, n) }; out.toByteArray()
        }
    }
    private fun responseError(response: Response): AccountApiException {
        val data = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
        return AccountApiException(response.code, data?.optString("code").orEmpty(), message = data?.optString("message").orEmpty().ifBlank { "Ask AI request failed" })
    }
    private suspend fun <T> readResponse(request: Request, read: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request); continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try { val result = response.use(read); if (continuation.isActive) continuation.resume(result) }
                catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
            }
        })
    }

    private fun <T> JSONArray?.toObjects(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return buildList { for (index in 0 until length()) optJSONObject(index)?.let { add(transform(it)) } }
    }

}
