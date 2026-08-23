package com.neatcode.tabgreater.core.exchange.ws

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ExchangeSocketTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var scope: CoroutineScope

    private val serverSockets = Channel<WebSocket>(Channel.UNLIMITED)
    private val serverMessages = Channel<String>(Channel.UNLIMITED)

    private val serverListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            serverSockets.trySend(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            serverMessages.trySend(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    @Test
    fun `frames sent before the socket is open are flushed on connect`() = runBlocking {
        server.enqueue(MockResponse.Builder().webSocketUpgrade(serverListener).build())
        val socket = newSocket()

        socket.send("hello")
        socket.connect()

        assertEquals("hello", withTimeout(TIMEOUT_MS) { serverMessages.receive() })
        assertEquals(SocketState.OPEN, withTimeout(TIMEOUT_MS) { socket.state.first { it == SocketState.OPEN } })
        socket.close()
    }

    @Test
    fun `a dropped connection is re-established and reported through onReconnected`() = runBlocking {
        repeat(2) { server.enqueue(MockResponse.Builder().webSocketUpgrade(serverListener).build()) }
        val reconnects = Channel<Unit>(Channel.UNLIMITED)
        val received = Channel<String>(Channel.UNLIMITED)
        val socket = newSocket()
        socket.onReconnected = { reconnects.trySend(Unit) }
        val collector: Job = scope.launch { socket.messages.collect { received.send(it) } }

        socket.connect()
        val first = withTimeout(TIMEOUT_MS) { serverSockets.receive() }
        first.close(1000, "server restart")

        withTimeout(TIMEOUT_MS) { reconnects.receive() }
        val second = withTimeout(TIMEOUT_MS) { serverSockets.receive() }
        assertEquals(2, server.requestCount)

        second.send("after-reconnect")
        assertEquals("after-reconnect", withTimeout(TIMEOUT_MS) { received.receive() })

        socket.close()
        collector.cancel()
    }

    private fun newSocket() = ExchangeSocket(
        client = client,
        url = "ws://${server.hostName}:${server.port}/stream",
        name = "test",
        scope = scope,
    )

    private companion object {
        const val TIMEOUT_MS = 20_000L
    }
}
