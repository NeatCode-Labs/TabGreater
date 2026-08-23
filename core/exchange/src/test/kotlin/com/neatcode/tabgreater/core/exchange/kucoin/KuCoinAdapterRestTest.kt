package com.neatcode.tabgreater.core.exchange.kucoin

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
import java.time.LocalDate
import java.time.ZoneOffset

class KuCoinAdapterRestTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var scope: CoroutineScope
    private lateinit var adapter: KuCoinAdapter

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        adapter = KuCoinAdapter(client = client, scope = scope, restBase = server.url("/").toString())
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    @Test
    fun `listMarkets keeps tradable symbols and derives precision from the price increment`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(SYMBOLS).build())

        val markets = adapter.listMarkets()

        assertEquals(
            listOf("kucoin:BTC/USDT", "kucoin:AVA/USDT", "kucoin:KCS/BTC", "kucoin:PEPE/USDT", "kucoin:NOI/USDT"),
            markets.map { it.key.value },
        )
        val btc = markets.first { it.nativeSymbol == "BTC-USDT" }
        assertEquals(1, btc.pricePrecision)
        assertEquals(0.1, btc.tickSize!!, 1e-12)
        assertEquals(4, markets.first { it.nativeSymbol == "AVA-USDT" }.pricePrecision)
        assertEquals(9, markets.first { it.nativeSymbol == "KCS-BTC" }.pricePrecision)
        // "1" -> whole numbers only.
        assertEquals(0, markets.first { it.nativeSymbol == "PEPE-USDT" }.pricePrecision)
        // Without a priceIncrement there is no tick size to derive anything from.
        val noIncrement = markets.first { it.nativeSymbol == "NOI-USDT" }
        assertEquals(8, noIncrement.pricePrecision)
        assertNull(noIncrement.tickSize)

        val request = server.takeRequest()
        assertEquals("/api/v2/symbols", request.url.encodedPath)
    }

    @Test
    fun `fetchTickers uses one stats request per market for small sets`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(STATS_BTC).build())
        server.enqueue(MockResponse.Builder().code(200).body(STATS_UNKNOWN).build())

        val tickers = adapter.fetchTickers(
            listOf(market("BTC-USDT", "BTC", "USDT"), market("NOPE-XXX", "NOPE", "XXX")),
        )

        // The unknown symbol answers HTTP 200 with all-null fields and must not produce a ticker.
        val ticker = tickers.single()
        assertEquals(MarketKey.of(ExchangeId.KUCOIN, "BTC", "USDT"), ticker.key)
        assertEquals(77260.8, ticker.last, 1e-9)
        // /market/stats has no open price: it is reconstructed as last - changePrice.
        assertEquals(77342.8, ticker.open24h!!, 1e-9)
        assertEquals(78816.1, ticker.high24h!!, 1e-9)
        assertEquals(76486.5, ticker.low24h!!, 1e-9)
        assertEquals(77260.8, ticker.bid!!, 1e-9)
        assertEquals(77260.9, ticker.ask!!, 1e-9)
        assertEquals(4235.44939602823015530496, ticker.volumeBase24h!!, 1e-6)
        assertEquals(328310222.58644865499392928052, ticker.volumeQuote24h!!, 1e-3)
        assertEquals((77260.8 - 77342.8) / 77342.8 * 100.0, ticker.changePct24h!!, 1e-9)
        assertEquals(1787422576250L, ticker.timestamp)

        assertEquals(2, server.requestCount)
        val symbols = (1..2).map { server.takeRequest() }.map { request ->
            assertEquals("/api/v1/market/stats", request.url.encodedPath)
            request.url.queryParameter("symbol")
        }
        assertEquals(listOf("BTC-USDT", "NOPE-XXX"), symbols)
    }

    @Test
    fun `fetchTickers switches to the allTickers dump above the stats threshold`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(ALL_TICKERS).build())
        val markets = listOf(market("CLANKER-USDT", "CLANKER", "USDT")) +
            (1..8).map { market("C$it-USDT", "C$it", "USDT") }

        val tickers = adapter.fetchTickers(markets)

        assertEquals(1, server.requestCount)
        assertEquals("/api/v1/market/allTickers", server.takeRequest().url.encodedPath)
        // Only the symbols we asked for are mapped; the dump carries a thousand more.
        val ticker = tickers.single()
        assertEquals(MarketKey.of(ExchangeId.KUCOIN, "CLANKER", "USDT"), ticker.key)
        assertEquals(14.003, ticker.last, 1e-9)
        assertEquals(13.708, ticker.open24h!!, 1e-9)
        assertEquals(15.262, ticker.high24h!!, 1e-9)
        assertEquals(13.478, ticker.low24h!!, 1e-9)
        assertEquals(13.926, ticker.bid!!, 1e-9)
        assertEquals(13.975, ticker.ask!!, 1e-9)
        assertEquals(6690.32, ticker.volumeBase24h!!, 1e-9)
        assertEquals(95610.674, ticker.volumeQuote24h!!, 1e-9)
        assertEquals((14.003 - 13.708) / 13.708 * 100.0, ticker.changePct24h!!, 1e-9)
        assertEquals(1787422576931L, ticker.timestamp)
    }

    @Test
    fun `fetchTickers reports a zero open as unknown change`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(STATS_ZERO_OPEN).build())

        val ticker = adapter.fetchTickers(listOf(market("NEW-USDT", "NEW", "USDT"))).single()

        assertEquals(0.0, ticker.open24h!!, 1e-12)
        assertNull(ticker.changePct24h)
    }

    @Test
    fun `fetchOHLCV reverses the newest-first rows and flags the forming bar`() = runTest {
        val step = Timeframe.M15.millis
        val forming = System.currentTimeMillis() / step * step
        server.enqueue(MockResponse.Builder().code(200).body(candles(forming, step)).build())

        val candles = adapter.fetchOHLCV(
            market = market("BTC-USDT", "BTC", "USDT"),
            timeframe = Timeframe.M15,
            endTime = null,
            limit = 2,
        )

        // Three rows come back newest first; only the newest two survive, oldest first.
        assertEquals(listOf(forming - step, forming), candles.map { it.openTime })
        val closed = candles[0]
        assertEquals(77200.0, closed.open, 1e-9)
        assertEquals(77230.0, closed.high, 1e-9)
        assertEquals(77190.0, closed.low, 1e-9)
        assertEquals(77223.3, closed.close, 1e-9)
        assertEquals(1.5, closed.volume, 1e-9)
        assertTrue(closed.closed)
        assertTrue(!candles[1].closed)
        // KuCoin puts the close before high/low in the row.
        assertEquals(77260.8, candles[1].close, 1e-9)
        assertEquals(77260.9, candles[1].high, 1e-9)
        assertEquals(77223.2, candles[1].low, 1e-9)
    }

    @Test
    fun `fetchOHLCV clamps the limit and spans the window with startAt`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"code":"200000","data":[]}""").build())

        val candles = adapter.fetchOHLCV(
            market = market("BTC-USDT", "BTC", "USDT"),
            timeframe = Timeframe.H1,
            endTime = 1787414400000L,
            limit = 5000,
        )

        assertTrue(candles.isEmpty())
        val request = server.takeRequest()
        assertEquals("/api/v1/market/candles", request.url.encodedPath)
        assertEquals("BTC-USDT", request.url.queryParameter("symbol"))
        // endTime is exclusive, so the bar starting exactly at 1787414400 must stay out.
        assertEquals("1787414399", request.url.queryParameter("endAt"))
        // Without startAt KuCoin returns only 100 rows; the window covers the clamped 1 500 + 1.
        assertEquals("${1787414399L - 1501L * 3600}", request.url.queryParameter("startAt"))
    }

    @Test
    fun `fetchOHLCV never asks for a negative startAt`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"code":"200000","data":[]}""").build())

        adapter.fetchOHLCV(
            market = market("BTC-USDT", "BTC", "USDT"),
            timeframe = Timeframe.MN1,
            endTime = null,
            limit = 5000,
        )

        // 1 501 monthly windows reach back past 1970, and KuCoin rejects a negative startAt.
        assertEquals("0", server.takeRequest().url.queryParameter("startAt"))
    }

    @Test
    fun `fetchOHLCV closes a month bar on the calendar boundary`() = runTest {
        val thisMonth = monthStart(0)
        val previousMonth = monthStart(-1)
        server.enqueue(
            MockResponse.Builder().code(200).body(monthCandles(thisMonth, previousMonth)).build(),
        )

        val candles = adapter.fetchOHLCV(
            market = market("BTC-USDT", "BTC", "USDT"),
            timeframe = Timeframe.MN1,
            endTime = null,
            limit = 2,
        )

        assertEquals(listOf(previousMonth, thisMonth), candles.map { it.openTime })
        // A flat 30 day month would call February final two days late and the running bar of a
        // 31 day month final one day early.
        assertTrue(candles[0].closed)
        assertTrue(!candles[1].closed)
    }

    @Test
    fun `a business error code with http 200 becomes an http exception`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"msg":"Incorrect candlestick type.","code":"400100"}""")
                .build(),
        )

        val error = runCatching {
            adapter.fetchOHLCV(market("BTC-USDT", "BTC", "USDT"), Timeframe.H1, null, 10)
        }.exceptionOrNull()

        assertTrue("expected ExchangeHttpException, got $error", error is ExchangeHttpException)
        error as ExchangeHttpException
        assertEquals(200, error.code)
        assertTrue(error.message!!, error.message!!.contains("400100"))
        assertTrue(error.message!!, error.message!!.contains("Incorrect candlestick type."))
    }

    @Test
    fun `http 429 becomes an http exception carrying the retry hint`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(429)
                .addHeader("Retry-After", "17")
                .body("""{"code":"429000","msg":"Too Many Requests"}""")
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
        assertEquals(ExchangeId.KUCOIN, (error as ExchangeUnavailableException).exchange)
    }

    private fun market(nativeSymbol: String, base: String, quote: String) = Market(
        key = MarketKey.of(ExchangeId.KUCOIN, base, quote),
        nativeSymbol = nativeSymbol,
        pricePrecision = 8,
    )

    /** Start of the UTC month [offset] months from the current one. */
    private fun monthStart(offset: Long): Long = LocalDate.now(ZoneOffset.UTC)
        .withDayOfMonth(1)
        .plusMonths(offset)
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

    /** Monthly rows in KuCoin's newest-first order; only the bar starts matter here. */
    private fun monthCandles(vararg openTimes: Long) = openTimes.joinToString(
        separator = ",",
        prefix = """{"code":"200000","data":[""",
        postfix = "]}",
    ) { """["${it / 1000}","1","2","3","0.5","10","20"]""" }

    /** Newest first, exactly as KuCoin answers: `[time_sec, open, close, high, low, volume, turnover]`. */
    private fun candles(formingOpenTime: Long, step: Long) = """
        {"code":"200000","data":[
          ["${formingOpenTime / 1000}","77223.3","77260.8","77260.9","77223.2","2.2675762","175150.962850219"],
          ["${(formingOpenTime - step) / 1000}","77200.0","77223.3","77230.0","77190.0","1.5","115000.0"],
          ["${(formingOpenTime - 2 * step) / 1000}","77100.0","77200.0","77210.0","77090.0","0.5","38000.0"]
        ]}
    """.trimIndent()

    private companion object {
        const val SYMBOLS = """
        {"code":"200000","data":[
          {"symbol":"BTC-USDT","name":"BTC-USDT","baseCurrency":"BTC","quoteCurrency":"USDT","feeCurrency":"USDT",
           "market":"USDS","baseIncrement":"0.00000001","quoteIncrement":"0.000001","priceIncrement":"0.1",
           "priceLimitRate":"0.1","isMarginEnabled":true,"enableTrading":true,"st":false},
          {"symbol":"AVA-USDT","baseCurrency":"AVA","quoteCurrency":"USDT","priceIncrement":"0.0001",
           "enableTrading":true},
          {"symbol":"KCS-BTC","baseCurrency":"KCS","quoteCurrency":"BTC","priceIncrement":"0.000000001",
           "enableTrading":true},
          {"symbol":"PEPE-USDT","baseCurrency":"PEPE","quoteCurrency":"USDT","priceIncrement":"1",
           "enableTrading":true},
          {"symbol":"HALT-USDT","baseCurrency":"HALT","quoteCurrency":"USDT","priceIncrement":"0.01",
           "enableTrading":false},
          {"symbol":"WE.IRD-USDT","baseCurrency":"WE.IRD","quoteCurrency":"USDT","priceIncrement":"0.01",
           "enableTrading":true},
          {"symbol":"NOI-USDT","baseCurrency":"NOI","quoteCurrency":"USDT","enableTrading":true}
        ]}
        """

        const val STATS_BTC = """
        {"code":"200000","data":{"time":1787422576250,"symbol":"BTC-USDT","buy":"77260.8","sell":"77260.9",
         "changeRate":"-0.001","changePrice":"-82","high":"78816.1","low":"76486.5",
         "vol":"4235.44939602823015530496","volValue":"328310222.58644865499392928052","last":"77260.8",
         "averagePrice":"77522.52725074","takerFeeRate":"0.001","makerFeeRate":"0.001",
         "takerCoefficient":"1","makerCoefficient":"1"}}
        """

        const val STATS_UNKNOWN = """
        {"code":"200000","data":{"time":1787422576250,"symbol":"NOPE-XXX","buy":null,"sell":null,
         "changeRate":null,"changePrice":null,"high":null,"low":null,"vol":null,"volValue":null,"last":null,
         "averagePrice":null,"takerFeeRate":null,"makerFeeRate":null,"takerCoefficient":null,
         "makerCoefficient":null}}
        """

        const val STATS_ZERO_OPEN = """
        {"code":"200000","data":{"time":1787422576250,"symbol":"NEW-USDT","buy":"1","sell":"1.1",
         "changeRate":"0","changePrice":"1","high":"1.2","low":"0","vol":"10","volValue":"10","last":"1"}}
        """

        const val ALL_TICKERS = """
        {"code":"200000","data":{"time":1787422576931,"ticker":[
          {"symbol":"CLANKER-USDT","symbolName":"CLANKER-USDT","buy":"13.926","bestBidSize":"2.15",
           "sell":"13.975","bestAskSize":"22.93","changeRate":"0.0215","changePrice":"0.295","open":"13.708",
           "high":"15.262","low":"13.478","vol":"6690.32","volValue":"95610.674","last":"14.003",
           "lastSize":"14.68","averagePrice":"14.28787841","takerFeeRate":"0.001","makerFeeRate":"0.001",
           "takerCoefficient":"2","makerCoefficient":"2","priceChange":"0.295","priceChangePercent":"0.0215"},
          {"symbol":"UNWANTED-USDT","buy":"1","sell":"2","changeRate":"0.01","changePrice":"0.01",
           "open":"1","high":"2","low":"1","vol":"1","volValue":"1","last":"1.01"}
        ]}}
        """
    }
}
