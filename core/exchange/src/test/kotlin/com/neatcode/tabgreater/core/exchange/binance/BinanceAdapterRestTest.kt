package com.neatcode.tabgreater.core.exchange.binance

import com.neatcode.tabgreater.core.exchange.ExchangeHttpException
import com.neatcode.tabgreater.core.exchange.ExchangeUnavailableException
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

class BinanceAdapterRestTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var scope: CoroutineScope
    private lateinit var adapter: BinanceAdapter

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
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
    fun `listMarkets keeps tradable spot symbols and derives price precision from tick size`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(EXCHANGE_INFO).build())

        val markets = adapter.listMarkets()

        assertEquals(
            listOf("binance:BTC/EUR", "binance:ETH/BTC", "binance:PEPE/USDT", "binance:NOF/EUR"),
            markets.map { it.key.value },
        )
        val btc = markets.first { it.nativeSymbol == "BTCEUR" }
        assertEquals(2, btc.pricePrecision)
        assertEquals(0.01, btc.tickSize!!, 1e-12)
        assertEquals(5, markets.first { it.nativeSymbol == "ETHBTC" }.pricePrecision)
        // "1.00000000" -> no decimals at all.
        assertEquals(0, markets.first { it.nativeSymbol == "PEPEUSDT" }.pricePrecision)
        // Without a PRICE_FILTER the exchange's own quotePrecision is used and there is no tick size.
        val noFilter = markets.first { it.nativeSymbol == "NOFEUR" }
        assertEquals(6, noFilter.pricePrecision)
        assertNull(noFilter.tickSize)

        val request = server.takeRequest()
        assertEquals("/api/v3/exchangeInfo", request.url.encodedPath)
        assertNull(request.url.query)
    }

    @Test
    fun `fetchTickers splits markets into chunks of twenty and books each chunk`() = runTest {
        repeat(6) { server.enqueue(MockResponse.Builder().code(200).body("[]").build()) }
        val markets = (1..45).map { market("C${it}EUR", "C$it", "EUR") }

        val tickers = adapter.fetchTickers(markets)

        assertTrue(tickers.isEmpty())
        // Two requests per chunk: the 24hr MINI round and the bookTicker round that carries bid/ask.
        assertEquals(6, server.requestCount)
        val chunkSizes = (1..6).map { server.takeRequest() }.mapIndexed { index, request ->
            if (index % 2 == 0) {
                assertEquals("/api/v3/ticker/24hr", request.url.encodedPath)
                assertEquals("MINI", request.url.queryParameter("type"))
            } else {
                assertEquals("/api/v3/ticker/bookTicker", request.url.encodedPath)
                assertNull(request.url.queryParameter("type"))
            }
            val symbols = request.url.queryParameter("symbols")!!
            assertTrue(symbols.startsWith("[\"") && symbols.endsWith("\"]"))
            symbols.split(",").size
        }
        assertEquals(listOf(20, 20, 20, 20, 5, 5), chunkSizes)
    }

    @Test
    fun `fetchTickers maps the mini ticker fields and merges the book quote`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(MINI_TICKERS).build())
        server.enqueue(MockResponse.Builder().code(200).body(BOOK_TICKERS).build())

        val ticker = adapter.fetchTickers(listOf(market("ETHBTC", "ETH", "BTC"))).single()

        assertEquals(MarketKey.of(ExchangeId.BINANCE, "ETH", "BTC"), ticker.key)
        assertEquals(0.03133, ticker.bid!!, 1e-12)
        assertEquals(0.03135, ticker.ask!!, 1e-12)
        assertEquals(0.03134, ticker.last, 1e-12)
        assertEquals(0.03103, ticker.open24h!!, 1e-12)
        assertEquals(0.03238, ticker.high24h!!, 1e-12)
        assertEquals(0.03103, ticker.low24h!!, 1e-12)
        assertEquals(24450.8313, ticker.volumeBase24h!!, 1e-9)
        assertEquals(773.04598645, ticker.volumeQuote24h!!, 1e-9)
        assertEquals((0.03134 - 0.03103) / 0.03103 * 100.0, ticker.changePct24h!!, 1e-9)
        assertEquals(1787415255475L, ticker.timestamp)
    }

    @Test
    fun `fetchTickers reports a zero open price as unknown change`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(ZERO_OPEN_TICKER).build())
        server.enqueue(MockResponse.Builder().code(200).body("[]").build())

        val ticker = adapter.fetchTickers(listOf(market("NEWEUR", "NEW", "EUR"))).single()

        assertNull(ticker.changePct24h)
    }

    @Test
    fun `a failing book round leaves the tickers of that chunk without a quote`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(MINI_TICKERS).build())
        server.enqueue(MockResponse.Builder().code(500).body("boom").build())

        val ticker = adapter.fetchTickers(listOf(market("ETHBTC", "ETH", "BTC"))).single()

        assertEquals(0.03134, ticker.last, 1e-12)
        assertNull(ticker.bid)
        assertNull(ticker.ask)
    }

    @Test
    fun `fetchOHLCV parses klines, clamps the limit and makes endTime exclusive`() = runTest {
        val now = System.currentTimeMillis()
        val closedAt = now - 3_600_000
        val openAt = now + 3_600_000
        server.enqueue(MockResponse.Builder().code(200).body(klines(closedAt, openAt)).build())

        val candles = adapter.fetchOHLCV(
            market = market("BTCEUR", "BTC", "EUR"),
            timeframe = Timeframe.H1,
            endTime = 1787414400000L,
            limit = 5000,
        )

        assertEquals(2, candles.size)
        val first = candles[0]
        assertEquals(1787410800000L, first.openTime)
        assertEquals(65780.26, first.open, 1e-9)
        assertEquals(65853.71, first.high, 1e-9)
        assertEquals(65638.78, first.low, 1e-9)
        assertEquals(65679.93, first.close, 1e-9)
        assertEquals(5.678, first.volume, 1e-9)
        assertTrue(first.closed)
        assertTrue(!candles[1].closed)

        val request = server.takeRequest()
        assertEquals("/api/v3/klines", request.url.encodedPath)
        assertEquals("BTCEUR", request.url.queryParameter("symbol"))
        assertEquals("1h", request.url.queryParameter("interval"))
        assertEquals("1000", request.url.queryParameter("limit"))
        // Binance filters `openTime <= endTime`: one millisecond less is what keeps the bar that
        // starts exactly at endTime — the seam bar KLineChart already holds — out of the page.
        assertEquals("1787414399999", request.url.queryParameter("endTime"))
    }

    @Test
    fun `http 451 becomes an unavailable exception`() = runTest {
        server.enqueue(MockResponse.Builder().code(451).body(BLOCKED_BODY).build())

        val error = runCatching { adapter.listMarkets() }.exceptionOrNull()

        assertTrue("expected ExchangeUnavailableException, got $error", error is ExchangeUnavailableException)
        assertEquals(ExchangeId.BINANCE, (error as ExchangeUnavailableException).exchange)
    }

    @Test
    fun `http 429 becomes an http exception carrying the retry hint`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(429)
                .addHeader("Retry-After", "17")
                .body("""{"code":-1003,"msg":"Too many requests."}""")
                .build(),
        )

        val error = runCatching { adapter.listMarkets() }.exceptionOrNull()

        assertTrue("expected ExchangeHttpException, got $error", error is ExchangeHttpException)
        error as ExchangeHttpException
        assertEquals(429, error.code)
        assertTrue(error.message!!.contains("17"))
    }

    private fun market(nativeSymbol: String, base: String, quote: String) = Market(
        key = MarketKey.of(ExchangeId.BINANCE, base, quote),
        nativeSymbol = nativeSymbol,
        pricePrecision = 8,
    )

    private fun klines(closedCloseTime: Long, openCloseTime: Long) = """
        [
          [1787410800000,"65780.26000000","65853.71000000","65638.78000000","65679.93000000","5.67800000",
           $closedCloseTime,"373268.61001630",1291,"2.75809000","181294.96004010","0"],
          [1787414400000,"65679.93000000","65900.00000000","65600.00000000","65700.00000000","1.23400000",
           $openCloseTime,"81000.00000000",42,"0.60000000","39000.00000000","0"]
        ]
    """.trimIndent()

    private companion object {
        const val EXCHANGE_INFO = """
        {
          "timezone": "UTC",
          "serverTime": 1787415255475,
          "symbols": [
            {"symbol":"BTCEUR","status":"TRADING","baseAsset":"BTC","quoteAsset":"EUR","quotePrecision":8,
             "baseAssetPrecision":8,"isSpotTradingAllowed":true,
             "filters":[{"filterType":"PRICE_FILTER","minPrice":"0.01000000","maxPrice":"1000000.00000000","tickSize":"0.01000000"},
                        {"filterType":"LOT_SIZE","stepSize":"0.00001000"}]},
            {"symbol":"ETHBTC","status":"TRADING","baseAsset":"ETH","quoteAsset":"BTC","quotePrecision":8,
             "isSpotTradingAllowed":true,
             "filters":[{"filterType":"PRICE_FILTER","tickSize":"0.00001000"}]},
            {"symbol":"PEPEUSDT","status":"TRADING","baseAsset":"PEPE","quoteAsset":"USDT","quotePrecision":8,
             "isSpotTradingAllowed":true,
             "filters":[{"filterType":"PRICE_FILTER","tickSize":"1.00000000"}]},
            {"symbol":"HALTEUR","status":"HALT","baseAsset":"HALT","quoteAsset":"EUR","isSpotTradingAllowed":true,
             "filters":[{"filterType":"PRICE_FILTER","tickSize":"0.01000000"}]},
            {"symbol":"MARGINEUR","status":"TRADING","baseAsset":"MARGIN","quoteAsset":"EUR","isSpotTradingAllowed":false,
             "filters":[{"filterType":"PRICE_FILTER","tickSize":"0.01000000"}]},
            {"symbol":"WEIRDEUR","status":"TRADING","baseAsset":"WE.IRD","quoteAsset":"EUR","isSpotTradingAllowed":true,
             "filters":[{"filterType":"PRICE_FILTER","tickSize":"0.01000000"}]},
            {"symbol":"NOFEUR","status":"TRADING","baseAsset":"NOF","quoteAsset":"EUR","quotePrecision":6,
             "isSpotTradingAllowed":true,"filters":[]}
          ]
        }
        """

        const val MINI_TICKERS = """
        [{"symbol":"ETHBTC","openPrice":"0.03103000","highPrice":"0.03238000","lowPrice":"0.03103000",
          "lastPrice":"0.03134000","volume":"24450.83130000","quoteVolume":"773.04598645",
          "openTime":1787328855475,"closeTime":1787415255475,"firstId":532062904,"lastId":532142138,"count":79235}]
        """

        const val BOOK_TICKERS = """
        [{"symbol":"ETHBTC","bidPrice":"0.03133000","bidQty":"12.00000000",
          "askPrice":"0.03135000","askQty":"9.00000000"}]
        """

        const val ZERO_OPEN_TICKER = """
        [{"symbol":"NEWEUR","openPrice":"0.00000000","highPrice":"1.00000000","lowPrice":"0.00000000",
          "lastPrice":"1.00000000","volume":"10.00000000","quoteVolume":"10.00000000",
          "openTime":1787328855475,"closeTime":1787415255475}]
        """

        const val BLOCKED_BODY = """{"code":0,"msg":"Service unavailable from a restricted location."}"""
    }
}
