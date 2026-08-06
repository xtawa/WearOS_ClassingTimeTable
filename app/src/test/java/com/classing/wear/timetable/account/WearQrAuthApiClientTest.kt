package com.classing.wear.timetable.account

import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Mirrors the embedded HTTP server pattern from [com.classing.wear.timetable.sync.WearOfficialCloudHttpClientTest]:
 * a real [ServerSocket] bound to 127.0.0.1 accepts a single connection, parses the raw HTTP
 * request, and writes back a configurable response. [WearQrAuthApiClient] is constructed with
 * context = null so ClientIntegrity (which needs an Android Context) is skipped, letting the
 * login flow run as a pure HttpURLConnection exchange against this local server.
 */
class WearQrAuthApiClientTest {
    private lateinit var server: ServerSocket
    private lateinit var baseUrl: String
    private val responseStatus = AtomicReference(200)
    private val responseBody = AtomicReference(SUCCESS_JSON)
    private val capturedBody = AtomicReference<String>()

    @Before
    fun setUp() {
        server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        thread(name = "wear-qr-auth-test-server", isDaemon = true) {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                reader.readLine() // request line
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
                capturedBody.set(body)
                val status = responseStatus.get()
                val payload = responseBody.get()
                val bytes = payload.toByteArray(Charsets.UTF_8)
                val reason = when (status) {
                    200 -> "OK"
                    401 -> "Unauthorized"
                    else -> "Error"
                }
                socket.getOutputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write("HTTP/1.1 $status $reason\r\n")
                    writer.write("Content-Type: application/json\r\n")
                    writer.write("Content-Length: ${bytes.size}\r\n")
                    writer.write("Connection: close\r\n\r\n")
                    writer.write(payload)
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
    fun login_success_parses_session() = runTest {
        responseStatus.set(200)
        responseBody.set(SUCCESS_JSON)

        val session = WearQrAuthApiClient(baseUrl, context = null)
            .login("user", "pass1234")
            .getOrThrow()

        assertEquals("at", session.accessToken)
        assertEquals("rt", session.refreshToken)
        assertEquals(100L, session.accessExpiresAt)
        assertEquals(200L, session.refreshExpiresAt)
        assertEquals("u1", session.userId)
        assertEquals("alice", session.username)
        assertTrue(session.isMember)
        assertEquals("PRO", session.membershipTier)
    }

    @Test
    fun login_invalid_credentials_throws() = runTest {
        responseStatus.set(401)
        responseBody.set("{\"code\":\"AUTH_INVALID_CREDENTIALS\",\"message\":\"bad\"}")

        val result = WearQrAuthApiClient(baseUrl, context = null).login("user", "pass1234")

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("expected WearQrAuthException but was ${error?.javaClass}", error is WearQrAuthException)
        val ex = error as WearQrAuthException
        assertEquals(401, ex.statusCode)
        assertEquals("AUTH_INVALID_CREDENTIALS", ex.errorCode)
        assertEquals("bad", ex.message)
    }

    private companion object {
        const val SUCCESS_JSON =
            "{\"session\":{\"accessToken\":\"at\",\"refreshToken\":\"rt\"," +
                "\"accessExpiresAt\":100,\"refreshExpiresAt\":200}," +
                "\"account\":{\"userId\":\"u1\",\"username\":\"alice\"}," +
                "\"membership\":{\"isMember\":true,\"tier\":\"PRO\"}}"
    }
}
