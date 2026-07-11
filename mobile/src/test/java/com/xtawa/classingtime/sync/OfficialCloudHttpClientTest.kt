package com.xtawa.classingtime.sync

import com.classing.shared.sync.CloudProvider
import com.xtawa.classingtime.account.AccountApiException
import com.xtawa.classingtime.account.AuthSession
import java.io.Closeable
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfficialCloudHttpClientTest {
    @Test
    fun accountSessionManager_doesNotEraseSessionSavedByAnotherRefresh() = runBlocking {
        val now = System.currentTimeMillis()
        val store = FakeAccountSessionStore()
        val replacement = AuthSession(
            accessToken = "access-new",
            refreshToken = "refresh-new",
            accessExpiresAt = now + 600_000,
            refreshExpiresAt = now + 1_200_000,
        )
        val token = AccountSessionRefreshGate().ensureAccessToken(store) {
            store.saveSession(replacement)
            Result.failure(
                AccountApiException(
                    statusCode = 401,
                    errorCode = "AUTH_REFRESH_REVOKED",
                    message = "already rotated",
                ),
            )
        }

        assertEquals("access-new", token)
        assertEquals("refresh-new", store.loadRefreshToken())
        assertTrue(store.isAccessTokenUsable())
    }

    @Test
    fun accountSessionManager_coalescesConcurrentRefreshes() = runBlocking {
        val now = System.currentTimeMillis()
        val store = FakeAccountSessionStore()
        val refreshCalls = AtomicInteger()
        val gate = AccountSessionRefreshGate()
        val refresh: suspend (String) -> Result<AuthSession> = { token ->
            assertEquals("refresh-old", token)
            refreshCalls.incrementAndGet()
            delay(50)
            Result.success(
                AuthSession(
                    accessToken = "access-new",
                    refreshToken = "refresh-new",
                    accessExpiresAt = now + 600_000,
                    refreshExpiresAt = now + 1_200_000,
                ),
            )
        }
        val tokens = listOf(
            async(Dispatchers.Default) { gate.ensureAccessToken(store, refresh) },
            async(Dispatchers.Default) { gate.ensureAccessToken(store, refresh) },
        ).awaitAll()

        assertEquals(listOf("access-new", "access-new"), tokens)
        assertEquals(1, refreshCalls.get())
        assertEquals("refresh-new", store.loadRefreshToken())
    }

    @Test
    fun testConnection_sendsAccountBearerToken() = runBlocking {
        OneShotHttpServer(status = 200, body = "{\"status\":\"ok\"}").use { server ->
            val result = OfficialCloudHttpClient().testConnection(config(server.baseUrl))

            assertTrue(result.isSuccess)
            assertTrue(server.awaitRequest().contains("Authorization: Bearer account-token", ignoreCase = true))
        }
    }

    @Test
    fun testConnection_distinguishesUnauthorizedFromForbidden() = runBlocking {
        OneShotHttpServer(status = 401, body = "{\"code\":\"AUTH_ACCESS_EXPIRED\"}").use { server ->
            val result = OfficialCloudHttpClient().testConnection(config(server.baseUrl))
            assertTrue(result.exceptionOrNull() is CloudAuthExpiredException)
        }
        OneShotHttpServer(status = 403, body = "{\"code\":\"MEMBERSHIP_REQUIRED\"}").use { server ->
            val result = OfficialCloudHttpClient().testConnection(config(server.baseUrl))
            assertTrue(result.exceptionOrNull() is CloudPermissionDeniedException)
        }
    }

    private fun config(baseUrl: String) = CloudRuntimeConfig(
        provider = CloudProvider.OFFICIAL,
        enabled = true,
        serverUrl = baseUrl,
        remotePath = "/api/v1/cloud/official/document",
        username = "",
        password = "",
        driveFileName = "",
        driveAccessToken = "",
        driveAccessTokenExpireAt = 0L,
        driveAccessTokenRefreshAfterAt = 0L,
        accountAccessToken = "account-token",
        officialMemberAuthorized = false,
    )
}

private class FakeAccountSessionStore : AccountSessionStore {
    private var accessToken = "expired-access"
    private var refreshToken = "refresh-old"
    private var accessUsable = false
    private var refreshUsable = true

    override fun loadAccessToken(): String = accessToken

    override fun loadRefreshToken(): String = refreshToken

    override fun isAccessTokenUsable(): Boolean = accessUsable

    override fun isRefreshTokenUsable(): Boolean = refreshUsable

    override fun saveSession(session: AuthSession) {
        accessToken = session.accessToken
        refreshToken = session.refreshToken
        accessUsable = true
        refreshUsable = true
    }

    override fun clear() {
        accessToken = ""
        refreshToken = ""
        accessUsable = false
        refreshUsable = false
    }
}

private class OneShotHttpServer(
    status: Int,
    body: String,
) : Closeable {
    private val socket = ServerSocket(0)
    private val executor = Executors.newSingleThreadExecutor()
    private val request = java.util.concurrent.CompletableFuture<String>()
    val baseUrl: String = "http://127.0.0.1:${socket.localPort}"

    init {
        executor.execute {
            runCatching {
                socket.accept().use { client ->
                    val lines = mutableListOf<String>()
                    val reader = client.getInputStream().bufferedReader()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        lines += line
                    }
                    request.complete(lines.joinToString("\n"))
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    val reason = when (status) {
                        200 -> "OK"
                        401 -> "Unauthorized"
                        403 -> "Forbidden"
                        else -> "Error"
                    }
                    client.getOutputStream().bufferedWriter().use { writer ->
                        writer.write("HTTP/1.1 $status $reason\r\n")
                        writer.write("Content-Type: application/json\r\n")
                        writer.write("Content-Length: ${bytes.size}\r\n")
                        writer.write("Connection: close\r\n\r\n")
                        writer.write(body)
                    }
                }
            }.onFailure(request::completeExceptionally)
        }
    }

    fun awaitRequest(): String = request.get(5, TimeUnit.SECONDS)

    override fun close() {
        socket.close()
        executor.shutdownNow()
    }
}
