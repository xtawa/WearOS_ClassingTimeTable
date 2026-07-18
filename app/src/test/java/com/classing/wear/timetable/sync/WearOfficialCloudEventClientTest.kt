package com.classing.wear.timetable.sync

import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class WearOfficialCloudEventClientTest {
    private lateinit var server: ServerSocket
    private lateinit var baseUrl: String
    private val headers = AtomicReference<Map<String, String>>(emptyMap())

    @Before
    fun setUp() {
        server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        thread(name = "wear-sse-test-server", isDaemon = true) {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                reader.readLine()
                val captured = mutableMapOf<String, String>()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) break
                    val split = line.indexOf(':')
                    if (split > 0) captured[line.substring(0, split).lowercase()] = line.substring(split + 1).trim()
                }
                headers.set(captured)
                val body = "id: 12\nevent: cloud-document\ndata: {\"version\":12}\n\n"
                socket.getOutputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write("HTTP/1.1 200 OK\r\n")
                    writer.write("Content-Type: text/event-stream\r\n")
                    writer.write("Content-Length: ${body.toByteArray().size}\r\n")
                    writer.write("Connection: close\r\n\r\n")
                    writer.write(body)
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
    fun listen_sendsCursorAndEmitsDocumentVersion() = runBlocking {
        var received = 0L
        WearOfficialCloudEventClient(baseUrl).listen("wear-access", 9L) { received = it }

        assertEquals(12L, received)
        assertEquals("Bearer wear-access", headers.get()["authorization"])
        assertEquals("9", headers.get()["last-event-id"])
    }
}
