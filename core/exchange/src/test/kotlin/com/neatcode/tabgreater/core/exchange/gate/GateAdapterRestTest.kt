package com.neatcode.tabgreater.core.exchange.gate

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

class GateAdapterRestTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var scope: CoroutineScope
    private lateinit var adapter: GateAdapter

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        adapter = GateAdapter(
            client = client,
            scope = scope,
            restBase = server.url("/").toString(),
            wsBase = "ws://${server.hostName}:${server.port}/ws/v4/",
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
    fun `listMarkets keeps tradable pairs, derives the tick size and skips non-ascii assets`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(CURRENCY_PAIRS).build())

        val markets = adapter.listMarkets()

        assertEquals(
            listOf("gate:BTC/USDT", "gate:ETH/BTC", "gate:PEPE/USDT"),
            markets.map { it.key.value },
        )
        val btc = markets.first { it.nativeSymbol == "BTC_USDT" }
        assertEquals(1, btc.pricePrecision)
        assertEquals(0.1, btc.tickSize!!, 1e-12)
        val eth = markets.first { it.nativeSymbol == "ETH_BTC" }
        assertEquals(6, eth.pricePrecision)
        assertEquals(1e-6, eth.tickSize!!, 1e-15)
        val pepe = markets.first { it.nativeSymbol == "PEPE_USDT" }
        assertEquals(9, pepe.pricePrecision)
        assertEquals(1e-9, pepe.tickSize!!, 1e-18)

        val request = server.takeRequest()
        assertEquals("/api/v4/spot/currency_pairs", request.url.encodedPath)
        assertNull(request.url.query)
    }

    @Test
    fun `fetchTickers asks pair by pair for small sets and maps every field`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(BTC_TICKER).build())
        server.enqueue(MockResponse.Builder().code(200).body(ETH_TICKER).build())

        val tickers = adapter.fetchTickers(listOf(market("BTC_USDT", "BTC", "USDT"), market("ETH_BTC", "ETH", "BTC")))

        assertEquals(2, server.requestCount)
        assertEquals(listOf("BTC_USDT", "ETH_BTC"), (1..2).map { server.takeRequest() }.map { request ->
            assertEquals("/api/v4/spot/tickers", request.url.encodedPath)
            request.url.queryParameter("currency_pair")!!
        })

        val btc = tickers.first { it.key == MarketKey.of(ExchangeId.GATE, "BTC", "USDT") }
        assertEquals(77281.2, btc.last, 1e-9)
        assertEquals(0.02, btc.changePct24h!!, 1e-12)
        // Gate ships no open price: it is derived from last and the signed change.
        assertEquals(77281.2 / 1.0002, btc.open24h!!, 1e-9)
        assertEquals(78835.1, btc.high24h!!, 1e-9)
        assertEquals(76500.0, btc.low24h!!, 1e-9)
        assertEquals(14185.4769289849, btc.volumeBase24h!!, 1e-9)
        assertEquals(1101256030.5302483, btc.volumeQuote24h!!, 1e-3)
        assertEquals(77281.1, btc.bid!!, 1e-9)
        assertEquals(77281.2, btc.ask!!, 1e-9)
        assertTrue(btc.timestamp > 0)

        val eth = tickers.first { it.key == MarketKey.of(ExchangeId.GATE, "ETH", "BTC") }
        assertEquals(-0.1451, eth.changePct24h!!, 1e-12)
        assertEquals(0.03134 / (1 - 0.001451), eth.open24h!!, 1e-12)
    }

    @Test
    fun `fetchTickers drops open and change together when the derived open would be zero`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(DEAD_TICKER).build())

        val ticker = adapter.fetchTickers(listOf(market("DEAD_USDT", "DEAD", "USDT"))).single()

        // A -100 % change means the open was 0: neither number can be shown, so both are unknown.
        assertNull(ticker.open24h)
        assertNull(ticker.changePct24h)
        assertEquals(0.0001, ticker.last, 1e-12)
    }

    @Test
    fun `fetchTickers switches to one all-tickers request above the threshold`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(ALL_TICKERS).build())
        val markets = listOf(market("BTC_USDT", "BTC", "USDT")) +
            (1..GateAdapter.ALL_TICKERS_THRESHOLD).map { market("C${it}_USDT", "C$it", "USDT") }

        val tickers = adapter.fetchTickers(markets)

        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("/api/v4/spot/tickers", request.url.encodedPath)
        assertNull(request.url.query)
        // The response carries every pair Gate trades; only the requested ones come back.
        assertEquals(listOf(MarketKey.of(ExchangeId.GATE, "BTC", "USDT")), tickers.map { it.key })
    }

    @Test
    fun `fetchOHLCV parses the c-h-l-o cells, clamps the limit and makes endTime exclusive`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(CANDLESTICKS).build())

        val candles = adapter.fetchOHLCV(
            market = market("BTC_USDT", "BTC", "USDT"),
            timeframe = Timeframe.M15,
            endTime = 1787414400000L,
            limit = 5000,
        )

        assertEquals(2, candles.size)
        val first = candles[0]
        assertEquals(1787420700000L, first.openTime)
        assertEquals(77214.7, first.open, 1e-9)
        assertEquals(77371.8, first.high, 1e-9)
        assertEquals(77214.7, first.low, 1e-9)
        assertEquals(77275.1, first.close, 1e-9)
        assertEquals(41.379353, first.volume, 1e-9)
        assertTrue(first.closed)
        val second = candles[1]
        assertEquals(1787421600000L, second.openTime)
        assertEquals(12.5, second.volume, 1e-9)
        assertTrue(!second.closed)

        val request = server.takeRequest()
        assertEquals("/api/v4/spot/candlesticks", request.url.encodedPath)
        assertEquals("BTC_USDT", request.url.queryParameter("currency_pair"))
        assertEquals("15m", request.url.queryParameter("interval"))
        assertEquals("1000", request.url.queryParameter("limit"))
        assertEquals("1787414399", request.url.queryParameter("to"))
    }

    @Test
    fun `fetchOHLCV omits the upper bound and uses Gate's 7d and 30d intervals`() = runTest {
        repeat(2) { server.enqueue(MockResponse.Builder().code(200).body("[]").build()) }
        val btc = market("BTC_USDT", "BTC", "USDT")

        assertTrue(adapter.fetchOHLCV(btc, Timeframe.W1, endTime = null, limit = 10).isEmpty())
        assertTrue(adapter.fetchOHLCV(btc, Timeframe.MN1, endTime = null, limit = 10).isEmpty())

        val weekly = server.takeRequest()
        assertEquals("7d", weekly.url.queryParameter("interval"))
        assertNull(weekly.url.queryParameter("to"))
        assertEquals("30d", server.takeRequest().url.queryParameter("interval"))
    }

    @Test
    fun `an error body surfaces the Gate label`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(400)
                .body("""{"label":"INVALID_CURRENCY","message":"Invalid currency NOPE"}""")
                .build(),
        )

        val error = runCatching { adapter.listMarkets() }.exceptionOrNull()

        assertTrue("expected ExchangeHttpException, got $error", error is ExchangeHttpException)
        error as ExchangeHttpException
        assertEquals(400, error.code)
        assertEquals(ExchangeId.GATE, error.exchange)
        assertTrue(error.message!!, error.message!!.contains("INVALID_CURRENCY"))
    }

    @Test
    fun `a forbidden label on http 403 becomes an unavailable exception`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(403)
                .body("""{"label":"IP_FORBIDDEN","message":"Request forbidden from this location"}""")
                .build(),
        )

        val error = runCatching { adapter.listMarkets() }.exceptionOrNull()

        assertTrue("expected ExchangeUnavailableException, got $error", error is ExchangeUnavailableException)
        assertEquals(ExchangeId.GATE, (error as ExchangeUnavailableException).exchange)
    }

    @Test
    fun `a plain http 403 stays an http exception`() = runTest {
        server.enqueue(MockResponse.Builder().code(403).body("""{"label":"INVALID_KEY"}""").build())

        val error = runCatching { adapter.listMarkets() }.exceptionOrNull()

        assertTrue("expected ExchangeHttpException, got $error", error is ExchangeHttpException)
    }

    @Test
    fun `http 451 becomes an unavailable exception`() = runTest {
        server.enqueue(MockResponse.Builder().code(451).body("blocked").build())

        val error = runCatching { adapter.listMarkets() }.exceptionOrNull()

        assertTrue("expected ExchangeUnavailableException, got $error", error is ExchangeUnavailableException)
    }

    @Test
    fun `http 429 becomes an http exception carrying the retry hint`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(429)
                .addHeader("Retry-After", "17")
                .body("""{"label":"TOO_MANY_REQUESTS","message":"Too many requests"}""")
                .build(),
        )

        val error = runCatching { adapter.listMarkets() }.exceptionOrNull()

        assertTrue("expected ExchangeHttpException, got $error", error is ExchangeHttpException)
        error as ExchangeHttpException
        assertEquals(429, error.code)
        assertTrue(error.message!!, error.message!!.contains("17"))
        assertTrue(error.message!!, error.message!!.contains("TOO_MANY_REQUESTS"))
    }

    private fun market(nativeSymbol: String, base: String, quote: String) = Market(
        key = MarketKey.of(ExchangeId.GATE, base, quote),
        nativeSymbol = nativeSymbol,
        pricePrecision = 2,
    )

    private companion object {
        /** Gate really lists pairs like this one; the canonical key cannot represent them. */
        const val NON_ASCII_BASE = "龙虾"

        val CURRENCY_PAIRS = """
        [
          {"id":"BTC_USDT","base":"BTC","base_name":"Bitcoin","quote":"USDT","quote_name":"Tether","fee":"0.2",
           "min_base_amount":"0.000001","amount_precision":6,"precision":1,"trade_status":"tradable","type":"normal"},
          {"id":"ETH_BTC","base":"ETH","quote":"BTC","fee":"0.2","amount_precision":4,"precision":6,
           "trade_status":"tradable","type":"normal"},
          {"id":"PEPE_USDT","base":"PEPE","quote":"USDT","fee":"0.2","amount_precision":0,"precision":9,
           "trade_status":"tradable","type":"normal"},
          {"id":"${NON_ASCII_BASE}_USDT","base":"$NON_ASCII_BASE","quote":"USDT","precision":4,
           "trade_status":"tradable","type":"normal"},
          {"id":"OLD_USDT","base":"OLD","quote":"USDT","precision":4,"trade_status":"untradable","type":"normal"},
          {"id":"SELL_USDT","base":"SELL","quote":"USDT","precision":4,"trade_status":"sellable","type":"normal"}
        ]
        """

        const val BTC_TICKER = """
        [{"currency_pair":"BTC_USDT","last":"77281.2","lowest_ask":"77281.2","lowest_size":"5.543854",
          "highest_bid":"77281.1","highest_size":"0.440371","change_percentage":"0.02",
          "base_volume":"14185.4769289849","quote_volume":"1101256030.53024830148",
          "high_24h":"78835.1","low_24h":"76500"}]
        """

        const val ETH_TICKER = """
        [{"currency_pair":"ETH_BTC","last":"0.03134","lowest_ask":"0.031341","highest_bid":"0.031339",
          "change_percentage":"-0.1451","base_volume":"24450.8313","quote_volume":"773.04598645",
          "high_24h":"0.03238","low_24h":"0.03103"}]
        """

        const val DEAD_TICKER = """
        [{"currency_pair":"DEAD_USDT","last":"0.0001","lowest_ask":"0.0002","highest_bid":"0.00005",
          "change_percentage":"-100","base_volume":"1","quote_volume":"0.0001","high_24h":"1","low_24h":"0.0001"}]
        """

        const val ALL_TICKERS = """
        [{"currency_pair":"BTC_USDT","last":"77281.2","lowest_ask":"77281.2","highest_bid":"77281.1",
          "change_percentage":"0.02","base_volume":"14185.4769289849","quote_volume":"1101256030.53",
          "high_24h":"78835.1","low_24h":"76500"},
         {"currency_pair":"UNWANTED_USDT","last":"1","lowest_ask":"1","highest_bid":"1",
          "change_percentage":"0","base_volume":"1","quote_volume":"1","high_24h":"1","low_24h":"1"}]
        """

        const val CANDLESTICKS = """
        [["1787420700","3198386.50303000","77275.1","77371.8","77214.7","77214.7","41.37935300","true"],
         ["1787421600","1198386.50303000","77300.5","77400.0","77200.0","77275.1","12.50000000","false"]]
        """
    }
}
