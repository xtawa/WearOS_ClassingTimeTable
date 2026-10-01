package com.xtawa.classingtime.account

import com.xtawa.classingtime.data.DailyBriefingChannel
import java.io.Closeable
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract tests for the account API client: request bodies and response handling that the
 * app relies on when talking to the account service.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountApiClientContractTest {
    // The email-change request uses PATCH, which the JVM's HttpURLConnection rejects (Android's
    // implementation supports it), so the response contract is exercised on the parser directly.
    @Test
    fun parseEmailChangeResponse_withoutChallenge_reportsUnchangedEmail() {
        val json = JSONObject("""{"account":{"userId":"u1","username":"alice","email":"alice@example.com"}}""")

        val error = runCatching { AccountApiClient.parseEmailChangeResponse(json) }.exceptionOrNull()

        assertTrue(error is AccountApiException)
        assertEquals(AccountApiClient.ERROR_EMAIL_UNCHANGED, (error as AccountApiException).errorCode)
    }

    @Test
    fun parseEmailChangeResponse_withChallenge_parsesRequestId() {
        val json = JSONObject("""{"account":{"userId":"u1"},"emailChange":{"requestId":"req-1","expiresAt":42,"resendAfterSeconds":30}}""")

        val change = AccountApiClient.parseEmailChangeResponse(json)

        assertEquals("req-1", change.requestId)
        assertEquals(42L, change.expiresAt)
        assertEquals(30, change.resendAfterSeconds)
    }

    @Test
    fun saveDailyBriefingSubscription_sendsRegionTimezone() = runBlocking {
        RecordingHttpServer(status = 200, body = """{"briefing":{"enabled":true}}""").use { server ->
            val result = AccountApiClient(baseUrl = server.baseUrl).saveDailyBriefingSubscription(
                accessToken = "token",
                enabled = true,
                channel = DailyBriefingChannel.BOTH,
                time = "07:30",
                timezone = "Europe/Berlin",
            )

            assertTrue(result.isSuccess)
            val json = server.awaitRequest().json()
            assertEquals(true, json.getBoolean("enabled"))
            assertEquals("BOTH", json.getString("channel"))
            assertEquals("07:30", json.getString("time"))
            assertEquals("Europe/Berlin", json.getString("timezone"))
        }
    }

    @Test
    fun saveDailyBriefingSubscription_convertsWholeHourOffset() = runBlocking {
        RecordingHttpServer(status = 200, body = """{"briefing":{"enabled":true}}""").use { server ->
            val result = AccountApiClient(baseUrl = server.baseUrl).saveDailyBriefingSubscription(
                accessToken = "token",
                enabled = true,
                channel = DailyBriefingChannel.EMAIL,
                time = "20:00",
                timezone = "GMT+08:00",
            )

            assertTrue(result.isSuccess)
            assertEquals("Etc/GMT-8", server.awaitRequest().json().getString("timezone"))
        }
    }

    @Test
    fun saveDailyBriefingSubscription_omitsOffsetWithoutTzName() = runBlocking {
        RecordingHttpServer(status = 200, body = """{"briefing":{"enabled":false}}""").use { server ->
            val result = AccountApiClient(baseUrl = server.baseUrl).saveDailyBriefingSubscription(
                accessToken = "token",
                enabled = false,
                channel = DailyBriefingChannel.EMAIL,
                time = "20:00",
                timezone = "+05:30",
            )

            assertTrue(result.isSuccess)
            assertFalse(server.awaitRequest().json().has("timezone"))
        }
    }

    @Test
    fun resolveBriefingTimezone_passesRegionIdsThrough() {
        assertEquals("Asia/Shanghai", AccountApiClient.briefingTimezoneOrNull("Asia/Shanghai"))
        assertEquals("Asia/Kolkata", AccountApiClient.briefingTimezoneOrNull("Asia/Kolkata"))
        assertEquals("America/Argentina/Buenos_Aires", AccountApiClient.briefingTimezoneOrNull(" America/Argentina/Buenos_Aires "))
        assertEquals("Europe/London", AccountApiClient.briefingTimezoneOrNull("Europe/London"))
    }

    @Test
    fun resolveBriefingTimezone_normalisesUtcAliases() {
        for (alias in listOf("UTC", "GMT", "UT", "Z", "Zulu", "Etc/UTC", "Etc/GMT", "GMT0", "Etc/GMT+0", "GMT+00:00", "+00:00", "UTC+0")) {
            assertEquals(alias, "UTC", AccountApiClient.briefingTimezoneOrNull(alias))
        }
    }

    @Test
    fun resolveBriefingTimezone_convertsWholeHourOffsetsToEtcZones() {
        // tz database Etc/GMT zones use the inverted POSIX sign.
        assertEquals("Etc/GMT-8", AccountApiClient.briefingTimezoneOrNull("GMT+8"))
        assertEquals("Etc/GMT-8", AccountApiClient.briefingTimezoneOrNull("GMT+08:00"))
        assertEquals("Etc/GMT-8", AccountApiClient.briefingTimezoneOrNull("UTC+08:00"))
        assertEquals("Etc/GMT-8", AccountApiClient.briefingTimezoneOrNull("+08:00"))
        assertEquals("Etc/GMT+5", AccountApiClient.briefingTimezoneOrNull("-05:00"))
        assertEquals("Etc/GMT-14", AccountApiClient.briefingTimezoneOrNull("+14:00"))
        assertEquals("Etc/GMT+12", AccountApiClient.briefingTimezoneOrNull("-12:00"))
        assertEquals("Etc/GMT-8", AccountApiClient.briefingTimezoneOrNull("Etc/GMT-8"))
        assertEquals("Etc/GMT+8", AccountApiClient.briefingTimezoneOrNull("Etc/GMT+8"))
    }

    @Test
    fun resolveBriefingTimezone_flagsOffsetsWithoutTzName() {
        for (offset in listOf("+05:30", "GMT+05:45", "-03:30", "+15:00", "-13:00")) {
            val resolution = AccountApiClient.resolveBriefingTimezone(offset)
            assertNull(offset, resolution.id)
            assertTrue(offset, resolution.unsupportedOffset)
        }
    }

    @Test
    fun resolveBriefingTimezone_omitsUnknownIds() {
        for (value in listOf("", null, "Mars/Olympus;drop", "A/" + "x".repeat(70), "not a zone")) {
            val resolution = AccountApiClient.resolveBriefingTimezone(value)
            assertNull(value, resolution.id)
            assertFalse(value, resolution.unsupportedOffset)
        }
    }

    @Test
    fun login_sendsFullConsentPayload() = runBlocking {
        val body = """{"session":{"accessToken":"a","refreshToken":"r","accessExpiresAt":1,"refreshExpiresAt":2}}"""
        RecordingHttpServer(status = 200, body = body).use { server ->
            val result = AccountApiClient(baseUrl = server.baseUrl, deviceId = "device-1").login("alice", "pw")

            assertEquals("a", result.getOrThrow().accessToken)
            val request = server.awaitRequest()
            assertTrue(request.headers.contains("X-Classing-Device-ID: device-1"))
            val consent = request.json().getJSONObject("consent")
            assertTrue(consent.getBoolean("privacyPolicy"))
            assertTrue(consent.getBoolean("termsOfService"))
            assertTrue(consent.getBoolean("crossBorderTransfer"))
            assertTrue(consent.getLong("acceptedAt") > 0L)
            assertEquals("android-mobile", consent.getString("client"))
        }
    }

    @Test
    fun rateLimitedRequest_surfacesErrorCodeAndRetryAfter() = runBlocking {
        // Uses the password-reset path so the client-side cooldown recorded by this test does
        // not throttle the login tests that share the same process.
        val body = """{"code":"AUTH_RATE_LIMITED","message":"slow down","requestId":"x"}"""
        RecordingHttpServer(status = 429, body = body, extraHeaders = listOf("Retry-After: 60")).use { server ->
            val result = AccountApiClient(baseUrl = server.baseUrl).requestPasswordReset("alice@example.com")

            val error = result.exceptionOrNull() as AccountApiException
            assertEquals(429, error.statusCode)
            assertEquals("AUTH_RATE_LIMITED", error.errorCode)
            assertEquals(60, error.retryAfterSeconds)
        }
    }
}

private data class RecordedRequest(val headers: String, val body: String) {
    fun json(): JSONObject = JSONObject(body)
}

private class RecordingHttpServer(
    status: Int,
    body: String,
    extraHeaders: List<String> = emptyList(),
) : Closeable {
    private val socket = ServerSocket(0)
    private val executor = Executors.newSingleThreadExecutor()
    private val request = CompletableFuture<RecordedRequest>()
    val baseUrl: String = "http://127.0.0.1:${socket.localPort}"

    init {
        executor.execute {
            runCatching {
                socket.accept().use { client ->
                    val input = client.getInputStream()
                    val headerLines = mutableListOf<String>()
                    val lineBuffer = StringBuilder()
                    while (true) {
                        val byte = input.read()
                        if (byte < 0) break
                        if (byte == '\n'.code) {
                            val line = lineBuffer.toString().trimEnd('\r')
                            lineBuffer.setLength(0)
                            if (line.isEmpty()) break
                            headerLines += line
                        } else {
                            lineBuffer.append(byte.toChar())
                        }
                    }
                    val contentLength = headerLines
                        .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                        ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
                    val bodyBytes = ByteArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val count = input.read(bodyBytes, read, contentLength - read)
                        if (count < 0) break
                        read += count
                    }
                    request.complete(RecordedRequest(headerLines.joinToString("\n"), String(bodyBytes, 0, read, Charsets.UTF_8)))
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    val reason = when (status) {
                        200 -> "OK"
                        202 -> "Accepted"
                        401 -> "Unauthorized"
                        403 -> "Forbidden"
                        429 -> "Too Many Requests"
                        else -> "Error"
                    }
                    client.getOutputStream().bufferedWriter().use { writer ->
                        writer.write("HTTP/1.1 $status $reason\r\n")
                        writer.write("Content-Type: application/json\r\n")
                        extraHeaders.forEach { writer.write("$it\r\n") }
                        writer.write("Content-Length: ${bytes.size}\r\n")
                        writer.write("Connection: close\r\n\r\n")
                        writer.write(body)
                    }
                }
            }.onFailure(request::completeExceptionally)
        }
    }

    fun awaitRequest(): RecordedRequest = request.get(5, TimeUnit.SECONDS)

    override fun close() {
        socket.close()
        executor.shutdownNow()
    }
}
