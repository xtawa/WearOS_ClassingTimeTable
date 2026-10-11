package com.classing.client.lms

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

data class LmsCourse(val id: String, val name: String)
data class LmsMeeting(val id: String, val courseId: String, val title: String, val start: Instant, val end: Instant, val room: String)
data class LmsAssignment(val id: String, val courseId: String, val title: String, val due: Instant?)
data class CanvasSnapshot(val courses: List<LmsCourse>, val meetings: List<LmsMeeting>, val assignments: List<LmsAssignment>, val warnings: List<String>)
data class CanvasPage(val body: JSONArray, val link: String = "")

/** Phone-only read-only PoC. The token is held for this request, never persisted. */
class CanvasReadonlyClient(private val transport: (HttpUrl, String) -> CanvasPage = ::canvasGet) {
    suspend fun load(baseUrl: String, token: String, from: LocalDate, through: LocalDate): CanvasSnapshot = withContext(Dispatchers.IO) {
        val base = baseUrl.trim().toHttpUrl()
        require(base.scheme == "https" && base.username.isEmpty() && base.password.isEmpty() && base.encodedPath == "/" && base.query == null && base.fragment == null) { "Enter the HTTPS Canvas school origin" }
        require(token.isNotBlank() && token.length <= 8192 && '\n' !in token && '\r' !in token)
        require(through >= from && through.toEpochDay() - from.toEpochDay() <= 366)
        fun pages(first: HttpUrl): List<JSONObject> {
            val result = mutableListOf<JSONObject>(); var next: HttpUrl? = first; val visited = mutableSetOf<String>()
            while (next != null) {
                val url = next
                require(url.scheme == base.scheme && url.host == base.host && url.port == base.port && url.encodedPath.startsWith("/api/v1/") && url.username.isEmpty() && url.password.isEmpty()) { "Unsafe Canvas pagination destination" }
                require(visited.size < 50 && visited.add(url.toString())) { "Canvas pagination limit or loop" }
                val page = transport(url, token)
                for (i in 0 until page.body.length()) result += page.body.getJSONObject(i)
                require(result.size <= 5000) { "Canvas import exceeds 5000 records" }
                val link = page.link.split(',').firstOrNull { Regex("rel\\s*=\\s*\"?next\"?").containsMatchIn(it) }
                next = link?.substringAfter('<')?.substringBefore('>')?.let { url.resolve(it) ?: error("Invalid Canvas next link") }
            }
            return result
        }
        val courses = pages(base.newBuilder().encodedPath("/api/v1/courses").addQueryParameter("enrollment_state", "active").addQueryParameter("per_page", "100").build())
            .map { LmsCourse(it.get("id").toString(), it.optString("name")) }.distinctBy { it.id }
        val meetings = mutableListOf<LmsMeeting>(); val assignments = mutableListOf<LmsAssignment>(); val warnings = mutableListOf<String>()
        courses.chunked(10).forEach { chunk ->
            fun endpoint(type: String): HttpUrl = base.newBuilder().encodedPath("/api/v1/calendar_events").addQueryParameter("type", type)
                .addQueryParameter("start_date", from.toString()).addQueryParameter("end_date", through.toString()).addQueryParameter("per_page", "100")
                .apply { chunk.forEach { addQueryParameter("context_codes[]", "course_${it.id}") } }.build()
            pages(endpoint("event")).forEach { event ->
                if (event.optString("workflow_state") == "deleted" || event.optBoolean("hidden")) return@forEach
                val id = event.get("id").toString()
                val start = runCatching { Instant.parse(event.getString("start_at")) }.getOrNull()
                val end = runCatching { Instant.parse(event.getString("end_at")) }.getOrNull()
                if (event.optBoolean("all_day") || start == null || end == null || end <= start) { warnings += "Calendar event $id needs manual scheduling"; return@forEach }
                meetings += LmsMeeting(id, event.optString("context_code").removePrefix("course_"), event.optString("title"), start, end, event.optString("location_name"))
            }
            pages(endpoint("assignment")).forEach { item ->
                if (item.optString("workflow_state") != "deleted") assignments += LmsAssignment(item.get("id").toString(), item.optString("context_code").removePrefix("course_"), item.optString("title"), runCatching { Instant.parse(item.getString("start_at")) }.getOrNull())
            }
        }
        CanvasSnapshot(courses, meetings.distinctBy { it.id }, assignments.distinctBy { it.id }, warnings)
    }
}
private val canvasHttp by lazy { OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(30, TimeUnit.SECONDS).build() }
private fun canvasGet(url: HttpUrl, token: String): CanvasPage {
    canvasHttp.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").header("Accept", "application/json").get().build()).execute().use { response ->
        if (!response.isSuccessful) throw IOException(when(response.code) { 401,403 -> "Canvas permission denied. Use ICS or manual import."; 429 -> "Canvas rate limited. Retry later."; else -> "Canvas request failed (${response.code})" })
        val body = response.body ?: throw IOException("Canvas response missing")
        val source = body.source()
        val exceedsLimit = source.request(1_048_577)
        require(!exceedsLimit && source.buffer.size <= 1_048_576) { "Canvas page is too large" }
        return CanvasPage(JSONArray(source.readUtf8()), response.header("Link").orEmpty())
    }
}
