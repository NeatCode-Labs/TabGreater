package com.neatcode.tabgreater.core.exchange.mexc

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

class MexcAdapterRestTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var scope: CoroutineScope
    private lateinit var adapter: MexcAdapter

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        adapter = MexcAdapter(client = client, scope = scope, restBase = server.url("/").toString())
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    @Test
    fun `listMarkets keeps enabled spot symbols and takes the precision from quotePrecision`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(EXCHANGE_INFO).build())

        val markets = adapter.listMarkets()

        assertEquals(
            listOf("mexc:BTC/USDT", "mexc:ETH/BTC", "mexc:PEPE/USDT"),
            markets.map { it.key.value },
        )
        val btc = markets.first { it.nativeSymbol == "BTCUSDT" }
        assertEquals(2, btc.pricePrecision)
        // MEXC exposes no PRICE_FILTER, so there is no tick size to report.
        assertNull(btc.tickSize)
        assertEquals(6, markets.first { it.nativeSymbol == "ETHBTC" }.pricePrecision)
        assertEquals(9, markets.first { it.nativeSymbol == "PEPEUSDT" }.pricePrecision)

        val request = server.takeRequest()
        assertEquals("/api/v3/exchangeInfo", request.url.encodedPath)
        assertNull(request.url.query)
    }

    @Test
    fun `fetchTickers asks per symbol while the watchlist is small`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(BTC_TICKER).build())
        server.enqueue(MockResponse.Builder().code(200).body(ETH_TICKER).build())

        val tickers = adapter.fetchTickers(listOf(market("BTCUSDT", "BTC", "USDT"), market("ETHUSDT", "ETH", "USDT")))

        assertEquals(
            listOf(MarketKey("mexc:BTC/USDT"), MarketKey("mexc:ETH/USDT")),
            tickers.map { it.key },
        )
        assertEquals(2, server.requestCount)
        val symbols = (1..2).map { server.takeRequest() }.map { request ->
            assertEquals("/api/v3/ticker/24hr", request.url.encodedPath)
            request.url.queryParameter("symbol")
        }
        assertEquals(listOf("BTCUSDT", "ETHUSDT"), symbols)
    }

    @Test
    fun `fetchTickers maps the 24h payload and derives the change from the open price`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(BTC_TICKER).build())

        val ticker = adapter.fetchTickers(listOf(market("BTCUSDT", "BTC", "USDT"))).single()

        assertEquals(77329.99, ticker.last, 1e-9)
        assertEquals(77434.78, ticker.open24h!!, 1e-9)
        assertEquals(78821.48, ticker.high24h!!, 1e-9)
        assertEquals(76517.39, ticker.low24h!!, 1e-9)
        assertEquals(10178.86793886, ticker.volumeBase24h!!, 1e-9)
        assertEquals(789395183.33, ticker.volumeQuote24h!!, 1e-6)
        assertEquals(77324.92, ticker.bid!!, 1e-9)
        assertEquals(77324.93, ticker.ask!!, 1e-9)
        assertEquals(1787422987340L, ticker.timestamp)
        // priceChangePercent is a fraction ("-0.0013"); the signed percentage comes from the open.
        assertEquals((77329.99 - 77434.78) / 77434.78 * 100.0, ticker.changePct24h!!, 1e-9)
        assertTrue("$ticker", ticker.changePct24h!! < -0.13 && ticker.changePct24h!! > -0.14)
    }

    @Test
    fun `fetchTickers switches to the whole-market call above the threshold`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(ALL_TICKERS).build())
        val markets = listOf(market("BTCUSDT", "BTC", "USDT"), market("ETHUSDT", "ETH", "USDT")) +
            (1..20).map { market("C${it}USDT", "C$it", "USDT") }

        val tickers = adapter.fetchTickers(markets)

        // One weight-40 request for everything; symbols nobody asked for are dropped locally.
        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("/api/v3/ticker/24hr", request.url.encodedPath)
        assertNull(request.url.query)
        assertEquals(listOf(MarketKey("mexc:BTC/USDT"), MarketKey("mexc:ETH/USDT")), tickers.map { it.key })
    }

    @Test
    fun `fetchTickers still asks per symbol at exactly the threshold`() = runTest {
        // The strategy switch is `markets.size <= ALL_TICKERS_THRESHOLD`; this pins the boundary so
        // that flipping the comparison changes the request weight profile *and* fails the suite.
        val markets = (1..MexcAdapter.ALL_TICKERS_THRESHOLD).map { market("C${it}USDT", "C$it", "USDT") }
        for (m in markets) server.enqueue(MockResponse.Builder().code(200).body(tickerFor(m.nativeSymbol)).build())

        val tickers = adapter.fetchTickers(markets)

        assertEquals(markets.size, tickers.size)
        assertEquals(MexcAdapter.ALL_TICKERS_THRESHOLD, server.requestCount)
        val symbols = markets.map { server.takeRequest().url.queryParameter("symbol") }
        assertEquals(markets.map { it.nativeSymbol }, symbols)
    }

    @Test
    fun `fetchTickers takes the whole-market call one market above the threshold`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(ALL_TICKERS).build())
        val markets = (1..MexcAdapter.ALL_TICKERS_THRESHOLD + 1).map { market("C${it}USDT", "C$it", "USDT") }

        adapter.fetchTickers(markets)

        assertEquals(1, server.requestCount)
        assertNull(server.takeRequest().url.query)
    }

    @Test
    fun `fetchTickers rethrows a throttle instead of returning a partial round`() = runTest {
        // Swallowing a 429 like an `invalid symbol` would hand the poll loop a healthy-looking
        // partial list and skip the backoff, so the round aborts on the first throttled symbol.
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "3").body(THROTTLED_BODY).build())
        server.enqueue(MockResponse.Builder().code(200).body(BTC_TICKER).build())

        val error = runCatching {
            adapter.fetchTickers(
                listOf(market("ETHUSDT", "ETH", "USDT"), market("BTCUSDT", "BTC", "USDT")),
            )
        }.exceptionOrNull()

        assertTrue("expected ExchangeHttpException, got $error", error is ExchangeHttpException)
        assertEquals(429, (error as ExchangeHttpException).code)
        // The round stopped at the throttled symbol instead of walking the rest of the watchlist.
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `fetchTickers reports a zero open price as unknown change`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(ZERO_OPEN_TICKER).build())

        val ticker = adapter.fetchTickers(listOf(market("NEWUSDT", "NEW", "USDT"))).single()

        assertNull(ticker.changePct24h)
    }

    @Test
    fun `fetchTickers keeps the healthy symbols when one of them is rejected`() = runTest {
        // A market delisted since the last listMarkets answers 400 forever; the rest of the round
        // (and, above it, the shared poll loop) must survive it.
        server.enqueue(MockResponse.Builder().code(400).body(INVALID_SYMBOL_BODY).build())
        server.enqueue(MockResponse.Builder().code(200).body(BTC_TICKER).build())

        val tickers = adapter.fetchTickers(
            listOf(market("GONEUSDT", "GONE", "USDT"), market("BTCUSDT", "BTC", "USDT")),
        )

        assertEquals(listOf(MarketKey("mexc:BTC/USDT")), tickers.map { it.key })
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `fetchTickers ignores a response that does not carry the requested symbol`() = runTest {
        // If a cache or proxy ever answered a `symbol=` request with the whole-market array, the
        // first row must not be labelled with the requested market's key.
        server.enqueue(MockResponse.Builder().code(200).body(ALL_TICKERS).build())

        // XRPUSDT is not part of ALL_TICKERS, so nothing may come back for it.
        assertTrue(adapter.fetchTickers(listOf(market("XRPUSDT", "XRP", "USDT"))).isEmpty())
    }

    @Test
    fun `fetchTickers of nothing issues no request`() = runTest {
        assertTrue(adapter.fetchTickers(emptyList()).isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `fetchOHLCV parses klines, flags the forming bar and clamps the limit`() = runTest {
        val now = System.currentTimeMillis()
        server.enqueue(
            MockResponse.Builder().code(200).body(klines(closeTime = now - 900_000, formingCloseTime = now + 900_000)).build(),
        )

        val candles = adapter.fetchOHLCV(
            market = market("BTCUSDT", "BTC", "USDT"),
            timeframe = Timeframe.H1,
            endTime = 1787428800000L,
            limit = 5000,
        )

        assertEquals(2, candles.size)
        val first = candles[0]
        assertEquals(1787421600000L, first.openTime)
        assertEquals(77276.41, first.open, 1e-9)
        assertEquals(77356.22, first.high, 1e-9)
        assertEquals(77211.21, first.low, 1e-9)
        assertEquals(77232.81, first.close, 1e-9)
        assertEquals(42.2334848, first.volume, 1e-9)
        assertTrue(first.closed)
        assertTrue(!candles[1].closed)
        assertTrue(candles[0].openTime < candles[1].openTime)

        val request = server.takeRequest()
        assertEquals("/api/v3/klines", request.url.encodedPath)
        assertEquals("BTCUSDT", request.url.queryParameter("symbol"))
        // One hour is "60m" on MEXC: "1h" is rejected with {"msg":"Invalid interval."}.
        assertEquals("60m", request.url.queryParameter("interval"))
        assertEquals("1000", request.url.queryParameter("limit"))
        assertEquals("1787428800000", request.url.queryParameter("endTime"))
        // A lone endTime is ignored by api.mexc.com, so the window is bounded on both sides.
        assertEquals(
            (1787428800000L - 1000L * Timeframe.H1.millis).toString(),
            request.url.queryParameter("startTime"),
        )
    }

    @Test
    fun `fetchOHLCV drops the bar that starts exactly at the exclusive endTime`() = runTest {
        val now = System.currentTimeMillis()
        server.enqueue(
            MockResponse.Builder().code(200).body(klines(closeTime = now - 900_000, formingCloseTime = now - 100)).build(),
        )

        // The second row of `klines` opens at 1787425200000 — exactly the requested bound, i.e. the
        // seam bar KLineChart already holds and would otherwise draw twice.
        val candles = adapter.fetchOHLCV(
            market = market("BTCUSDT", "BTC", "USDT"),
            timeframe = Timeframe.H1,
            endTime = 1787425200000L,
            limit = 300,
        )

        assertEquals(listOf(1787421600000L), candles.map { it.openTime })
        val request = server.takeRequest()
        assertEquals("1787425200000", request.url.queryParameter("endTime"))
        assertEquals(
            (1787425200000L - 300L * Timeframe.H1.millis).toString(),
            request.url.queryParameter("startTime"),
        )
    }

    @Test
    fun `fetchOHLCV maps every timeframe to a MEXC interval`() = runTest {
        repeat(Timeframe.entries.size) { server.enqueue(MockResponse.Builder().code(200).body("[]").build()) }

        for (timeframe in Timeframe.entries) {
            adapter.fetchOHLCV(market("BTCUSDT", "BTC", "USDT"), timeframe, endTime = null, limit = 10)
        }

        val intervals = Timeframe.entries.map { server.takeRequest() }.map { request ->
            assertNull(request.url.queryParameter("endTime"))
            request.url.queryParameter("interval")
        }
        assertEquals(listOf("1m", "5m", "15m", "30m", "60m", "4h", "1d", "1W", "1M"), intervals)
    }

    @Test
    fun `an error body with http 400 becomes an http exception`() = runTest {
        server.enqueue(MockResponse.Builder().code(400).body(INVALID_SYMBOL_BODY).build())

        val error = runCatching { adapter.fetchTickers(listOf(market("NOPEUSDT", "NOPE", "USDT"))) }.exceptionOrNull()

        assertTrue("expected ExchangeHttpException, got $error", error is ExchangeHttpException)
        error as ExchangeHttpException
        assertEquals(ExchangeId.MEXC, error.exchange)
        assertEquals(400, error.code)
        assertTrue(error.message!!, error.message!!.contains("invalid symbol"))
    }

    @Test
    fun `http 429 becomes an http exception carrying the retry hint`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(429)
                .addHeader("Retry-After", "17")
                .body("""{"msg":"Too many requests.","code":-1003}""")
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
        server.enqueue(MockResponse.Builder().code(451).body(BLOCKED_BODY).build())

        val error = runCatching { adapter.listMarkets() }.exceptionOrNull()

        assertTrue("expected ExchangeUnavailableException, got $error", error is ExchangeUnavailableException)
        assertEquals(ExchangeId.MEXC, (error as ExchangeUnavailableException).exchange)
    }

    @Test
    fun `the restricted-location body is an unavailable exception whatever the status is`() = runTest {
        server.enqueue(MockResponse.Builder().code(403).body(BLOCKED_BODY).build())

        val error = runCatching { adapter.listMarkets() }.exceptionOrNull()

        assertTrue("expected ExchangeUnavailableException, got $error", error is ExchangeUnavailableException)
    }

    @Test
    fun `a plain http 403 is not reported as a rate limit`() = runTest {
        server.enqueue(MockResponse.Builder().code(403).body("""{"msg":"Forbidden","code":-1022}""").build())

        val error = runCatching { adapter.listMarkets() }.exceptionOrNull()

        assertTrue("expected ExchangeHttpException, got $error", error is ExchangeHttpException)
        error as ExchangeHttpException
        assertEquals(403, error.code)
        assertTrue(error.message!!, !error.message!!.contains("rate limit"))
    }

    @Test
    fun `every endpoint gets its own token bucket`() = runTest {
        val paths = ArrayList<String>()
        val paced = MexcAdapter(
            client = client,
            scope = scope,
            restBase = server.url("/").toString(),
            bucketFactory = { path ->
                paths += path
                TokenBucket(capacity = 500.0, refillPerSecond = 50.0)
            },
        )
        server.enqueue(MockResponse.Builder().code(200).body(EXCHANGE_INFO).build())
        server.enqueue(MockResponse.Builder().code(200).body(BTC_TICKER).build())
        server.enqueue(MockResponse.Builder().code(200).body("[]").build())
        server.enqueue(MockResponse.Builder().code(200).body(BTC_TICKER).build())

        paced.listMarkets()
        val btc = market("BTCUSDT", "BTC", "USDT")
        paced.fetchTickers(listOf(btc))
        paced.fetchOHLCV(btc, Timeframe.M15, endTime = null, limit = 2)
        paced.fetchTickers(listOf(btc))

        // One bucket per endpoint path, created once and reused afterwards.
        assertEquals(listOf("/api/v3/exchangeInfo", "/api/v3/ticker/24hr", "/api/v3/klines"), paths)
    }

    private fun market(nativeSymbol: String, base: String, quote: String) = Market(
        key = MarketKey.of(ExchangeId.MEXC, base, quote),
        nativeSymbol = nativeSymbol,
        pricePrecision = 2,
    )

    private fun tickerFor(symbol: String) = """
        {"symbol":"$symbol","lastPrice":"1.10","openPrice":"1.00","highPrice":"1.20","lowPrice":"0.90",
         "volume":"10","quoteVolume":"11","openTime":1787336587340,"closeTime":1787422987340,"count":null}
    """.trimIndent()

    private fun klines(closeTime: Long, formingCloseTime: Long) = """
        [
          [1787421600000,"77276.41","77356.22","77211.21","77232.81","42.2334848",$closeTime,"3264261.78"],
          [1787425200000,"77232.81","77400.00","77100.00","77350.10","12.3456789",$formingCloseTime,"954321.00"]
        ]
    """.trimIndent()

    private companion object {
        const val EXCHANGE_INFO = """
        {
          "timezone":"CST","serverTime":1787422987340,
          "symbols":[
            {"symbol":"BTCUSDT","status":"1","baseAsset":"BTC","baseAssetPrecision":8,"quoteAsset":"USDT",
             "quotePrecision":2,"quoteAssetPrecision":2,"isSpotTradingAllowed":true,"isMarginTradingAllowed":false,
             "permissions":["SPOT"],"filters":[{"filterType":"PERCENT_PRICE_BY_SIDE","bidMultiplierUp":"0.005"}],
             "fullName":"Bitcoin","st":false},
            {"symbol":"ETHBTC","status":"1","baseAsset":"ETH","quoteAsset":"BTC","quotePrecision":6,
             "isSpotTradingAllowed":true,"filters":[]},
            {"symbol":"PEPEUSDT","status":"1","baseAsset":"PEPE","quoteAsset":"USDT","quotePrecision":9,
             "isSpotTradingAllowed":true,"filters":[]},
            {"symbol":"PAUSEDUSDT","status":"2","baseAsset":"PAUSED","quoteAsset":"USDT","quotePrecision":4,
             "isSpotTradingAllowed":true,"filters":[]},
            {"symbol":"NOSPOTUSDT","status":"1","baseAsset":"NOSPOT","quoteAsset":"USDT","quotePrecision":4,
             "isSpotTradingAllowed":false,"filters":[]},
            {"symbol":"WEIRDUSDT","status":"1","baseAsset":"WE.IRD","quoteAsset":"USDT","quotePrecision":4,
             "isSpotTradingAllowed":true,"filters":[]}
          ]
        }
        """

        const val BTC_TICKER = """
        {"symbol":"BTCUSDT","priceChange":"-104.79","priceChangePercent":"-0.0013","prevClosePrice":"77434.78",
         "lastPrice":"77329.99","bidPrice":"77324.92","bidQty":"1.367327","askPrice":"77324.93","askQty":"0.1001",
         "openPrice":"77434.78","highPrice":"78821.48","lowPrice":"76517.39","volume":"10178.86793886",
         "quoteVolume":"789395183.33","openTime":1787422971044,"closeTime":1787422987340,"count":null}
        """

        const val ETH_TICKER = """
        {"symbol":"ETHUSDT","priceChange":"12.30","priceChangePercent":"0.0032","prevClosePrice":"3842.70",
         "lastPrice":"3855.00","bidPrice":"3854.90","bidQty":"2.1","askPrice":"3855.10","askQty":"0.9",
         "openPrice":"3842.70","highPrice":"3901.00","lowPrice":"3810.00","volume":"52341.1234",
         "quoteVolume":"201234567.89","openTime":1787336587340,"closeTime":1787422987341,"count":null}
        """

        const val ALL_TICKERS = """
        [$BTC_TICKER,$ETH_TICKER,
         {"symbol":"DOGEUSDT","lastPrice":"0.31","openPrice":"0.30","highPrice":"0.32","lowPrice":"0.29",
          "volume":"1000","quoteVolume":"310","openTime":1787336587340,"closeTime":1787422987342,"count":null}]
        """

        const val ZERO_OPEN_TICKER = """
        {"symbol":"NEWUSDT","lastPrice":"1.00","openPrice":"0","highPrice":"1.00","lowPrice":"0",
         "volume":"10","quoteVolume":"10","openTime":1787336587340,"closeTime":1787422987340,"count":null}
        """

        const val INVALID_SYMBOL_BODY = """{"msg":"invalid symbol","code":-1121}"""
        const val THROTTLED_BODY = """{"msg":"Too many requests.","code":-1003}"""
        const val BLOCKED_BODY = """{"msg":"Service unavailable from a restricted location.","code":0}"""
    }
}
