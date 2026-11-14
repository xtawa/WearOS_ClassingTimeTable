package com.classing.client.byok

import com.classing.shared.ai.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.withTimeout

data class ByokImage(val name: String, val jpeg: ByteArray)

class ByokProvider(private val transport: (suspend (ByokProfile, String, ByteArray, String) -> ByteArray)? = null) {
    suspend fun chat(profile: ByokProfile, messages: JSONArray, snapshot: JSONObject, images: List<ByokImage> = emptyList(), readOnly: Boolean = false): String {
        require(profile.role in setOf(ByokRole.CHAT,ByokRole.VISION))
        require(images.size<=5 && images.all { it.jpeg.size<=2_500_000 })
        val context = JSONObject().put("role","system").put("content",
            "You are Classing, a timetable and exam assistant. Answer using the current snapshot. Never invent dates or execute actions. " +
            (if(readOnly) "This client is read-only; provide answers only." else ByokActions.policy) + "\nCurrent snapshot:\n" + snapshot.toString())
        val sent=JSONArray().put(context)
        val first=(messages.length()-40).coerceAtLeast(0)
        for(i in first until messages.length()) {
            val m=messages.getJSONObject(i);require(m.getString("role") in setOf("user","assistant"))
            sent.put(JSONObject().put("role",m.getString("role")).put("content",m.getString("content")))
        }
        require(sent.length()>1)
        if(images.isNotEmpty()) {
            val last=sent.getJSONObject(sent.length()-1);require(last.getString("role")=="user")
            last.put("content",JSONArray().put(JSONObject().put("type","text").put("text",last.getString("content"))).also { content ->
                images.forEach { content.put(JSONObject().put("type","image_url").put("image_url",JSONObject()
                    .put("url","data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(it.jpeg)))) }
            })
        }
        val body=JSONObject().put("model",profile.model).put("messages",sent).put("stream",false)
        val response=request(profile,"chat/completions",body.toString().toByteArray(),"application/json")
        return providerJson(profile) {JSONObject(String(response,Charsets.UTF_8)).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")}
            .also {require(it.isNotBlank() && it.length<=512*1024) { "Provider returned an empty or oversized answer" }}
    }
    suspend fun transcribe(profile: ByokProfile, audio: ByteArray): String {
        require(profile.role==ByokRole.TRANSCRIPTION && audio.isNotEmpty() && audio.size<=12*1024*1024)
        val boundary="classing-"+UUID.randomUUID();val out=ByteArrayOutputStream()
        fun text(s: String) { out.write(s.toByteArray()) }
        text("--$boundary\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\n${profile.model}\r\n")
        text("--$boundary\r\nContent-Disposition: form-data; name=\"response_format\"\r\n\r\njson\r\n")
        text("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"voice.mp3\"\r\nContent-Type: audio/mpeg\r\n\r\n")
        out.write(audio);text("\r\n--$boundary--\r\n")
        val response=request(profile,"audio/transcriptions",out.toByteArray(),"multipart/form-data; boundary=$boundary")
        return providerJson(profile) {JSONObject(String(response,Charsets.UTF_8)).getString("text")}
    }
    suspend fun speech(profile: ByokProfile, text: String): ByteArray {
        require(profile.role==ByokRole.SPEECH && text.isNotBlank() && text.length<=4096)
        val body=JSONObject().put("model",profile.model).put("voice",profile.voice).put("input",text).put("response_format","mp3")
        return request(profile,"audio/speech",body.toString().toByteArray(),"application/json")
    }
    private fun providerJson(profile: ByokProfile, parse: () -> String): String = try {
        parse().replace(profile.apiKey,"[REDACTED]")
    } catch(e: org.json.JSONException) {
        // JSON exceptions may include the raw body. Gateways occasionally echo credentials.
        throw IllegalStateException("Provider returned an invalid response")
    }
    private suspend fun request(profile: ByokProfile, path: String, body: ByteArray, type: String): ByteArray {
        transport?.let { return it(profile,path,body,type) }
        return withTimeout(120_000) { cancellableIo { close ->
            val connection=URL(profile.baseUrl.trimEnd('/')+"/"+path).openConnection() as HttpURLConnection
            close { connection.disconnect() }
            connection.instanceFollowRedirects=false;connection.connectTimeout=15_000;connection.readTimeout=90_000
            connection.requestMethod="POST";connection.doOutput=true
            connection.setRequestProperty("Authorization","Bearer ${profile.apiKey}")
            connection.setRequestProperty("Content-Type",type);connection.setRequestProperty("Accept","application/json, audio/mpeg")
            connection.outputStream.use { it.write(body) }
            // Never echo provider errors: some gateways include request headers / API keys.
            require(connection.responseCode in 200..299) { "BYOK provider request failed (HTTP ${connection.responseCode})" }
            connection.inputStream.use { input -> val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true) { val n=input.read(buffer);if(n<0) break;require(out.size()+n<=4*1024*1024) { "Provider response is too large" };out.write(buffer,0,n) };out.toByteArray() }
        } }
    }
}
