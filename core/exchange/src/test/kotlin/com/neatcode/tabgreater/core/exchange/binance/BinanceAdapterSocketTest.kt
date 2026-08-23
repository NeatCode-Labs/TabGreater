package com.neatcode.tabgreater.core.exchange.binance

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
 * Drives the combined stream endpoint against MockWebServer's WebSocket support:
 * subscribe on collect, map frames, unsubscribe on cancel, close when the last collector leaves.
 */
class BinanceAdapterSocketTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var scope: CoroutineScope
    private lateinit var adapter: BinanceAdapter

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
        adapter = BinanceAdapter(
            client = client,
            scope = scope,
            restBase = server.url("/").toString(),
            wsBase = "ws://${server.hostName}:${server.port}",
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
    fun `watchTickers subscribes, maps mini ticker frames and closes the socket on cancel`() = runBlocking {
        adapter.watchTickers(listOf(BTC_EUR)).test(timeout = TIMEOUT) {
            val socket = awaitServerSocket()
            val subscribe = awaitServerMessage()
            assertTrue(subscribe, subscribe.contains("\"method\":\"SUBSCRIBE\""))
            assertTrue(subscribe, subscribe.contains("\"btceur@miniTicker\""))
            assertTrue(subscribe, subscribe.contains("\"btceur@bookTicker\""))

            socket.send(MINI_TICKER_FRAME)
            val ticker: Ticker = awaitItem()
            assertEquals(BTC_EUR.key, ticker.key)
            assertNull(ticker.bid)
            assertNull(ticker.ask)
            assertEquals(65609.70, ticker.last, 1e-9)
            assertEquals(61600.0, ticker.open24h!!, 1e-9)
            assertEquals(66000.0, ticker.high24h!!, 1e-9)
            assertEquals(61000.0, ticker.low24h!!, 1e-9)
            assertEquals(1234.56, ticker.volumeBase24h!!, 1e-9)
            assertEquals(8.0e7, ticker.volumeQuote24h!!, 1e-6)
            assertEquals((65609.70 - 61600.0) / 61600.0 * 100.0, ticker.changePct24h!!, 1e-9)
            assertEquals(1787415255475L, ticker.timestamp)

            cancelAndIgnoreRemainingEvents()
        }

        // The last collector leaving closes the socket outright; no UNSUBSCRIBE round trip.
        assertTrue(awaitServerClosing() > 0)
        assertTrue(serverMessages.tryReceive().isFailure)
    }

    @Test
    fun `book ticker frames fill bid and ask, are sampled and survive the next mini ticker`() = runBlocking {
        adapter.watchTickers(listOf(BTC_EUR)).test(timeout = TIMEOUT) {
            val socket = awaitServerSocket()
            awaitServerMessage()

            // A quote before the first miniTicker has no price to attach to and is dropped.
            socket.send(bookFrame(bid = "65609.60", ask = "65609.80"))
            socket.send(MINI_TICKER_FRAME)
            val first = awaitItem()
            assertEquals(65609.70, first.last, 1e-9)
            assertNull(first.bid)

            socket.send(bookFrame(bid = "65609.60", ask = "65609.80"))
            val quoted = awaitItem()
            assertEquals(65609.60, quoted.bid!!, 1e-9)
            assertEquals(65609.80, quoted.ask!!, 1e-9)
            // The whole snapshot survives: a quote-only Ticker would wipe the live repository's row.
            assertEquals(65609.70, quoted.last, 1e-9)
            assertEquals(66000.0, quoted.high24h!!, 1e-9)

            // Within the one-second sampling window the next quote is swallowed, so the next item
            // is the following miniTicker — which still carries the last known quote.
            socket.send(bookFrame(bid = "65700.00", ask = "65700.10"))
            socket.send(MINI_TICKER_FRAME)
            val carried = awaitItem()
            assertEquals(65609.70, carried.last, 1e-9)
            assertEquals(65609.60, carried.bid!!, 1e-9)
            assertEquals(65609.80, carried.ask!!, 1e-9)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `watchKlines maps forming and closed bars`() = runBlocking {
        adapter.watchKlines(BTC_EUR, Timeframe.H1).test(timeout = TIMEOUT) {
            val socket = awaitServerSocket()
            val subscribe = awaitServerMessage()
            assertTrue(subscribe, subscribe.contains("\"btceur@kline_1h\""))

            socket.send(klineFrame(closed = false))
            val forming: Candle = awaitItem()
            assertEquals(1787410800000L, forming.openTime)
            assertEquals(65780.26, forming.open, 1e-9)
            assertEquals(65853.71, forming.high, 1e-9)
            assertEquals(65638.78, forming.low, 1e-9)
            assertEquals(65679.93, forming.close, 1e-9)
            assertEquals(5.678, forming.volume, 1e-9)
            assertTrue(!forming.closed)

            socket.send(klineFrame(closed = true))
            assertTrue(awaitItem().closed)

            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(awaitServerClosing() > 0)
    }

    @Test
    fun `ticker and kline collectors share one socket that closes with the last of them`() = runBlocking {
        val tickers = Channel<Ticker>(Channel.UNLIMITED)
        val candles = Channel<Candle>(Channel.UNLIMITED)
        val tickerJob = scope.launch { adapter.watchTickers(listOf(BTC_EUR)).collect { tickers.send(it) } }
        val klineJob = scope.launch {
            adapter.watchKlines(BTC_EUR, Timeframe.H1).collect { candles.send(it) }
        }

        val socket = awaitServerSocket()
        // Both subscriptions are issued within the coalescing window, so they share one frame.
        val subscribe = awaitServerMessage()
        assertTrue(subscribe, subscribe.contains("\"method\":\"SUBSCRIBE\""))
        assertTrue(subscribe, subscribe.contains("btceur@miniTicker"))
        assertTrue(subscribe, subscribe.contains("btceur@kline_1h"))

        socket.send(MINI_TICKER_FRAME)
        socket.send(klineFrame(closed = true))
        assertEquals(BTC_EUR.key, withTimeout(TIMEOUT_MS) { tickers.receive() }.key)
        assertEquals(1787410800000L, withTimeout(TIMEOUT_MS) { candles.receive() }.openTime)

        // Only one upgrade request: both flows ride the same socket.
        assertEquals(1, server.requestCount)

        tickerJob.cancelAndJoin()
        val unsubscribe = awaitServerMessage()
        assertTrue(unsubscribe, unsubscribe.contains("\"method\":\"UNSUBSCRIBE\""))
        assertTrue(unsubscribe, unsubscribe.contains("btceur@miniTicker"))
        assertTrue(unsubscribe, !unsubscribe.contains("btceur@kline_1h"))
        assertTrue(
            "socket must stay open while the kline collector is alive",
            serverClosings.tryReceive().isFailure,
        )

        klineJob.cancelAndJoin()
        assertTrue(awaitServerClosing() > 0)
    }

    private suspend fun awaitServerSocket(): WebSocket = withTimeout(TIMEOUT_MS) { serverSockets.receive() }

    private suspend fun awaitServerMessage(): String = withTimeout(TIMEOUT_MS) { serverMessages.receive() }

    private suspend fun awaitServerClosing(): Int = withTimeout(TIMEOUT_MS) { serverClosings.receive() }

    private fun bookFrame(bid: String, ask: String) = """
        {"stream":"btceur@bookTicker","data":{"u":400900217,"s":"BTCEUR",
         "b":"$bid","B":"1.10000000","a":"$ask","A":"2.20000000"}}
    """.trimIndent()

    private fun klineFrame(closed: Boolean) = """
        {"stream":"btceur@kline_1h","data":{"e":"kline","E":1787415255475,"s":"BTCEUR",
         "k":{"t":1787410800000,"T":1787414399999,"s":"BTCEUR","i":"1h","f":100,"L":200,
              "o":"65780.26000000","c":"65679.93000000","h":"65853.71000000","l":"65638.78000000",
              "v":"5.67800000","n":1291,"x":$closed,"q":"373268.61001630"}}}
    """.trimIndent()

    private companion object {
        val TIMEOUT = 20.seconds
        const val TIMEOUT_MS = 20_000L

        val BTC_EUR = Market(
            key = MarketKey.of(ExchangeId.BINANCE, "BTC", "EUR"),
            nativeSymbol = "BTCEUR",
            pricePrecision = 2,
            tickSize = 0.01,
        )

        const val MINI_TICKER_FRAME = """
        {"stream":"btceur@miniTicker","data":{"e":"24hrMiniTicker","E":1787415255475,"s":"BTCEUR",
         "c":"65609.70000000","o":"61600.00000000","h":"66000.00000000","l":"61000.00000000",
         "v":"1234.56000000","q":"80000000.00000000"}}
        """
    }
}
