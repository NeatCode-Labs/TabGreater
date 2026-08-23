package com.neatcode.tabgreater.core.exchange.kucoin

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
 * Drives the KuCoin socket against MockWebServer: the `bullet-public` handshake feeds the WebSocket
 * URL back to the same server, so one queue serves both the token request and the upgrade.
 */
class KuCoinAdapterSocketTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var scope: CoroutineScope

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
        server.enqueue(MockResponse.Builder().code(200).body(bulletBody()).build())
        server.enqueue(MockResponse.Builder().webSocketUpgrade(serverListener).build())
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
    fun `watchTickers handshakes with bullet-public and maps snapshot and ticker frames`() = runBlocking {
        val adapter = newAdapter()

        adapter.watchTickers(listOf(BTC_USDT, ETH_USDT)).test(timeout = TIMEOUT) {
            val socket = awaitServerSocket()
            val frames = awaitServerMessages(SUBSCRIBE_FRAMES)
            val tickerFrame = frames.single { it.contains("/market/ticker:") }
            assertTrue(tickerFrame, tickerFrame.contains("\"type\":\"subscribe\""))
            assertTrue(tickerFrame, tickerFrame.contains("\"topic\":\"/market/ticker:BTC-USDT,ETH-USDT\""))
            assertTrue(tickerFrame, tickerFrame.contains("\"privateChannel\":false"))
            assertTrue(tickerFrame, tickerFrame.contains("\"response\":true"))
            // The snapshot channel takes exactly one symbol per topic string.
            assertTrue("$frames", frames.any { it.contains("\"topic\":\"/market/snapshot:BTC-USDT\"") })
            assertTrue("$frames", frames.any { it.contains("\"topic\":\"/market/snapshot:ETH-USDT\"") })

            // Handshake noise and garbage must never reach the flow.
            socket.send(WELCOME)
            socket.send(ACK)
            socket.send(ERROR_FRAME)
            socket.send("not json at all")

            socket.send(SNAPSHOT_FRAME)
            val snapshot: Ticker = awaitItem()
            assertEquals(BTC_USDT.key, snapshot.key)
            assertEquals(77320.7, snapshot.last, 1e-9)
            assertEquals(77443.7, snapshot.open24h!!, 1e-9)
            assertEquals(78816.1, snapshot.high24h!!, 1e-9)
            assertEquals(76486.5, snapshot.low24h!!, 1e-9)
            assertEquals(4235.4, snapshot.volumeBase24h!!, 1e-9)
            assertEquals(328310222.5, snapshot.volumeQuote24h!!, 1e-3)
            assertEquals(77320.7, snapshot.bid!!, 1e-9)
            assertEquals(77320.8, snapshot.ask!!, 1e-9)
            assertEquals(CHANGE_PCT, snapshot.changePct24h!!, 1e-9)
            assertEquals(1787423030030L, snapshot.timestamp)

            // A trade.ticker frame has no statistics of its own: they come from the cached snapshot.
            socket.send(TICKER_FRAME)
            val ticker: Ticker = awaitItem()
            assertEquals(BTC_USDT.key, ticker.key)
            assertEquals(77320.7, ticker.last, 1e-9)
            assertEquals(77320.7, ticker.bid!!, 1e-9)
            assertEquals(77320.8, ticker.ask!!, 1e-9)
            assertEquals(77443.7, ticker.open24h!!, 1e-9)
            assertEquals(CHANGE_PCT, ticker.changePct24h!!, 1e-9)
            assertEquals(1787423027834L, ticker.timestamp)

            cancelAndIgnoreRemainingEvents()
        }

        val bullet = server.takeRequest()
        assertEquals("/api/v1/bullet-public", bullet.url.encodedPath)
        assertEquals("POST", bullet.method)
        val upgrade = server.takeRequest()
        assertEquals(TOKEN, upgrade.url.queryParameter("token"))
        assertTrue(upgrade.url.queryParameter("connectId")!!.isNotEmpty())

        // The last collector leaving closes the socket outright.
        assertTrue(awaitServerClosing() > 0)
    }

    @Test
    fun `a ticker frame before the first snapshot is emitted without statistics`() = runBlocking {
        val adapter = newAdapter()

        adapter.watchTickers(listOf(BTC_USDT)).test(timeout = TIMEOUT) {
            val socket = awaitServerSocket()
            awaitServerMessages(2)

            socket.send(TICKER_FRAME)
            val ticker: Ticker = awaitItem()
            assertEquals(77320.7, ticker.last, 1e-9)
            assertNull(ticker.open24h)
            assertNull(ticker.changePct24h)

            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(awaitServerClosing() > 0)
    }

    @Test
    fun `watchKlines maps updates and closes the previous bar when a new one is added`() = runBlocking {
        val adapter = newAdapter()

        adapter.watchKlines(BTC_USDT, Timeframe.M1).test(timeout = TIMEOUT) {
            val socket = awaitServerSocket()
            val subscribe = awaitServerMessage()
            assertTrue(subscribe, subscribe.contains("\"topic\":\"/market/candles:BTC-USDT_1min\""))

            socket.send(candleFrame(CANDLES_UPDATE, FIRST_BAR_SECONDS))
            val forming: Candle = awaitItem()
            assertEquals(FIRST_BAR_SECONDS * 1000, forming.openTime)
            assertEquals(77333.7, forming.open, 1e-9)
            assertEquals(77333.7, forming.high, 1e-9)
            assertEquals(77320.2, forming.low, 1e-9)
            assertEquals(77320.2, forming.close, 1e-9)
            assertEquals(0.41026251, forming.volume, 1e-9)
            assertTrue(!forming.closed)

            // KuCoin never marks a bar final; the "add" of the next one does it.
            socket.send(candleFrame(CANDLES_ADD, SECOND_BAR_SECONDS))
            val finished: Candle = awaitItem()
            assertEquals(FIRST_BAR_SECONDS * 1000, finished.openTime)
            assertTrue(finished.closed)
            val started: Candle = awaitItem()
            assertEquals(SECOND_BAR_SECONDS * 1000, started.openTime)
            assertTrue(!started.closed)

            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(awaitServerClosing() > 0)
    }

    @Test
    fun `collectors share one socket and only the keys of the leaving one are unsubscribed`() = runBlocking {
        val adapter = newAdapter()
        val both = Channel<Ticker>(Channel.UNLIMITED)
        val btcOnly = Channel<Ticker>(Channel.UNLIMITED)

        val bothJob = scope.launch {
            adapter.watchTickers(listOf(BTC_USDT, ETH_USDT)).collect { both.send(it) }
        }
        val socket = awaitServerSocket()
        awaitServerMessages(SUBSCRIBE_FRAMES)

        // The second collector only re-uses keys, so it must not produce a single extra frame.
        val btcJob = scope.launch { adapter.watchTickers(listOf(BTC_USDT)).collect { btcOnly.send(it) } }
        // The socket is hot: frames sent before the second flow subscribes are dropped, so repeat
        // until it is attached.
        val shared = withTimeout(TIMEOUT_MS) {
            var ticker: Ticker? = null
            while (ticker == null) {
                socket.send(TICKER_FRAME)
                ticker = withTimeoutOrNull(RESEND_GAP_MS) { btcOnly.receive() }
            }
            ticker
        }
        assertEquals(BTC_USDT.key, shared.key)
        assertEquals(BTC_USDT.key, withTimeout(TIMEOUT_MS) { both.receive() }.key)
        // One bullet request plus one upgrade: both flows ride the same socket.
        assertEquals(2, server.requestCount)
        assertTrue(serverMessages.tryReceive().isFailure)

        bothJob.cancelAndJoin()
        val unsubscribe = awaitServerMessages(2)
        assertTrue("$unsubscribe", unsubscribe.all { it.contains("\"type\":\"unsubscribe\"") })
        assertTrue("$unsubscribe", unsubscribe.any { it.contains("\"topic\":\"/market/ticker:ETH-USDT\"") })
        assertTrue("$unsubscribe", unsubscribe.any { it.contains("\"topic\":\"/market/snapshot:ETH-USDT\"") })
        assertTrue("$unsubscribe", unsubscribe.none { it.contains("BTC-USDT") })
        assertTrue(
            "socket must stay open while the second collector is alive",
            serverClosings.tryReceive().isFailure,
        )

        btcJob.cancelAndJoin()
        assertTrue(awaitServerClosing() > 0)
    }

    @Test
    fun `keepalive pings are sent while the socket is alive`() = runBlocking {
        val adapter = newAdapter(pingIntervalMs = 100L)
        val tickers = Channel<Ticker>(Channel.UNLIMITED)
        val job = scope.launch { adapter.watchTickers(listOf(BTC_USDT)).collect { tickers.send(it) } }

        awaitServerSocket()
        var ping: String? = null
        while (ping == null) {
            val frame = awaitServerMessage()
            if (frame.contains("\"type\":\"ping\"")) ping = frame
        }
        assertTrue(ping, ping.contains("\"id\":\""))

        // The ping coroutine is bound to the socket lifetime, so the last collector stops it.
        job.cancelAndJoin()
        assertTrue(awaitServerClosing() > 0)
    }

    @Test
    fun `a reconnect resubscribes every active key with a fresh token`() = runBlocking {
        // A dropped session resolves its URL again, so the queue needs a second handshake pair.
        server.enqueue(MockResponse.Builder().code(200).body(bulletBody()).build())
        server.enqueue(MockResponse.Builder().webSocketUpgrade(serverListener).build())
        val adapter = newAdapter()
        val tickers = Channel<Ticker>(Channel.UNLIMITED)
        val job = scope.launch {
            adapter.watchTickers(listOf(BTC_USDT, ETH_USDT)).collect { tickers.send(it) }
        }

        val first = awaitServerSocket()
        awaitServerMessages(SUBSCRIBE_FRAMES)

        // The server hangs up: KuCoin forgets every topic, so the adapter has to send them again.
        first.close(NORMAL_CLOSURE, "bye")
        val second = awaitServerSocket()
        val resubscribe = awaitServerMessages(SUBSCRIBE_FRAMES)
        assertTrue("$resubscribe", resubscribe.all { it.contains("\"type\":\"subscribe\"") })
        assertTrue("$resubscribe", resubscribe.any { it.contains("\"topic\":\"/market/ticker:BTC-USDT,ETH-USDT\"") })
        assertTrue("$resubscribe", resubscribe.any { it.contains("\"topic\":\"/market/snapshot:BTC-USDT\"") })
        assertTrue("$resubscribe", resubscribe.any { it.contains("\"topic\":\"/market/snapshot:ETH-USDT\"") })
        // Two bullet requests and two upgrades: the token is never reused across sessions.
        assertEquals(4, server.requestCount)

        // The new session really carries the flow.
        val live = withTimeout(TIMEOUT_MS) {
            var ticker: Ticker? = null
            while (ticker == null) {
                second.send(TICKER_FRAME)
                ticker = withTimeoutOrNull(RESEND_GAP_MS) { tickers.receive() }
            }
            ticker
        }
        assertEquals(BTC_USDT.key, live.key)

        while (serverClosings.tryReceive().isSuccess) { /* drop the first hang-up */ }
        job.cancelAndJoin()
        assertTrue(awaitServerClosing() > 0)
    }

    @Test
    fun `ticker topics are chunked at a hundred symbols per frame`() = runBlocking {
        val adapter = newAdapter()
        val markets = (1..CHUNKED_MARKETS).map { usdtMarket("C$it") }

        adapter.watchTickers(markets).test(timeout = TIMEOUT) {
            awaitServerSocket()
            // Ticker batches are queued ahead of the per-symbol snapshot frames.
            val first = tickerSymbolsOf(awaitServerMessage())
            val second = tickerSymbolsOf(awaitServerMessage())
            assertEquals(MAX_SYMBOLS_PER_TOPIC, first.size)
            assertEquals(listOf("C${CHUNKED_MARKETS}-USDT"), second)
            assertEquals("C1-USDT", first.first())
            assertEquals("C$MAX_SYMBOLS_PER_TOPIC-USDT", first.last())

            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(awaitServerClosing() > 0)
    }

    @Test
    fun `watchTickers without markets never completes and opens no socket`() = runBlocking {
        val adapter = newAdapter()

        // The live repository reads completion as a dropped stream, so an empty set must just idle.
        val completed = withTimeoutOrNull(IDLE_MS) {
            adapter.watchTickers(emptyList()).collect { }
            true
        }

        assertNull("watchTickers must not complete on its own", completed)
        assertEquals(0, server.requestCount)
    }

    /** Symbols of a `/market/ticker:a,b,c` subscribe frame. */
    private fun tickerSymbolsOf(frame: String): List<String> {
        assertTrue(frame, frame.contains(TICKER_TOPIC_PREFIX))
        return frame.substringAfter(TICKER_TOPIC_PREFIX).substringBefore('"').split(",")
    }

    private fun usdtMarket(base: String) = Market(
        key = MarketKey.of(ExchangeId.KUCOIN, base, "USDT"),
        nativeSymbol = "$base-USDT",
        pricePrecision = 4,
    )

    private fun newAdapter(pingIntervalMs: Long? = null) = KuCoinAdapter(
        client = client,
        scope = scope,
        restBase = server.url("/").toString(),
        pingIntervalOverrideMs = pingIntervalMs,
    )

    private fun bulletBody() = """
        {"code":"200000","data":{"token":"$TOKEN","instanceServers":[
          {"endpoint":"ws://${server.hostName}:${server.port}/","encrypt":false,"protocol":"websocket",
           "pingInterval":18000,"pingTimeout":10000}]}}
    """.trimIndent()

    private suspend fun awaitServerSocket(): WebSocket = withTimeout(TIMEOUT_MS) { serverSockets.receive() }

    private suspend fun awaitServerMessage(): String = withTimeout(TIMEOUT_MS) { serverMessages.receive() }

    private suspend fun awaitServerMessages(count: Int): List<String> = (1..count).map { awaitServerMessage() }

    private suspend fun awaitServerClosing(): Int = withTimeout(TIMEOUT_MS) { serverClosings.receive() }

    private fun candleFrame(subject: String, openTimeSeconds: Long) = """
        {"topic":"/market/candles:BTC-USDT_1min","type":"message","subject":"$subject",
         "data":{"symbol":"BTC-USDT",
                 "candles":["$openTimeSeconds","77333.7","77320.2","77333.7","77320.2","0.41026251",
                            "31723.684496325"],
                 "time":1787423033254043338}}
    """.trimIndent()

    private companion object {
        val TIMEOUT = 20.seconds
        const val TIMEOUT_MS = 20_000L
        const val RESEND_GAP_MS = 200L

        /** Long enough for a flow that would complete on its own to do so. */
        const val IDLE_MS = 500L

        /** One batched ticker topic plus one snapshot topic per symbol. */
        const val SUBSCRIBE_FRAMES = 3

        /** KuCoin accepts at most 100 symbols per topic string. */
        const val MAX_SYMBOLS_PER_TOPIC = 100

        /** One more than a full batch, so the chunker has to emit a second frame. */
        const val CHUNKED_MARKETS = MAX_SYMBOLS_PER_TOPIC + 1

        const val TICKER_TOPIC_PREFIX = "\"topic\":\"/market/ticker:"

        /** RFC 6455 normal closure, used by the test server to hang up. */
        const val NORMAL_CLOSURE = 1000

        const val TOKEN = "2neAiuYv-bullet-token"

        const val CANDLES_UPDATE = "trade.candles.update"
        const val CANDLES_ADD = "trade.candles.add"
        const val FIRST_BAR_SECONDS = 1787422980L
        const val SECOND_BAR_SECONDS = 1787423040L

        /** Snapshot open 77443.7 against a last of 77320.7. */
        const val CHANGE_PCT = (77320.7 - 77443.7) / 77443.7 * 100.0

        val BTC_USDT = Market(
            key = MarketKey.of(ExchangeId.KUCOIN, "BTC", "USDT"),
            nativeSymbol = "BTC-USDT",
            pricePrecision = 1,
            tickSize = 0.1,
        )

        val ETH_USDT = Market(
            key = MarketKey.of(ExchangeId.KUCOIN, "ETH", "USDT"),
            nativeSymbol = "ETH-USDT",
            pricePrecision = 2,
            tickSize = 0.01,
        )

        const val WELCOME = """{"id":"connect-id","type":"welcome"}"""
        const val ACK = """{"id":"1","type":"ack"}"""
        const val ERROR_FRAME = """{"id":"9","type":"error","code":404,"data":"topic /market/nope is not found"}"""

        const val TICKER_FRAME = """
        {"topic":"/market/ticker:BTC-USDT","type":"message","subject":"trade.ticker",
         "data":{"bestAsk":"77320.8","bestAskSize":"0.4901691","bestBid":"77320.7",
                 "bestBidSize":"0.18858834","price":"77320.7","sequence":"35915948987",
                 "size":"0.00065102","time":1787423027834}}
        """

        const val SNAPSHOT_FRAME = """
        {"topic":"/market/snapshot:BTC-USDT","type":"message","subject":"trade.snapshot",
         "data":{"sequence":"35915949287","data":{"askSize":0.4901566,"averagePrice":77522.52725074,
          "baseCurrency":"BTC","bidSize":0.18858834,"board":1,"buy":77320.7,"changePrice":-123.0,
          "changeRate":-0.0015,"close":77320.7,"datetime":1787423030030,"high":78816.1,
          "lastSize":0.00065102,"lastTradedPrice":77320.7,"low":76486.5,"makerCoefficient":1.0,
          "makerFeeRate":0.001,"marginTrade":true,"mark":0,"market":"USDS",
          "marketChange24h":{"changePrice":-123.0,"changeRate":-0.0015,"high":78816.1,"low":76486.5,
                             "open":77443.7,"vol":4235.4,"volValue":328310222.5},
          "open":77443.7,"quoteCurrency":"USDT","sell":77320.8,"sort":100,"symbol":"BTC-USDT",
          "symbolCode":"BTC-USDT","trading":true,"vol":4235.4,"volValue":328310222.5}}}
        """
    }
}
