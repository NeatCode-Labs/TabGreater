package com.neatcode.tabgreater.core.exchange.kraken

import com.neatcode.tabgreater.core.exchange.ExchangeHttpException
import com.neatcode.tabgreater.core.exchange.ExchangeUnavailableException
import com.neatcode.tabgreater.core.exchange.ratelimit.TokenBucket
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.Timeframe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KrakenAdapterRestTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var scope: CoroutineScope
    private lateinit var adapter: KrakenAdapter

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        adapter = KrakenAdapter(
            client = client,
            scope = scope,
            restBase = server.url("/").toString(),
            wsBase = "ws://${server.hostName}:${server.port}",
            // The production bucket paces requests at 1/s; tests would spend that second waiting.
            restBucket = TokenBucket(capacity = 64.0, refillPerSecond = 10_000.0),
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
    fun `listMarkets aliases XBT and XDG, keeps only online pairs and reads Kraken's precision`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(ASSET_PAIRS).build())

        val markets = adapter.listMarkets()

        assertEquals(
            listOf("kraken:BTC/EUR", "kraken:ETH/BTC", "kraken:DOGE/EUR", "kraken:ADA/EUR"),
            markets.map { it.key.value },
        )
        val btc = markets.first { it.key.value == "kraken:BTC/EUR" }
        // The irregular REST pair id is what every REST call needs, so it is the native symbol.
        assertEquals("XXBTZEUR", btc.nativeSymbol)
        assertEquals(1, btc.pricePrecision)
        assertEquals(0.1, btc.tickSize!!, 1e-12)
        val doge = markets.first { it.key.value == "kraken:DOGE/EUR" }
        assertEquals("XDGEUR", doge.nativeSymbol)
        assertEquals(7, doge.pricePrecision)
        assertEquals(1e-7, doge.tickSize!!, 1e-15)

        val request = server.takeRequest()
        assertEquals("/0/public/AssetPairs", request.url.encodedPath)
        assertNull(request.url.query)
    }

    @Test
    fun `fetchTickers asks for the native pair ids and maps the 24 hour columns`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(TICKERS).build())

        val tickers = adapter.fetchTickers(
            listOf(market("XXBTZEUR", "BTC", "EUR"), market("ADAEUR", "ADA", "EUR")),
        )

        assertEquals("XXBTZEUR,ADAEUR", server.takeRequest().url.queryParameter("pair"))
        val btc = tickers.first { it.key == MarketKey.of(ExchangeId.KRAKEN, "BTC", "EUR") }
        assertEquals(65908.90, btc.last, 1e-9)
        assertEquals(65926.10, btc.bid!!, 1e-9)
        assertEquals(65926.20, btc.ask!!, 1e-9)
        // The second column of every array is the rolling 24 h value; the first is "today".
        assertEquals(67389.20, btc.high24h!!, 1e-9)
        assertEquals(65160.50, btc.low24h!!, 1e-9)
        assertEquals(627.83444338, btc.volumeBase24h!!, 1e-9)
        assertEquals(627.83444338 * 66266.23772, btc.volumeQuote24h!!, 1e-6)
        // REST only knows today's open (since 00:00 UTC), which is not a 24 h figure: left null so
        // callers fall back to the candle window until the v2 stream delivers the rolling change.
        assertNull(btc.open24h)
        assertNull(btc.changePct24h)
        assertEquals(2, tickers.size)
    }

    @Test
    fun `fetchTickers splits markets into chunks of one hundred`() = runTest {
        repeat(2) { server.enqueue(MockResponse.Builder().code(200).body(EMPTY_RESULT).build()) }
        val markets = (1..150).map { market("C${it}EUR", "C$it", "EUR") }

        assertTrue(adapter.fetchTickers(markets).isEmpty())

        assertEquals(2, server.requestCount)
        val chunkSizes = (1..2).map { server.takeRequest() }.map { request ->
            assertEquals("/0/public/Ticker", request.url.encodedPath)
            request.url.queryParameter("pair")!!.split(",").size
        }
        assertEquals(listOf(100, 50), chunkSizes)
    }

    @Test
    fun `fetchTickers reports a zero open as unknown change`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(ZERO_OPEN_TICKER).build())

        val ticker = adapter.fetchTickers(listOf(market("NEWEUR", "NEW", "EUR"))).single()

        assertNull(ticker.changePct24h)
    }

    @Test
    fun `fetchOHLCV parses bars oldest first and forms everything after the last committed bar`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(ohlc(last = SECOND_BAR_SECONDS)).build())

        val candles = adapter.fetchOHLCV(market("XXBTZEUR", "BTC", "EUR"), Timeframe.M15, null, 720)

        assertEquals(3, candles.size)
        assertEquals(listOf(1786773600000L, 1786774500000L, 1786775400000L), candles.map { it.openTime })
        val first = candles[0]
        assertEquals(54495.7, first.open, 1e-9)
        assertEquals(54499.9, first.high, 1e-9)
        assertEquals(54469.8, first.low, 1e-9)
        assertEquals(54469.8, first.close, 1e-9)
        assertEquals(0.49378870, first.volume, 1e-9)
        assertEquals(listOf(true, true, false), candles.map { it.closed })

        val request = server.takeRequest()
        assertEquals("/0/public/OHLC", request.url.encodedPath)
        assertEquals("XXBTZEUR", request.url.queryParameter("pair"))
        assertEquals("15", request.url.queryParameter("interval"))
        // `since` only trims the head of a window Kraken caps at 720 bars anyway.
        assertNull(request.url.queryParameter("since"))
    }

    @Test
    fun `fetchOHLCV without a last field treats only the newest bar as forming`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(ohlc(last = null)).build())

        val candles = adapter.fetchOHLCV(market("XXBTZEUR", "BTC", "EUR"), Timeframe.M15, null, 720)

        // Calling every bar closed would cache a half-built one for good, so the newest still forms.
        assertEquals(listOf(true, true, false), candles.map { it.closed })
    }

    @Test
    fun `fetchOHLCV keeps the newest bars up to the limit`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(ohlc(last = THIRD_BAR_SECONDS)).build())

        val candles = adapter.fetchOHLCV(market("XXBTZEUR", "BTC", "EUR"), Timeframe.M15, null, 2)

        assertEquals(listOf(1786774500000L, 1786775400000L), candles.map { it.openTime })
    }

    @Test
    fun `fetchOHLCV drops bars at or after endTime`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(ohlc(last = THIRD_BAR_SECONDS)).build())

        val candles = adapter.fetchOHLCV(
            market = market("XXBTZEUR", "BTC", "EUR"),
            timeframe = Timeframe.M15,
            endTime = 1786775400000L,
            limit = 5000,
        )

        assertEquals(listOf(1786773600000L, 1786774500000L), candles.map { it.openTime })
    }

    @Test
    fun `fetchOHLCV builds monthly bars from daily ones`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(DAILY_OHLC).build())

        val candles = adapter.fetchOHLCV(market("XXBTZEUR", "BTC", "EUR"), Timeframe.MN1, null, 5)

        assertEquals("1440", server.takeRequest().url.queryParameter("interval"))
        assertEquals(2, candles.size)
        val july = candles[0]
        assertEquals(JULY_2026_SECONDS * 1000, july.openTime)
        assertEquals(100.0, july.open, 1e-9)
        assertEquals(130.0, july.high, 1e-9)
        assertEquals(95.0, july.low, 1e-9)
        assertEquals(120.0, july.close, 1e-9)
        assertEquals(3.0, july.volume, 1e-9)
        // The month is only covered from the 30th onwards, so it is reported as still forming.
        assertTrue(!july.closed)
        val august = candles[1]
        assertEquals(AUGUST_2026_SECONDS * 1000, august.openTime)
        assertEquals(121.0, august.open, 1e-9)
        assertEquals(150.0, august.high, 1e-9)
        assertEquals(145.0, august.close, 1e-9)
        assertTrue(!august.closed)
    }

    @Test
    fun `a kraken error array with http 200 becomes an http exception`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(UNKNOWN_PAIR_ERROR).build())

        val error = runCatching { adapter.listMarkets() }.exceptionOrNull()

        assertTrue("expected ExchangeHttpException, got $error", error is ExchangeHttpException)
        error as ExchangeHttpException
        assertEquals(200, error.code)
        assertTrue(error.message!!, error.message!!.contains("EQuery:Unknown asset pair"))
    }

    @Test
    fun `a too many requests error array is reported as a rate limit`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(RATE_LIMIT_ERROR).build())

        val error = runCatching { adapter.listMarkets() }.exceptionOrNull()

        assertTrue("expected ExchangeHttpException, got $error", error is ExchangeHttpException)
        error as ExchangeHttpException
        // Kraken serves its throttle response with HTTP 200 and `code` always mirrors the real
        // status, so only the message says that this particular 200 was a rate limit.
        assertEquals(200, error.code)
        assertTrue(error.message!!, error.message!!.contains("rate limit"))
        assertTrue(error.message!!, error.message!!.contains("EGeneral:Too many requests"))
    }

    @Test
    fun `http 429 becomes an http exception carrying the retry hint`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(429)
                .addHeader("Retry-After", "17")
                .body("Too many requests")
                .build(),
        )

        val error = runCatching { adapter.listMarkets() }.exceptionOrNull()

        assertTrue("expected ExchangeHttpException, got $error", error is ExchangeHttpException)
        error as ExchangeHttpException
        assertEquals(429, error.code)
        assertTrue(error.message!!, error.message!!.contains("17"))
    }

    @Test
    fun `http 451 becomes an unavailable exception`() = runTest {
        server.enqueue(MockResponse.Builder().code(451).body("blocked").build())

        val error = runCatching { adapter.listMarkets() }.exceptionOrNull()

        assertTrue("expected ExchangeUnavailableException, got $error", error is ExchangeUnavailableException)
        assertEquals(ExchangeId.KRAKEN, (error as ExchangeUnavailableException).exchange)
    }

    @Test
    fun `http 500 becomes a plain http exception`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).body("EService:Unavailable").build())

        val error = runCatching { adapter.listMarkets() }.exceptionOrNull()

        assertTrue("expected ExchangeHttpException, got $error", error is ExchangeHttpException)
        assertEquals(500, (error as ExchangeHttpException).code)
    }

    private fun market(nativeSymbol: String, base: String, quote: String) = Market(
        key = MarketKey.of(ExchangeId.KRAKEN, base, quote),
        nativeSymbol = nativeSymbol,
        pricePrecision = 2,
    )

    /** Three bars; [last] is Kraken's newest committed bar, `null` renders a result without it. */
    private fun ohlc(last: Long?) = """
        {"error":[],"result":{"XXBTZEUR":[
          [$FIRST_BAR_SECONDS,"54495.7","54499.9","54469.8","54469.8","54495.4","0.49378870",238],
          [$SECOND_BAR_SECONDS,"54469.8","54600.0","54400.0","54580.1","54500.0","1.20000000",311],
          [$THIRD_BAR_SECONDS,"54580.1","54700.0","54550.0","54690.0","54600.0","0.30000000",42]
        ]${if (last == null) "" else ",\"last\":$last"}}}
    """.trimIndent()

    private companion object {
        const val FIRST_BAR_SECONDS = 1786773600L
        const val SECOND_BAR_SECONDS = 1786774500L
        const val THIRD_BAR_SECONDS = 1786775400L

        const val JULY_2026_SECONDS = 1782864000L
        const val AUGUST_2026_SECONDS = 1785542400L
        const val JULY_30_SECONDS = 1785369600L
        const val JULY_31_SECONDS = 1785456000L
        const val AUGUST_2_SECONDS = 1785628800L

        const val ASSET_PAIRS = """
        {"error":[],"result":{
          "XXBTZEUR":{"altname":"XBTEUR","wsname":"XBT/EUR","base":"XXBT","quote":"ZEUR",
            "pair_decimals":1,"cost_decimals":5,"tick_size":"0.1","status":"online"},
          "XETHXXBT":{"altname":"ETHXBT","wsname":"ETH/XBT","base":"XETH","quote":"XXBT",
            "pair_decimals":5,"tick_size":"0.00001","status":"online"},
          "XDGEUR":{"altname":"XDGEUR","wsname":"XDG/EUR","base":"XXDG","quote":"ZEUR",
            "pair_decimals":7,"tick_size":"0.0000001","status":"online"},
          "ADAEUR":{"altname":"ADAEUR","wsname":"ADA/EUR","base":"ADA","quote":"ZEUR",
            "pair_decimals":6,"tick_size":"0.000001","status":"online"},
          "HALTEUR":{"altname":"HALTEUR","wsname":"HALT/EUR","base":"HALT","quote":"ZEUR",
            "pair_decimals":2,"tick_size":"0.01","status":"cancel_only"},
          "POSTEUR":{"altname":"POSTEUR","wsname":"POST/EUR","base":"POST","quote":"ZEUR",
            "pair_decimals":2,"tick_size":"0.01","status":"post_only"},
          "WEIRDEUR":{"altname":"WEIRDEUR","wsname":"WE.IRD/EUR","base":"WE.IRD","quote":"ZEUR",
            "pair_decimals":2,"tick_size":"0.01","status":"online"},
          "NOWSEUR":{"altname":"NOWSEUR","base":"NOWS","quote":"ZEUR",
            "pair_decimals":2,"tick_size":"0.01","status":"online"}
        }}
        """

        const val TICKERS = """
        {"error":[],"result":{
          "ADAEUR":{"a":["0.194774","2194","2194.000"],"b":["0.194740","2764","2764.000"],
            "c":["0.194641","39.01626300"],"v":["17779793.57647459","21913185.11525931"],
            "p":["0.198829","0.197455"],"t":[10890,13292],"l":["0.176803","0.176803"],
            "h":["0.221047","0.221047"],"o":"0.195867"},
          "XXBTZEUR":{"a":["65926.20000","1","1.000"],"b":["65926.10000","1","1.000"],
            "c":["65908.90000","0.02077666"],"v":["452.78380117","627.83444338"],
            "p":["66153.82909","66266.23772"],"t":[17399,24763],"l":["65160.50000","65160.50000"],
            "h":["67389.20000","67389.20000"],"o":"66997.30000"}
        }}
        """

        const val ZERO_OPEN_TICKER = """
        {"error":[],"result":{"NEWEUR":{"a":["1.0","1","1.000"],"b":["1.0","1","1.000"],
          "c":["1.0","10.0"],"v":["10.0","10.0"],"p":["1.0","1.0"],"t":[1,1],
          "l":["1.0","1.0"],"h":["1.0","1.0"],"o":"0.00000000"}}}
        """

        const val EMPTY_RESULT = """{"error":[],"result":{}}"""

        const val UNKNOWN_PAIR_ERROR = """{"error":["EQuery:Unknown asset pair"]}"""

        const val RATE_LIMIT_ERROR = """{"error":["EGeneral:Too many requests"]}"""

        val DAILY_OHLC = """
        {"error":[],"result":{"XXBTZEUR":[
          [$JULY_30_SECONDS,"100.0","110.0","95.0","105.0","100.0","1.0",10],
          [$JULY_31_SECONDS,"105.0","130.0","100.0","120.0","110.0","2.0",20],
          [$AUGUST_2026_SECONDS,"121.0","140.0","118.0","135.0","130.0","3.0",30],
          [$AUGUST_2_SECONDS,"135.0","150.0","130.0","145.0","140.0","4.0",40]
        ],"last":$AUGUST_2026_SECONDS}}
        """.trimIndent()
    }
}
