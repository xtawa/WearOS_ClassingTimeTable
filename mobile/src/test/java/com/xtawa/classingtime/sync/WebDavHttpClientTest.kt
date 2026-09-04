package com.xtawa.classingtime.sync

import com.classing.shared.sync.CloudProvider
import java.io.Closeable
import java.net.ServerSocket
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebDavHttpClientTest {
    @Test
    fun readJson_retriesInsideCollectionWhenGetReturns405() = runBlocking {
        ScriptedHttpServer(
            listOf(
                HttpResponseSpec(status = 405),
                HttpResponseSpec(status = 200, body = "{}", headers = mapOf("ETag" to "\"v1\"")),
            ),
        ).use { server ->
            val result = WebDavHttpClient().readJson(config(server.baseUrl, "/classing")).getOrThrow()

            assertEquals("{}", result.payload)
            assertEquals("\"v1\"", result.versionToken)
            assertEquals(
                listOf(
                    "GET /classing HTTP/1.1",
                    "GET /classing/classing_sync.json HTTP/1.1",
                ),
                server.awaitRequests(),
            )
        }
    }

    @Test
    fun writeJson_createsMissingParentCollectionBeforeFirstPut() = runBlocking {
        ScriptedHttpServer(
            listOf(
                HttpResponseSpec(status = 404),
                HttpResponseSpec(status = 201),
                HttpResponseSpec(status = 201),
            ),
        ).use { server ->
            val result = WebDavHttpClient().writeJson(
                config = config("${server.baseUrl}/dav", "/classing/classing_sync.json"),
                payload = "{}",
                expectedVersion = null,
            )

            assertTrue(result.isSuccess)
            assertEquals(
                listOf(
                    "PROPFIND /dav/classing/ HTTP/1.1",
                    "MKCOL /dav/classing/ HTTP/1.1",
                    "PUT /dav/classing/classing_sync.json HTTP/1.1",
                ),
                server.awaitRequests(),
            )
        }
    }

    @Test
    fun readJson_usesPropfindEtagWhenGetHeaderIsMissing() = runBlocking {
        val multistatus = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response><d:propstat><d:prop><d:getetag>&quot;v2&quot;</d:getetag></d:prop></d:propstat></d:response>
            </d:multistatus>
        """.trimIndent()
        ScriptedHttpServer(
            listOf(
                HttpResponseSpec(status = 200, body = "{}"),
                HttpResponseSpec(status = 207, body = multistatus),
            ),
        ).use { server ->
            val result = WebDavHttpClient()
                .readJson(config(server.baseUrl, "/classing_sync.json"))
                .getOrThrow()

            assertEquals("\"v2\"", result.versionToken)
            assertEquals(
                listOf(
                    "GET /classing_sync.json HTTP/1.1",
                    "PROPFIND /classing_sync.json HTTP/1.1",
                ),
                server.awaitRequests(),
            )
        }
    }

    @Test
    fun readJson_treats404AsEmptyRemoteDocument() = runBlocking {
        ScriptedHttpServer(listOf(HttpResponseSpec(status = 404))).use { server ->
            val result = WebDavHttpClient()
                .readJson(config(server.baseUrl, "/classing/classing_sync.json"))
                .getOrThrow()

            assertEquals(null, result.payload)
            assertEquals(null, result.versionToken)
            assertEquals(
                listOf("GET /classing/classing_sync.json HTTP/1.1"),
                server.awaitRequests(),
            )
        }
    }

    private fun config(serverUrl: String, remotePath: String) = CloudRuntimeConfig(
        provider = CloudProvider.WEBDAV,
        enabled = true,
        serverUrl = serverUrl,
        remotePath = remotePath,
        username = "user",
        password = "password",
        driveFileName = "",
        driveAccessToken = "",
        driveAccessTokenExpireAt = 0L,
        driveAccessTokenRefreshAfterAt = 0L,
        accountAccessToken = "",
        officialMemberAuthorized = false,
    )
}

private data class HttpResponseSpec(
    val status: Int,
    val body: String = "",
    val headers: Map<String, String> = emptyMap(),
)

private class ScriptedHttpServer(
    private val responses: List<HttpResponseSpec>,
) : Closeable {
    private val socket = ServerSocket(0)
    private val executor = Executors.newSingleThreadExecutor()
    private val requestLines = Collections.synchronizedList(mutableListOf<String>())
    private val latch = CountDownLatch(responses.size)
    private val failure = AtomicReference<Throwable?>(null)
    val baseUrl: String = "http://127.0.0.1:${socket.localPort}"

    init {
        executor.execute {
            try {
                responses.forEach { spec ->
                    socket.accept().use { client ->
                        val reader = client.getInputStream().bufferedReader(Charsets.UTF_8)
                        val lines = mutableListOf<String>()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            lines += line
                        }
                        requestLines += lines.firstOrNull().orEmpty()
                        val contentLength = lines.firstOrNull {
                            it.startsWith("Content-Length:", ignoreCase = true)
                        }?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
                        var remaining = contentLength
                        val buffer = CharArray(512)
                        while (remaining > 0) {
                            val count = reader.read(buffer, 0, minOf(buffer.size, remaining))
                            if (count <= 0) break
                            remaining -= count
                        }

                        val bytes = spec.body.toByteArray(Charsets.UTF_8)
                        val reason = when (spec.status) {
                            200 -> "OK"
                            201 -> "Created"
                            204 -> "No Content"
                            207 -> "Multi-Status"
                            401 -> "Unauthorized"
                            403 -> "Forbidden"
                            404 -> "Not Found"
                            405 -> "Method Not Allowed"
                            409 -> "Conflict"
                            412 -> "Precondition Failed"
                            else -> "Response"
                        }
                        val output = client.getOutputStream()
                        val headerText = buildString {
                            append("HTTP/1.1 ${spec.status} $reason\r\n")
                            spec.headers.forEach { (name, value) -> append("$name: $value\r\n") }
                            append("Content-Length: ${bytes.size}\r\n")
                            append("Connection: close\r\n\r\n")
                        }
                        output.write(headerText.toByteArray(Charsets.ISO_8859_1))
                        output.write(bytes)
                        output.flush()
                    }
                    latch.countDown()
                }
            } catch (error: Throwable) {
                failure.set(error)
                while (latch.count > 0) latch.countDown()
            }
        }
    }

    fun awaitRequests(): List<String> {
        assertTrue("Timed out waiting for WebDAV requests", latch.await(5, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("Test HTTP server failed", it) }
        return synchronized(requestLines) { requestLines.toList() }
    }

    override fun close() {
        socket.close()
        executor.shutdownNow()
    }
}
