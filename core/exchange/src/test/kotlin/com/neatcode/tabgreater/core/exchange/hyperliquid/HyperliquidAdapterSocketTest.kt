package com.neatcode.tabgreater.core.exchange.hyperliquid

import com.neatcode.tabgreater.core.model.Candle
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.Timeframe
import com.neatcode.tabgreater.core.model.Ticker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HyperliquidAdapterSocketTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var scope: CoroutineScope
    private lateinit var adapter: HyperliquidAdapter
    private val serverSockets = Channel<WebSocket>(Channel.UNLIMITED)
    private val serverMessages = Channel<String>(Channel.UNLIMITED)

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            serverSockets.trySend(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            serverMessages.trySend(text)
        }

    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        server.enqueue(MockResponse.Builder().webSocketUpgrade(listener).build())
        client = OkHttpClient()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        adapter = HyperliquidAdapter(
            client = client,
            scope = scope,
            wsBase = "ws://${server.hostName}:${server.port}/ws",
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    @Test
    fun `trades and object-form candles share one socket and preserve native coins`() = runBlocking {
        val tickerChannel = Channel<Ticker>(Channel.UNLIMITED)
        val candleChannel = Channel<Candle>(Channel.UNLIMITED)
        val tickerJob = scope.launch { adapter.watchTickers(listOf(HIP3_ANTHROPIC)).collect { tickerChannel.send(it) } }
        val candleJob = scope.launch {
            adapter.watchKlines(HIP3_ANTHROPIC, Timeframe.M1).collect { candleChannel.send(it) }
        }

        val socket = awaitServerSocket()
        val subscriptions = listOf(awaitServerMessage(), awaitServerMessage(), awaitServerMessage())
        assertTrue(subscriptions.any { it.contains("\"type\":\"trades\"") && it.contains("\"coin\":\"xyz:ANT\"") })
        assertTrue(subscriptions.any { it.contains("\"type\":\"bbo\"") && it.contains("\"coin\":\"xyz:ANT\"") })
        assertTrue(subscriptions.any {
            it.contains("\"type\":\"candle\"") && it.contains("\"coin\":\"xyz:ANT\"") && it.contains("\"interval\":\"1m\"")
        })

        socket.send(
            """{"channel":"trades","data":[{"coin":"xyz:ANT","side":"B","px":"10.5","sz":"1","time":1787415256000}]}""",
        )
        val ticker = withTimeout(TIMEOUT_MS) { tickerChannel.receive() }
        assertEquals(HIP3_ANTHROPIC.key, ticker.key)
        assertEquals(10.5, ticker.last, 1e-12)

        socket.send(
            """{"channel":"bbo","data":{"coin":"xyz:ANT","time":1787415257000,"bbo":[{"px":"10.4","sz":"2","n":1},{"px":"10.6","sz":"3","n":2}]}}""",
        )
        val quote = withTimeout(TIMEOUT_MS) { tickerChannel.receive() }
        assertEquals(10.5, quote.last, 1e-12)
        assertEquals(10.4, quote.bid!!, 1e-12)
        assertEquals(10.6, quote.ask!!, 1e-12)
        assertEquals(1787415257000L, quote.timestamp)
        assertFalse(quote.confirmsPrice)

        // The production candle channel sends one object, while REST candleSnapshot sends an array.
        socket.send(
            """{"channel":"candle","data":{"t":1790000000000,"T":2000000000000,"s":"xyz:ANT","i":"1m","o":"10","h":"11","l":"9","c":"10.5","v":"2","n":3}}""",
        )
        val candle = withTimeout(TIMEOUT_MS) { candleChannel.receive() }
        assertEquals(1_790_000_000_000L, candle.openTime)
        assertEquals(10.5, candle.close, 1e-12)
        assertFalse(candle.closed)

        tickerJob.cancelAndJoin()
        candleJob.cancelAndJoin()
        assertEquals(1, server.requestCount) // Both subscription types shared one connection.
    }

    private suspend fun awaitServerSocket(): WebSocket = withTimeout(TIMEOUT_MS) { serverSockets.receive() }

    private suspend fun awaitServerMessage(): String = withTimeout(TIMEOUT_MS) { serverMessages.receive() }

    private companion object {
        const val TIMEOUT_MS = 20_000L

        val HIP3_ANTHROPIC = Market(
            key = MarketKey.of(ExchangeId.HYPERLIQUID, "HIP3_XYZ_ANT", "USD"),
            nativeSymbol = "xyz:ANT",
            pricePrecision = 2,
        )
    }
}
