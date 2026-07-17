package com.classing.wear.timetable.sync

import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WearOfficialCloudHttpClientTest {
    private lateinit var server: ServerSocket
    private lateinit var baseUrl: String
    private val request = AtomicReference<CapturedRequest>()

    @Before
    fun setUp() {
        server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        thread(name = "wear-cloud-test-server", isDaemon = true) {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                val requestLine = reader.readLine().orEmpty()
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) break
                    val split = line.indexOf(':')
                    if (split > 0) headers[line.substring(0, split).trim().lowercase()] = line.substring(split + 1).trim()
                }
                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                val body = CharArray(contentLength).also { chars ->
                    var offset = 0
                    while (offset < chars.size) {
                        val count = reader.read(chars, offset, chars.size - offset)
                        if (count < 0) break
                        offset += count
                    }
                }.concatToString()
                val method = requestLine.substringBefore(' ')
                request.set(
                    CapturedRequest(
                        method = method,
                        authorization = headers["authorization"].orEmpty(),
                        ifMatch = headers["if-match"].orEmpty(),
                        idempotencyKey = headers["idempotency-key"].orEmpty(),
                        body = body,
                    ),
                )
                val response = if (method == "GET") EMPTY_DOCUMENT else "{\"success\":true}"
                val bytes = response.toByteArray(Charsets.UTF_8)
                socket.getOutputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write("HTTP/1.1 200 OK\r\n")
                    writer.write("Content-Type: application/json\r\n")
                    writer.write("ETag: \"7\"\r\n")
                    writer.write("Content-Length: ${bytes.size}\r\n")
                    writer.write("Connection: close\r\n\r\n")
                    writer.write(response)
                }
            }
        }
        baseUrl = "http://127.0.0.1:${server.localPort}"
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun read_sendsBearerTokenAndParsesEtag() = runBlocking {
        val result = WearOfficialCloudHttpClient(baseUrl).read("wear-access").getOrThrow()

        assertEquals(EMPTY_DOCUMENT, result.payload)
        assertEquals("7", result.versionToken)
        assertEquals("GET", request.get().method)
        assertEquals("Bearer wear-access", request.get().authorization)
    }

    @Test
    fun write_sendsQuotedCasVersionAndBoundedIdempotencyKey() = runBlocking {
        WearOfficialCloudHttpClient(baseUrl)
            .write("wear-access", EMPTY_DOCUMENT, "7")
            .getOrThrow()

        val captured = request.get()
        assertEquals("PUT", captured.method)
        assertEquals("Bearer wear-access", captured.authorization)
        assertEquals("\"7\"", captured.ifMatch)
        assertEquals(EMPTY_DOCUMENT, captured.body)
        assertNotNull(captured.idempotencyKey)
        assertTrue(captured.idempotencyKey.isNotBlank())
        assertTrue(captured.idempotencyKey.length <= 128)
    }

    private data class CapturedRequest(
        val method: String,
        val authorization: String,
        val ifMatch: String,
        val idempotencyKey: String,
        val body: String,
    )

    private companion object {
        const val EMPTY_DOCUMENT =
            "{\"format\":\"classing_cloud_sync_v2\",\"updatedAt\":0,\"records\":{},\"changes\":[],\"devices\":[]}"
    }
}
