package com.neatcode.tabgreater.core.exchange.kraken

import app.cash.turbine.test
import com.neatcode.tabgreater.core.exchange.ratelimit.TokenBucket
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * Drives the Kraken v2 socket against MockWebServer: subscribe on collect with the v2 symbol,
 * map ticker/ohlc frames, unsubscribe per channel, close when the last collector leaves.
 */
class KrakenAdapterSocketTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var scope: CoroutineScope
    private lateinit var adapter: KrakenAdapter

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
        adapter = newAdapter(pingIntervalMs = QUIET_PING_MS)
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    @Test
    fun `watchTickers subscribes with the v2 symbol and maps ticker frames`() = runBlocking {
        adapter.watchTickers(listOf(BTC_EUR)).test(timeout = TIMEOUT) {
            val socket = awaitServerSocket()
            val subscribe = awaitServerMessage()
            assertTrue(subscribe, subscribe.contains("\"method\":\"subscribe\""))
            assertTrue(subscribe, subscribe.contains("\"channel\":\"ticker\""))
            assertTrue(subscribe, subscribe.contains("\"BTC/EUR\""))
            // XBT/EUR is the v1 name; the v2 endpoint rejects it.
            assertTrue(subscribe, !subscribe.contains("XBT"))
            assertTrue(subscribe, subscribe.contains("\"snapshot\":true"))

            socket.send(TICKER_FRAME)
            val ticker: Ticker = awaitItem()
            assertEquals(BTC_EUR.key, ticker.key)
            assertEquals(65927.0, ticker.last, 1e-9)
            assertEquals(65926.9, ticker.bid!!, 1e-9)
            assertEquals(65927.0, ticker.ask!!, 1e-9)
            assertEquals(67389.2, ticker.high24h!!, 1e-9)
            assertEquals(65160.5, ticker.low24h!!, 1e-9)
            assertEquals(627.85684450, ticker.volumeBase24h!!, 1e-9)
            assertEquals(627.85684450 * 66266.2, ticker.volumeQuote24h!!, 1e-6)
            // Only the stream carries a rolling 24 h change, so the open is derived from it.
            assertEquals(65927.0 + 243.3, ticker.open24h!!, 1e-9)
            assertEquals(-0.37, ticker.changePct24h!!, 1e-9)
            assertEquals(Instant.parse("2026-08-22T18:15:11.309506Z").toEpochMilli(), ticker.timestamp)

            cancelAndIgnoreRemainingEvents()
        }

        // The last collector leaving closes the socket outright; no unsubscribe round trip.
        assertTrue(awaitServerClosing() > 0)
        assertTrue(serverMessages.tryReceive().isFailure)
    }

    @Test
    fun `watchKlines maps ohlc frames and closes the previous bar when the bucket rolls`() = runBlocking {
        adapter.watchKlines(BTC_EUR, Timeframe.M15).test(timeout = TIMEOUT) {
            val socket = awaitServerSocket()
            val subscribe = awaitServerMessage()
            assertTrue(subscribe, subscribe.contains("\"channel\":\"ohlc\""))
            assertTrue(subscribe, subscribe.contains("\"interval\":15"))
            // A snapshot would push hundreds of bars per pair; REST seeded the history already.
            assertTrue(subscribe, subscribe.contains("\"snapshot\":false"))

            socket.send(ohlcFrame(begin = FIRST_BUCKET, close = 65927.0))
            val forming: Candle = awaitItem()
            assertEquals(Instant.parse(FIRST_BUCKET).toEpochMilli(), forming.openTime)
            assertEquals(65927.0, forming.open, 1e-9)
            assertEquals(65930.0, forming.high, 1e-9)
            assertEquals(65920.0, forming.low, 1e-9)
            assertEquals(65927.0, forming.close, 1e-9)
            assertEquals(0.01591681, forming.volume, 1e-9)
            assertTrue(!forming.closed)

            socket.send(ohlcFrame(begin = SECOND_BUCKET, close = 65950.0))
            val closed: Candle = awaitItem()
            assertEquals(Instant.parse(FIRST_BUCKET).toEpochMilli(), closed.openTime)
            assertTrue(closed.closed)
            val next: Candle = awaitItem()
            assertEquals(Instant.parse(SECOND_BUCKET).toEpochMilli(), next.openTime)
            assertTrue(!next.closed)

            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(awaitServerClosing() > 0)
    }

    /** Kraken forgets every subscription when a connection drops, so the adapter must re-send them. */
    @Test
    fun `a dropped connection is resubscribed on the new socket`() = runBlocking {
        // Second upgrade response: the reconnect lands on the same MockWebServer.
        server.enqueue(MockResponse.Builder().webSocketUpgrade(serverListener).build())

        adapter.watchTickers(listOf(BTC_EUR)).test(timeout = TIMEOUT) {
            val first = awaitServerSocket()
            val subscribe = awaitServerMessage()
            assertTrue(subscribe, subscribe.contains("\"BTC/EUR\""))

            first.close(NORMAL_CLOSURE, "server drop")

            val second = awaitServerSocket()
            val resubscribe = awaitServerMessage()
            assertTrue(resubscribe, resubscribe.contains("\"method\":\"subscribe\""))
            assertTrue(resubscribe, resubscribe.contains("\"channel\":\"ticker\""))
            assertTrue(resubscribe, resubscribe.contains("\"BTC/EUR\""))
            assertEquals(2, server.requestCount)

            // The collector survived the drop and is fed by the fresh connection.
            second.send(TICKER_FRAME)
            assertEquals(BTC_EUR.key, awaitItem().key)

            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Kraken caps the symbol list of one frame, so a large watchlist is spread over several. */
    @Test
    fun `watchTickers batches fifty symbols per subscribe frame`() = runBlocking {
        val markets = (1..60).map(::market)

        adapter.watchTickers(markets).test(timeout = TIMEOUT) {
            awaitServerSocket()
            val first = symbolsOf(awaitServerMessage())
            val second = symbolsOf(awaitServerMessage())

            assertEquals(50, first.size)
            assertEquals(10, second.size)
            assertEquals("C1/EUR", first.first())
            assertEquals("C50/EUR", first.last())
            assertEquals("C51/EUR", second.first())
            assertEquals("C60/EUR", second.last())

            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(awaitServerClosing() > 0)
    }

    @Test
    fun `heartbeats, rejections and junk are ignored`() = runBlocking {
        adapter.watchTickers(listOf(BTC_EUR)).test(timeout = TIMEOUT) {
            val socket = awaitServerSocket()
            awaitServerMessage()

            socket.send(STATUS_FRAME)
            socket.send(HEARTBEAT_FRAME)
            socket.send(SUBSCRIBE_ACK)
            socket.send(REJECTION_FRAME)
            socket.send("not json at all")
            socket.send("""{"channel":"ticker","type":"update","data":[{"symbol":"ETH/EUR","last":1.0}]}""")
            expectNoEvents()

            socket.send(TICKER_FRAME)
            assertEquals(BTC_EUR.key, awaitItem().key)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ticker and ohlc collectors share one socket that closes with the last of them`() = runBlocking {
        val tickers = Channel<Ticker>(Channel.UNLIMITED)
        val candles = Channel<Candle>(Channel.UNLIMITED)
        val tickerJob = scope.launch { adapter.watchTickers(listOf(BTC_EUR)).collect { tickers.send(it) } }
        val klineJob = scope.launch { adapter.watchKlines(BTC_EUR, Timeframe.M15).collect { candles.send(it) } }

        val socket = awaitServerSocket()
        // Kraken needs one frame per channel, but both ride the same connection.
        val subscribes = listOf(awaitServerMessage(), awaitServerMessage())
        assertTrue(subscribes.toString(), subscribes.any { it.contains("\"channel\":\"ticker\"") })
        assertTrue(subscribes.toString(), subscribes.any { it.contains("\"channel\":\"ohlc\"") })
        assertEquals(1, server.requestCount)

        socket.send(TICKER_FRAME)
        socket.send(ohlcFrame(begin = FIRST_BUCKET, close = 65927.0))
        assertEquals(BTC_EUR.key, withTimeout(TIMEOUT_MS) { tickers.receive() }.key)
        assertEquals(
            Instant.parse(FIRST_BUCKET).toEpochMilli(),
            withTimeout(TIMEOUT_MS) { candles.receive() }.openTime,
        )

        tickerJob.cancelAndJoin()
        val unsubscribe = awaitServerMessage()
        assertTrue(unsubscribe, unsubscribe.contains("\"method\":\"unsubscribe\""))
        assertTrue(unsubscribe, unsubscribe.contains("\"channel\":\"ticker\""))
        assertTrue(unsubscribe, unsubscribe.contains("\"BTC/EUR\""))
        assertTrue(
            "socket must stay open while the kline collector is alive",
            serverClosings.tryReceive().isFailure,
        )

        klineJob.cancelAndJoin()
        assertTrue(awaitServerClosing() > 0)
    }

    @Test
    fun `the adapter pings while the socket is open`() = runBlocking {
        val pinging = newAdapter(pingIntervalMs = FAST_PING_MS)

        val job = scope.launch { pinging.watchTickers(listOf(BTC_EUR)).collect { } }
        awaitServerSocket()
        var message = awaitServerMessage()
        while (!message.contains("\"method\":\"ping\"")) message = awaitServerMessage()

        job.cancelAndJoin()
        assertTrue(awaitServerClosing() > 0)
    }

    /** Kraken has no monthly channel, so 1M rides the daily one and is re-aggregated per frame. */
    @Test
    fun `watchKlines builds monthly bars from the daily stream, seeded over REST`() = runBlocking {
        // Its own server: the REST seed request must not consume the WebSocket upgrade response.
        val monthlyServer = MockWebServer()
        monthlyServer.start()
        monthlyServer.enqueue(MockResponse.Builder().code(200).body(DAILY_OHLC).build())
        monthlyServer.enqueue(MockResponse.Builder().webSocketUpgrade(serverListener).build())
        val monthly = newAdapter(pingIntervalMs = QUIET_PING_MS, target = monthlyServer)

        try {
            monthly.watchKlines(BTC_EUR, Timeframe.MN1).test(timeout = TIMEOUT) {
                val socket = awaitServerSocket()
                val seed = monthlyServer.takeRequest()
                assertEquals("/0/public/OHLC", seed.url.encodedPath)
                assertEquals("1440", seed.url.queryParameter("interval"))
                val subscribe = awaitServerMessage()
                assertTrue(subscribe, subscribe.contains("\"channel\":\"ohlc\""))
                assertTrue(subscribe, subscribe.contains("\"interval\":1440"))

                socket.send(dailyFrame(begin = AUGUST_3, high = 165.0, low = 140.0, close = 160.0))
                val august: Candle = awaitItem()
                assertEquals(AUGUST_1_SECONDS * 1000, august.openTime)
                // Seeded 1 Aug open, extremes across the seeded days and the streamed one.
                assertEquals(121.0, august.open, 1e-9)
                assertEquals(165.0, august.high, 1e-9)
                assertEquals(118.0, august.low, 1e-9)
                assertEquals(160.0, august.close, 1e-9)
                assertEquals(12.0, august.volume, 1e-9)
                assertTrue(!august.closed)

                socket.send(dailyFrame(begin = SEPTEMBER_1, high = 170.0, low = 155.0, close = 165.0))
                val closed: Candle = awaitItem()
                assertEquals(AUGUST_1_SECONDS * 1000, closed.openTime)
                assertEquals(160.0, closed.close, 1e-9)
                assertTrue(closed.closed)
                val september: Candle = awaitItem()
                assertEquals(SEPTEMBER_1_SECONDS * 1000, september.openTime)
                assertEquals(165.0, september.close, 1e-9)
                assertTrue(!september.closed)

                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(awaitServerClosing() > 0)
        } finally {
            monthlyServer.close()
        }
    }

    private fun newAdapter(pingIntervalMs: Long, target: MockWebServer = server) = KrakenAdapter(
        client = client,
        scope = scope,
        restBase = target.url("/").toString(),
        wsBase = "ws://${target.hostName}:${target.port}",
        restBucket = TokenBucket(capacity = 64.0, refillPerSecond = 10_000.0),
        pingIntervalMs = pingIntervalMs,
    )

    private fun market(index: Int) = Market(
        key = MarketKey.of(ExchangeId.KRAKEN, "C$index", "EUR"),
        nativeSymbol = "C${index}EUR",
        pricePrecision = 2,
    )

    /** Symbols of a subscribe frame, so batching can be asserted on the real payload. */
    private fun symbolsOf(frame: String): List<String> =
        Json.parseToJsonElement(frame)
            .jsonObject.getValue("params")
            .jsonObject.getValue("symbol")
            .jsonArray.map { it.jsonPrimitive.content }

    private suspend fun awaitServerSocket(): WebSocket = withTimeout(TIMEOUT_MS) { serverSockets.receive() }

    private suspend fun awaitServerMessage(): String = withTimeout(TIMEOUT_MS) { serverMessages.receive() }

    private suspend fun awaitServerClosing(): Int = withTimeout(TIMEOUT_MS) { serverClosings.receive() }

    private fun ohlcFrame(begin: String, close: Double) = """
        {"channel":"ohlc","type":"update","timestamp":"2026-08-22T18:15:11.309506666Z","data":[
          {"symbol":"BTC/EUR","open":65927.0,"high":65930.0,"low":65920.0,"close":$close,"trades":2,
           "volume":0.01591681,"vwap":65927.0,"interval_begin":"$begin","interval":15,
           "timestamp":"2026-08-22T18:30:00.000000Z"}]}
    """.trimIndent()

    private fun dailyFrame(begin: String, high: Double, low: Double, close: Double) = """
        {"channel":"ohlc","type":"update","timestamp":"2026-09-01T00:00:00.000000000Z","data":[
          {"symbol":"BTC/EUR","open":145.0,"high":$high,"low":$low,"close":$close,"trades":2,
           "volume":5.0,"vwap":150.0,"interval_begin":"$begin","interval":1440,
           "timestamp":"2026-09-02T00:00:00.000000Z"}]}
    """.trimIndent()

    private companion object {
        val TIMEOUT = 20.seconds
        const val TIMEOUT_MS = 20_000L

        /** Long enough that no ping interleaves with the frames a test asserts on. */
        const val QUIET_PING_MS = 60_000L
        const val FAST_PING_MS = 100L

        /** WebSocket close code for a clean shutdown. */
        const val NORMAL_CLOSURE = 1000

        const val FIRST_BUCKET = "2026-08-22T18:15:00.000000000Z"
        const val SECOND_BUCKET = "2026-08-22T18:30:00.000000000Z"

        const val AUGUST_3 = "2026-08-03T00:00:00.000000000Z"
        const val SEPTEMBER_1 = "2026-09-01T00:00:00.000000000Z"
        const val AUGUST_1_SECONDS = 1785542400L
        const val SEPTEMBER_1_SECONDS = 1788220800L

        /** Four daily bars ending 2 Aug 2026, i.e. two days of the month that is still forming. */
        const val DAILY_OHLC = """
        {"error":[],"result":{"XXBTZEUR":[
          [1785369600,"100.0","110.0","95.0","105.0","100.0","1.0",10],
          [1785456000,"105.0","130.0","100.0","120.0","110.0","2.0",20],
          [1785542400,"121.0","140.0","118.0","135.0","130.0","3.0",30],
          [1785628800,"135.0","150.0","130.0","145.0","140.0","4.0",40]
        ],"last":1785628800}}
        """

        val BTC_EUR = Market(
            key = MarketKey.of(ExchangeId.KRAKEN, "BTC", "EUR"),
            nativeSymbol = "XXBTZEUR",
            pricePrecision = 1,
            tickSize = 0.1,
        )

        const val TICKER_FRAME = """
        {"channel":"ticker","type":"update","data":[
          {"symbol":"BTC/EUR","bid":65926.9,"bid_qty":0.10900000,"ask":65927.0,"ask_qty":0.05618531,
           "last":65927.0,"volume":627.85684450,"vwap":66266.2,"low":65160.5,"high":67389.2,
           "change":-243.3,"change_pct":-0.37,"trades":24757,"timestamp":"2026-08-22T18:15:11.309506Z"}]}
        """

        const val STATUS_FRAME = """
        {"channel":"status","type":"update","data":[
          {"api_version":"v2","connection_id":12345,"system":"online","version":"2.0.10"}]}
        """

        const val HEARTBEAT_FRAME = """{"channel":"heartbeat"}"""

        const val SUBSCRIBE_ACK = """
        {"method":"subscribe","req_id":1,"result":{"channel":"ticker","event_trigger":"trades",
         "snapshot":true,"symbol":"BTC/EUR"},"success":true,"time_in":"2026-08-22T18:15:10.000000Z",
         "time_out":"2026-08-22T18:15:10.000100Z"}
        """

        const val REJECTION_FRAME = """
        {"error":"Currency pair not supported XBT/EUR","method":"subscribe","req_id":9,
         "success":false,"symbol":"XBT/EUR","time_in":"2026-08-22T18:15:10.000000Z",
         "time_out":"2026-08-22T18:15:10.000100Z"}
        """
    }
}
