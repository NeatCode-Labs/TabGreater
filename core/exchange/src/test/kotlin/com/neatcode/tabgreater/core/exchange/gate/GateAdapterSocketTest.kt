package com.neatcode.tabgreater.core.exchange.gate

import app.cash.turbine.test
import com.neatcode.tabgreater.core.model.Candle
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.Ticker
import com.neatcode.tabgreater.core.model.Timeframe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Drives Gate's `wss://.../ws/v4/` channels against MockWebServer's WebSocket support: subscribe on
 * collect, map ticker/candle updates, unsubscribe per pair, keepalive ping, close on the last collector.
 */
class GateAdapterSocketTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var scope: CoroutineScope
    private lateinit var adapter: GateAdapter

    private val serverSockets = Channel<WebSocket>(Channel.UNLIMITED)
    private val serverMessages = Channel<String>(Channel.UNLIMITED)
    private val serverClosings = Channel<Int>(Channel.UNLIMITED)

    private val serverListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            serverSockets.trySend(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            serverMessages.trySend(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            serverClosings.trySend(code)
            webSocket.close(code, reason)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            serverClosings.trySend(-1)
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        server.enqueue(MockResponse.Builder().webSocketUpgrade(serverListener).build())
        client = OkHttpClient()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        adapter = adapter(pingIntervalMs = IDLE_PING_MS)
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    @Test
    fun `watchTickers subscribes with a pair payload, maps updates and ignores everything else`() = runBlocking {
        adapter.watchTickers(listOf(BTC_USDT)).test(timeout = TIMEOUT) {
            val socket = awaitServerSocket()
            val subscribe = awaitServerMessage()
            assertTrue(subscribe, subscribe.contains("\"channel\":\"spot.tickers\""))
            assertTrue(subscribe, subscribe.contains("\"event\":\"subscribe\""))
            assertTrue(subscribe, subscribe.contains("\"payload\":[\"BTC_USDT\"]"))

            // Garbage, a failed ack, a pong and an update for a pair nobody asked for are all ignored.
            socket.send("not json at all")
            socket.send(FAILED_ACK)
            socket.send(PONG_FRAME)
            socket.send(OTHER_PAIR_UPDATE)
            socket.send(TICKER_UPDATE)

            val ticker: Ticker = awaitItem()
            assertEquals(BTC_USDT.key, ticker.key)
            assertEquals(77308.6, ticker.last, 1e-9)
            assertEquals(-0.1451, ticker.changePct24h!!, 1e-12)
            assertEquals(77308.6 / (1 - 0.001451), ticker.open24h!!, 1e-9)
            assertEquals(78835.1, ticker.high24h!!, 1e-9)
            assertEquals(76500.0, ticker.low24h!!, 1e-9)
            assertEquals(14165.5900979849, ticker.volumeBase24h!!, 1e-9)
            assertEquals(1099716974.7384602, ticker.volumeQuote24h!!, 1e-3)
            assertEquals(77308.6, ticker.bid!!, 1e-9)
            assertEquals(77308.7, ticker.ask!!, 1e-9)
            assertEquals(1787423118099L, ticker.timestamp)

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        // The last collector leaving closes the socket outright; no unsubscribe round trip.
        assertTrue(awaitServerClosing() > 0)
        assertTrue(serverMessages.tryReceive().isFailure)
    }

    @Test
    fun `watchKlines subscribes with interval and pair and maps forming and closed bars`() = runBlocking {
        adapter.watchKlines(BTC_USDT, Timeframe.M1).test(timeout = TIMEOUT) {
            val socket = awaitServerSocket()
            val subscribe = awaitServerMessage()
            assertTrue(subscribe, subscribe.contains("\"channel\":\"spot.candlesticks\""))
            assertTrue(subscribe, subscribe.contains("\"payload\":[\"1m\",\"BTC_USDT\"]"))

            // A bar of another interval on the same pair travels the shared socket and is filtered out.
            socket.send(candleFrame(name = "5m_BTC_USDT", closed = false))
            socket.send(candleFrame(name = "1m_BTC_USDT", closed = false))
            val forming: Candle = awaitItem()
            assertEquals(1787423100000L, forming.openTime)
            assertEquals(77308.6, forming.open, 1e-9)
            assertEquals(77308.7, forming.high, 1e-9)
            assertEquals(77308.6, forming.low, 1e-9)
            assertEquals(77308.6, forming.close, 1e-9)
            // "a" is the base amount; "v" (19192.24...) is the quote volume.
            assertEquals(0.248255, forming.volume, 1e-9)
            assertTrue(!forming.closed)

            socket.send(candleFrame(name = "1m_BTC_USDT", closed = true))
            assertTrue(awaitItem().closed)

            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(awaitServerClosing() > 0)
    }

    @Test
    fun `two collectors share one socket and only the released pair is unsubscribed`() = runBlocking {
        val tickers = Channel<Ticker>(Channel.UNLIMITED)
        val btcJob = scope.launch { adapter.watchTickers(listOf(BTC_USDT)).collect { tickers.send(it) } }
        val ethJob = scope.launch { adapter.watchTickers(listOf(ETH_USDT)).collect { tickers.send(it) } }

        val socket = awaitServerSocket()
        // Both subscriptions usually coalesce into one frame, but either split is acceptable.
        var subscribed = ""
        while (!subscribed.contains("BTC_USDT") || !subscribed.contains("ETH_USDT")) {
            subscribed += awaitServerMessage()
        }
        assertTrue(subscribed, subscribed.contains("\"event\":\"subscribe\""))

        socket.send(TICKER_UPDATE)
        assertEquals(BTC_USDT.key, withTimeout(TIMEOUT_MS) { tickers.receive() }.key)

        // Only one upgrade request: both flows ride the same socket.
        assertEquals(1, server.requestCount)

        btcJob.cancelAndJoin()
        val unsubscribe = awaitFrame { it.contains("\"event\":\"unsubscribe\"") }
        assertTrue(unsubscribe, unsubscribe.contains("BTC_USDT"))
        assertTrue(unsubscribe, !unsubscribe.contains("ETH_USDT"))
        assertTrue(
            "socket must stay open while the second collector is alive",
            serverClosings.tryReceive().isFailure,
        )

        ethJob.cancelAndJoin()
        assertTrue(awaitServerClosing() > 0)
    }

    @Test
    fun `the socket is kept alive with application level pings`() = runBlocking {
        val pinging = adapter(pingIntervalMs = PING_MS)
        val idleChildren = activeChildren()
        val job = scope.launch { pinging.watchTickers(listOf(BTC_USDT)).collect { } }

        awaitServerSocket()
        val ping = awaitFrame { it.contains("spot.ping") }
        assertTrue(ping, ping.contains("\"channel\":\"spot.ping\""))
        assertTrue(ping, ping.contains("\"time\":"))
        // A second frame proves the keepalive repeats on the interval.
        assertTrue(awaitFrame { it.contains("spot.ping") }.contains("\"channel\":\"spot.ping\""))

        job.cancelAndJoin()
        assertTrue(awaitServerClosing() > 0)
        // The keepalive is a coroutine in the adapter scope: once the socket is gone the scope must
        // hold no more coroutines than before the first collector, or it would ping on forever.
        assertEquals(
            "the adapter left coroutines running after the socket closed",
            idleChildren,
            awaitActiveChildren(idleChildren),
        )
        // Secondary: nothing reaches the server after the socket is gone either.
        while (serverMessages.tryReceive().isSuccess) { /* drain */ }
        delay(PING_MS * PING_QUIET_INTERVALS)
        assertTrue(serverMessages.tryReceive().isFailure)
    }

    @Test
    fun `watchTickers without markets idles instead of completing`() = runBlocking {
        // The live repository treats a completed flow as a dropped feed and restarts it with backoff.
        val idle = scope.launch { adapter.watchTickers(emptyList()).collect { } }

        assertNull("the flow completed on its own", withTimeoutOrNull(EMPTY_IDLE_MS) { idle.join() })
        assertEquals("an empty subscription must not open a socket", 0, server.requestCount)

        idle.cancelAndJoin()
    }

    private fun adapter(pingIntervalMs: Long) = GateAdapter(
        client = client,
        scope = scope,
        restBase = server.url("/").toString(),
        wsBase = "ws://${server.hostName}:${server.port}/ws/v4/",
        pingIntervalMs = pingIntervalMs,
    )

    /** Coroutines the adapter currently runs in the injected scope (socket loop, sender, keepalive). */
    private fun activeChildren(): Int = scope.coroutineContext.job.children.count { it.isActive }

    /** Cancellation is asynchronous, so give the scope a moment to settle back to [expected]. */
    private suspend fun awaitActiveChildren(expected: Int): Int {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        var count = activeChildren()
        while (count != expected && System.currentTimeMillis() < deadline) {
            delay(CHILD_POLL_MS)
            count = activeChildren()
        }
        return count
    }

    private suspend fun awaitServerSocket(): WebSocket = withTimeout(TIMEOUT_MS) { serverSockets.receive() }

    private suspend fun awaitServerMessage(): String = withTimeout(TIMEOUT_MS) { serverMessages.receive() }

    private suspend fun awaitServerClosing(): Int = withTimeout(TIMEOUT_MS) { serverClosings.receive() }

    /** Skips frames that do not matter for the assertion at hand (acks, pings, other pairs). */
    private suspend fun awaitFrame(predicate: (String) -> Boolean): String = withTimeout(TIMEOUT_MS) {
        var text = serverMessages.receive()
        while (!predicate(text)) text = serverMessages.receive()
        text
    }

    private fun candleFrame(name: String, closed: Boolean) = """
        {"time":1787423119,"time_ms":1787423119084,"channel":"spot.candlesticks","event":"update",
         "result":{"t":"1787423100","v":"19192.2484961","c":"77308.6","h":"77308.7","l":"77308.6",
                   "o":"77308.6","n":"$name","a":"0.248255","w":$closed}}
    """.trimIndent()

    private companion object {
        val TIMEOUT = 20.seconds
        const val TIMEOUT_MS = 20_000L

        /** Long enough that no keepalive frame appears in tests that do not expect one. */
        const val IDLE_PING_MS = 600_000L
        const val PING_MS = 100L
        const val PING_QUIET_INTERVALS = 3L
        const val CHILD_POLL_MS = 20L

        /** How long a flow over an empty market set has to stay alive to count as "not completing". */
        const val EMPTY_IDLE_MS = 500L

        val BTC_USDT = Market(
            key = MarketKey.of(ExchangeId.GATE, "BTC", "USDT"),
            nativeSymbol = "BTC_USDT",
            pricePrecision = 1,
            tickSize = 0.1,
        )

        val ETH_USDT = Market(
            key = MarketKey.of(ExchangeId.GATE, "ETH", "USDT"),
            nativeSymbol = "ETH_USDT",
            pricePrecision = 2,
            tickSize = 0.01,
        )

        const val TICKER_UPDATE = """
        {"time":1787423118,"time_ms":1787423118099,"channel":"spot.tickers","event":"update",
         "result":{"currency_pair":"BTC_USDT","last":"77308.6","lowest_ask":"77308.7","highest_bid":"77308.6",
                   "change_percentage":"-0.1451","base_volume":"14165.5900979849",
                   "quote_volume":"1099716974.73846020148","high_24h":"78835.1","low_24h":"76500"}}
        """

        const val OTHER_PAIR_UPDATE = """
        {"time":1787423118,"time_ms":1787423118099,"channel":"spot.tickers","event":"update",
         "result":{"currency_pair":"DOGE_USDT","last":"0.1","lowest_ask":"0.11","highest_bid":"0.09",
                   "change_percentage":"1","base_volume":"1","quote_volume":"1","high_24h":"1","low_24h":"1"}}
        """

        const val FAILED_ACK = """
        {"time":1787423113,"channel":"spot.tickers","event":"subscribe","payload":["NOPE_XXX"],
         "error":{"code":2,"message":"unknown currency pair: NOPE_XXX"},"result":{"status":"fail"},"requestId":"x"}
        """

        const val PONG_FRAME = """
        {"time":1787423113,"time_ms":1787423113699,"conn_id":"425849b1745325b6","channel":"spot.pong",
         "event":"","result":null,"requestId":"x"}
        """
    }
}
