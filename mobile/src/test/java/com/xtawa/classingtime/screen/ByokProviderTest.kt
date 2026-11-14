package com.xtawa.classingtime.screen

import com.classing.client.byok.*
import com.classing.shared.ai.*
import kotlinx.coroutines.*
import org.json.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class ByokProviderTest {
    private fun profile(role: ByokRole=ByokRole.CHAT)=ByokProfile("test","Local","https://provider.example/v1","custom-model","test-only-private-key",role)
    @Test fun chatUsesCompatibleContractAndFreshSnapshotWithoutKeyInBody()= runBlocking {
        var calls=0
        val provider=ByokProvider { p,path,body,type ->
            calls++;assertEquals("provider.example",java.net.URI(p.baseUrl).host);assertEquals("chat/completions",path);assertEquals("application/json",type)
            val raw=String(body);assertFalse(raw.contains(p.apiKey));val json=JSONObject(raw)
            assertEquals("custom-model",json.getString("model"));assertFalse(json.getBoolean("stream"))
            val messages=json.getJSONArray("messages");assertTrue(messages.getJSONObject(0).getString("content").contains("Exam test"))
            assertEquals(41,messages.length());assertEquals("user",messages.getJSONObject(40).getString("role"))
            "{\"choices\":[{\"message\":{\"content\":\"answer\"}}]}".toByteArray()
        }
        val history=JSONArray();repeat(41) {history.put(JSONObject().put("role",if(it%2==0) "user" else "assistant").put("content","Q$it"))}
        assertEquals("answer",provider.chat(profile(),history,JSONObject().put("exams",JSONArray().put("Exam test"))))
        assertEquals(1,calls)
    }
    @Test fun malformedProviderResponsesCannotExposeCredentials()= runBlocking {
        val p=profile()
        val messages=JSONArray().put(JSONObject().put("role","user").put("content","Question"))
        for(response in listOf("unexpected body "+p.apiKey,"{\"choices\":\""+p.apiKey+"\"}")) {
            val provider=ByokProvider {_,_,_,_->response.toByteArray()}
            val failure=runCatching {provider.chat(p,messages,JSONObject())}.exceptionOrNull()
            assertNotNull(failure);assertFalse(failure!!.message.orEmpty().contains(p.apiKey))
        }
        val provider=ByokProvider {_,_,_,_->JSONObject().put("choices",JSONArray().put(JSONObject().put("message",JSONObject().put("content",p.apiKey)))).toString().toByteArray()}
        assertEquals("[REDACTED]",provider.chat(p,messages,JSONObject()))
    }
    @Test fun imagesTranscriptionAndSpeechUseSeparateModels()= runBlocking {
        val paths=mutableListOf<String>()
        val provider=ByokProvider { p,path,body,type ->
            paths+=path;assertFalse(String(body).contains(p.apiKey))
            when(path) {
                "chat/completions" -> {val content=JSONObject(String(body)).getJSONArray("messages").getJSONObject(1).getJSONArray("content")
                    assertEquals("image_url",content.getJSONObject(1).getString("type"));assertTrue(content.getJSONObject(1).getJSONObject("image_url").getString("url").startsWith("data:image/jpeg;base64,"))
                    "{\"choices\":[{\"message\":{\"content\":\"photo answer\"}}]}".toByteArray()}
                "audio/transcriptions" -> {assertTrue(type.startsWith("multipart/form-data; boundary="));assertTrue(String(body).contains("filename=\"voice.mp3\""));"{\"text\":\"draft only\"}".toByteArray()}
                "audio/speech" -> {assertEquals("mp3",JSONObject(String(body)).getString("response_format"));byteArrayOf(1,2,3)}
                else -> error("Unexpected endpoint")
            }
        }
        val messages=JSONArray().put(JSONObject().put("role","user").put("content","Read image"))
        provider.chat(profile(ByokRole.VISION),messages,JSONObject(),listOf(ByokImage("photo",byteArrayOf(1,2))))
        assertEquals("draft only",provider.transcribe(profile(ByokRole.TRANSCRIPTION),byteArrayOf(4,5)))
        assertArrayEquals(byteArrayOf(1,2,3),provider.speech(profile(ByokRole.SPEECH),"reply"))
        assertEquals(listOf("chat/completions","audio/transcriptions","audio/speech"),paths)
    }
    @Test fun modificationProposalsAreValidatedBeforeAnyCourseWrite() {
        val action="""```classing-course-actions
            {"version":1,"actions":[{"operation":"create","lesson":{"title":"Math","dayOfWeek":1,"startTime":"09:00","endTime":"10:00"}}]}
            ```""".trimIndent()
        val parsed=ByokActions.parse(action);assertNull(parsed.second);assertEquals(1,applyAskAiCourseProposal(emptyList(),parsed.first!!).size)
        assertThrows(IllegalArgumentException::class.java) {ByokActions.parse(action+"\n"+action)}
        assertThrows(IllegalArgumentException::class.java) {ByokActions.parse(action.removeSuffix("```"))}
        assertEquals("answer",ByokActions.display("answer\n$action"))
    }
    @Test fun privateSerializationRoundTripsAndWearPayloadOmitsUnselectedKeys() {
        val selected=profile();val other=profile(ByokRole.SPEECH).copy(id="speech",apiKey="other-test-key")
        val config=ByokConfig("owner",listOf(selected,other),true,chatId=selected.id,speechId=other.id,wearId=selected.id)
        assertEquals(config,ByokJson.decode(ByokJson.encode(config)))
        val wear=ByokJson.encode(config.forWear()).toString();assertFalse(wear.contains(other.apiKey));assertTrue(wear.contains(selected.apiKey))
    }
    @Test fun cancellationClosesResourcesAndInterruptsConfirmationWait()= runBlocking {
        val started=CountDownLatch(1);val closed=CountDownLatch(1);val ended=CountDownLatch(1)
        val job=launch(Dispatchers.Default) {cancellableIo {close -> close {closed.countDown()};started.countDown()
            try {runBlocking {awaitCancellation()}} finally {ended.countDown()}}
        }
        assertTrue(started.await(5,TimeUnit.SECONDS));job.cancelAndJoin()
        assertTrue(closed.await(5,TimeUnit.SECONDS));assertTrue(ended.await(5,TimeUnit.SECONDS))
    }
}
