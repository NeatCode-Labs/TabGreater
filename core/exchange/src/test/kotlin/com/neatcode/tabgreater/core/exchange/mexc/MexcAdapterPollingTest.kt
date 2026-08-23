package com.neatcode.tabgreater.core.exchange.mexc

import app.cash.turbine.test
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.Timeframe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

/**
 * MEXC has no (JSON) WebSocket, so live data is REST polling: one shared ticker loop for all
 * collectors, one loop per kline flow. The MockWebServer dispatcher answers by path and hands out a
 * unique price per response, which is what makes loop sharing observable.
 */
class MexcAdapterPollingTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var scope: CoroutineScope
    private lateinit var adapter: MexcAdapter

    private val requests = ConcurrentLinkedQueue<RecordedRequest>()
    private val priceCounter = AtomicInteger(0)
    private val failuresLeft = AtomicInteger(0)

    /** Symbols that answer HTTP 400 forever, like a market delisted since the last `listMarkets`. */
    private val deadSymbols = CopyOnWriteArraySet<String>()

    private val pollDispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            requests += request
            if (failuresLeft.getAndUpdate { if (it > 0) it - 1 else 0 } > 0) {
                return MockResponse.Builder().code(500).body("upstream is having a bad day").build()
            }
            return when (request.url.encodedPath) {
                "/api/v3/ticker/24hr" -> {
                    val symbol = request.url.queryParameter("symbol")
                    if (symbol != null && symbol in deadSymbols) {
                        return MockResponse.Builder().code(400).body(INVALID_SYMBOL_BODY).build()
                    }
                    val body = if (symbol == null) allTickersBody() else tickerBody(symbol)
                    MockResponse.Builder().code(200).body(body).build()
                }

                "/api/v3/klines" -> MockResponse.Builder().code(200).body(klinesBody()).build()
                else -> MockResponse.Builder().code(404).body("""{"msg":"not found","code":-1121}""").build()
            }
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = pollDispatcher
        server.start()
        client = OkHttpClient()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        adapter = MexcAdapter(
            client = client,
            scope = scope,
            restBase = server.url("/").toString(),
            tickerPollMs = POLL_MS,
            klinePollMs = POLL_MS,
            klineStartJitterMs = 0,
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
    fun `watchTickers polls every subscribed market and emits their tickers`() = runBlocking {
        adapter.watchTickers(listOf(BTC_USDT, ETH_USDT)).test(timeout = TIMEOUT) {
            val first = awaitItem()
            val second = awaitItem()
            assertEquals(setOf(BTC_USDT.key, ETH_USDT.key), setOf(first.key, second.key))
            assertTrue("$first", first.last > PRICE_BASE)
            assertEquals(OPEN_PRICE, first.open24h!!, 1e-9)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(setOf("BTCUSDT", "ETHUSDT"), requests.mapNotNull { it.url.queryParameter("symbol") }.toSet())
        assertTrue(requests.all { it.url.encodedPath == "/api/v3/ticker/24hr" })
    }

    @Test
    fun `watchTickers of many markets polls the whole market in one request per tick`() = runBlocking {
        val markets = listOf(BTC_USDT, ETH_USDT) + (1..20).map {
            Market(MarketKey.of(ExchangeId.MEXC, "C$it", "USDT"), "C${it}USDT", pricePrecision = 4)
        }

        adapter.watchTickers(markets).test(timeout = TIMEOUT) {
            val keys = setOf(awaitItem().key, awaitItem().key)
            assertEquals(setOf(BTC_USDT.key, ETH_USDT.key), keys)
            cancelAndIgnoreRemainingEvents()
        }

        // Above the threshold MEXC's per-symbol calls are replaced by the single weight-40 call.
        assertTrue(requests.isNotEmpty())
        for (request in requests) assertNull(request.url.queryParameter("symbol"))
    }

    @Test
    fun `each collector receives only the markets it subscribed to`() = runBlocking {
        // The shared loop publishes one snapshot per tick for the union of both collectors, so the
        // per-flow filter is the only thing keeping ETH's prices out of the BTC collector.
        val btc = ConcurrentLinkedQueue<MarketKey>()
        val eth = ConcurrentLinkedQueue<MarketKey>()
        val btcJob = scope.launch { adapter.watchTickers(listOf(BTC_USDT)).collect { btc += it.key } }
        val ethJob = scope.launch { adapter.watchTickers(listOf(ETH_USDT)).collect { eth += it.key } }

        withTimeout(TIMEOUT_MS) {
            while (btc.size < SHARED_TICKS || eth.size < SHARED_TICKS) delay(POLL_STEP_MS)
        }
        btcJob.cancelAndJoin()
        ethJob.cancelAndJoin()

        assertEquals(setOf(BTC_USDT.key), btc.toSet())
        assertEquals(setOf(ETH_USDT.key), eth.toSet())
    }

    @Test
    fun `watchTickers of nothing polls nothing and keeps the flow open`() = runBlocking {
        // The live repository reads a completed flow as a dropped feed, so an empty market list has
        // to park on awaitCancellation instead of returning — this is the branch that encodes that
        // contract (Turbine reports a completion as an event, so expectNoEvents also pins it).
        adapter.watchTickers(emptyList()).test(timeout = TIMEOUT) {
            delay(SETTLE_MS)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(0, requests.size)
    }

    @Test
    fun `a collector joining as another leaves keeps the shared loop alive`() = runBlocking {
        // Regression guard for the hand-over race: while BTC's collector is being cancelled, ETH's
        // arrives, so the union is briefly read while the book is being rewritten. The surviving
        // collector must keep receiving ticks.
        val eth = ConcurrentLinkedQueue<Double>()
        repeat(HANDOVER_ROUNDS) {
            val btcJob = scope.launch { adapter.watchTickers(listOf(BTC_USDT)).collect { } }
            withTimeout(TIMEOUT_MS) { while (requests.isEmpty()) delay(POLL_STEP_MS) }
            val ethJob = scope.launch { adapter.watchTickers(listOf(ETH_USDT)).collect { eth += it.last } }
            btcJob.cancel()
            withTimeout(TIMEOUT_MS) { while (eth.isEmpty()) delay(POLL_STEP_MS) }
            val before = eth.size
            // The loop has to survive the hand-over, not just deliver the first tick.
            withTimeout(TIMEOUT_MS) { while (eth.size < before + SHARED_TICKS) delay(POLL_STEP_MS) }
            ethJob.cancelAndJoin()
            eth.clear()
            requests.clear()
            delay(SETTLE_MS)
        }
    }

    @Test
    fun `two collectors share one poll loop`() = runBlocking {
        val first = ConcurrentLinkedQueue<Double>()
        val second = ConcurrentLinkedQueue<Double>()
        val startNs = System.nanoTime()
        val firstJob = scope.launch { adapter.watchTickers(listOf(BTC_USDT)).collect { first += it.last } }
        val secondJob = scope.launch { adapter.watchTickers(listOf(BTC_USDT)).collect { second += it.last } }

        withTimeout(TIMEOUT_MS) {
            while (first.size < SHARED_TICKS || second.size < SHARED_TICKS) delay(POLL_STEP_MS)
        }
        firstJob.cancelAndJoin()
        secondJob.cancelAndJoin()
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000

        // Every response carries its own price, so seeing the same value twice is only possible if
        // both flows are fed by one loop.
        assertTrue("$first vs $second", first.toSet().intersect(second.toSet()).isNotEmpty())
        // Both collectors receive every broadcast either way, so only requests per unit of *time*
        // discriminate: one loop polls once per tickerPollMs, a loop per collector twice.
        val maxRequests = elapsedMs / POLL_MS + 2
        assertTrue("${requests.size} requests in $elapsedMs ms", requests.size <= maxRequests)
    }

    @Test
    fun `the shared loop drops a departed collector's market and keeps polling the rest`() = runBlocking {
        val btc = ConcurrentLinkedQueue<Double>()
        val eth = ConcurrentLinkedQueue<Double>()
        val btcJob = scope.launch { adapter.watchTickers(listOf(BTC_USDT)).collect { btc += it.last } }
        val ethJob = scope.launch { adapter.watchTickers(listOf(ETH_USDT)).collect { eth += it.last } }

        withTimeout(TIMEOUT_MS) { while (btc.isEmpty() || eth.isEmpty()) delay(POLL_STEP_MS) }
        btcJob.cancelAndJoin()
        // Let the tick that was already in flight finish before the recording starts.
        delay(SETTLE_MS)
        requests.clear()
        val ethBefore = eth.size

        withTimeout(TIMEOUT_MS) { while (eth.size < ethBefore + SHARED_TICKS) delay(POLL_STEP_MS) }
        ethJob.cancelAndJoin()

        // The union shrank to the surviving market; the loop itself stayed alive.
        assertEquals(
            setOf("ETHUSDT"),
            requests.mapNotNull { it.url.queryParameter("symbol") }.toSet(),
        )
        assertTrue("$eth", eth.size >= ethBefore + SHARED_TICKS)
    }

    @Test
    fun `one permanently invalid symbol does not stop the tickers of the others`() = runBlocking {
        deadSymbols += "ETHUSDT"

        adapter.watchTickers(listOf(BTC_USDT, ETH_USDT)).test(timeout = TIMEOUT) {
            // Three ticks in a row: a symbol that answers 400 forever must not freeze the loop.
            repeat(SHARED_TICKS) { assertEquals(BTC_USDT.key, awaitItem().key) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the poll loop stops when the last collector leaves`() = runBlocking {
        val job = scope.launch { adapter.watchTickers(listOf(BTC_USDT)).collect { } }
        withTimeout(TIMEOUT_MS) { while (requests.size < 2) delay(POLL_STEP_MS) }

        job.cancelAndJoin()
        delay(SETTLE_MS)
        val afterCancel = requests.size
        delay(POLL_MS * 4)

        assertEquals("polling continued after the last collector left", afterCancel, requests.size)
    }

    @Test
    fun `a failing tick does not end the ticker flow`() = runBlocking {
        failuresLeft.set(1)

        adapter.watchTickers(listOf(BTC_USDT)).test(timeout = TIMEOUT) {
            // The first tick answers HTTP 500; the flow must survive it and emit on the next one.
            val ticker = awaitItem()
            assertEquals(BTC_USDT.key, ticker.key)
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue("${requests.size}", requests.size >= 2)
    }

    @Test
    fun `watchKlines polls two bars per tick and flags the forming one`() = runBlocking {
        adapter.watchKlines(BTC_USDT, Timeframe.M15).test(timeout = TIMEOUT) {
            val closed = awaitItem()
            val forming = awaitItem()
            assertEquals(1787421600000L, closed.openTime)
            assertEquals(77276.41, closed.open, 1e-9)
            assertEquals(77232.81, closed.close, 1e-9)
            assertTrue(closed.closed)
            assertEquals(1787422500000L, forming.openTime)
            assertTrue(!forming.closed)
            cancelAndIgnoreRemainingEvents()
        }

        val request = requests.first()
        assertEquals("/api/v3/klines", request.url.encodedPath)
        assertEquals("BTCUSDT", request.url.queryParameter("symbol"))
        assertEquals("15m", request.url.queryParameter("interval"))
        assertEquals("2", request.url.queryParameter("limit"))
        assertNull(request.url.queryParameter("endTime"))
    }

    @Test
    fun `a failing tick does not end the kline flow`() = runBlocking {
        failuresLeft.set(1)

        adapter.watchKlines(BTC_USDT, Timeframe.M15).test(timeout = TIMEOUT) {
            assertEquals(1787421600000L, awaitItem().openTime)
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue("${requests.size}", requests.size >= 2)
    }

    private fun tickerBody(symbol: String): String {
        val price = PRICE_BASE + priceCounter.incrementAndGet()
        return """
        {"symbol":"$symbol","priceChange":"-104.79","priceChangePercent":"-0.0013","prevClosePrice":"$OPEN_PRICE",
         "lastPrice":"$price","bidPrice":"$price","askPrice":"$price","openPrice":"$OPEN_PRICE",
         "highPrice":"99999.00","lowPrice":"1.00","volume":"10178.86793886","quoteVolume":"789395183.33",
         "openTime":1787422971044,"closeTime":1787422987340,"count":null}
        """.trimIndent()
    }

    private fun allTickersBody(): String = "[${tickerBody("BTCUSDT")},${tickerBody("ETHUSDT")}]"

    private fun klinesBody(): String {
        val now = System.currentTimeMillis()
        return """
        [
          [1787421600000,"77276.41","77356.22","77211.21","77232.81","42.2334848",${now - 900_000},"3264261.78"],
          [1787422500000,"77232.81","77400.00","77100.00","77350.10","12.3456789",${now + 900_000},"954321.00"]
        ]
        """.trimIndent()
    }

    private companion object {
        val TIMEOUT = 20.seconds
        const val TIMEOUT_MS = 20_000L
        const val POLL_MS = 200L
        const val POLL_STEP_MS = 20L
        const val SETTLE_MS = 300L

        /** Items each collector must see before the sharing assertions are meaningful. */
        const val SHARED_TICKS = 3

        /** Hand-overs the race guard replays; the window it targets is only a few nanoseconds wide. */
        const val HANDOVER_ROUNDS = 3

        const val PRICE_BASE = 1000.0
        const val OPEN_PRICE = 900.0

        const val INVALID_SYMBOL_BODY = """{"msg":"invalid symbol","code":-1121}"""

        val BTC_USDT = Market(
            key = MarketKey.of(ExchangeId.MEXC, "BTC", "USDT"),
            nativeSymbol = "BTCUSDT",
            pricePrecision = 2,
        )

        val ETH_USDT = Market(
            key = MarketKey.of(ExchangeId.MEXC, "ETH", "USDT"),
            nativeSymbol = "ETHUSDT",
            pricePrecision = 2,
        )
    }
}
