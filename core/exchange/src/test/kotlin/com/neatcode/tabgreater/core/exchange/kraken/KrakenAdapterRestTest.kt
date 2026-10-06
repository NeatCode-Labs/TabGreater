package com.neatcode.tabgreater.core.exchange.kraken

import com.neatcode.tabgreater.core.exchange.ExchangeHttpException
import com.neatcode.tabgreater.core.exchange.ExchangeUnavailableException
import com.neatcode.tabgreater.core.exchange.ratelimit.TokenBucket
import com.neatcode.tabgreater.core.model.AssetClass
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
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

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
        server.enqueue(MockResponse.Builder().code(200).body(EMPTY_RESULT).build())

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
        // The crypto catalogue only ever holds coins.
        assertTrue(markets.all { it.assetClass == AssetClass.CRYPTO && it.underlying == null })

        // The crypto catalogue request is unchanged: no parameters at all.
        val request = server.takeRequest()
        assertEquals("/0/public/AssetPairs", request.url.encodedPath)
        assertNull(request.url.query)
        assertEquals("/0/public/AssetPairs?aclass_base=tokenized_asset", server.takeRequest().target)
    }

    @Test
    fun `listMarkets adds the share-equivalent tokenized pairs as stocks`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(ASSET_PAIRS).build())
        server.enqueue(MockResponse.Builder().code(200).body(TOKENIZED_PAIRS).build())

        val markets = adapter.listMarkets()

        // post_only is a closed stock market and still listed; for coins it stays excluded (POST/EUR).
        // The SPV twins, the cancel_only token and BRK.Bx (a dot in its base) are dropped.
        assertEquals(
            listOf(
                "kraken:BTC/EUR", "kraken:ETH/BTC", "kraken:DOGE/EUR", "kraken:ADA/EUR",
                "kraken:AAPLX/USD", "kraken:AMDX/USD", "kraken:TQQQX/USD",
            ),
            markets.map { it.key.value },
        )
        val apple = markets.first { it.key.value == "kraken:AAPLX/USD" }
        // The share-equivalent id, as Ticker and OHLC need it; the canonical key is upper-cased.
        assertEquals("AAPLxUSD", apple.nativeSymbol)
        assertEquals(AssetClass.STOCK, apple.assetClass)
        assertEquals("AAPL", apple.underlying)
        assertEquals(2, apple.pricePrecision)
        assertEquals(0.01, apple.tickSize!!, 1e-12)
        val amd = markets.first { it.key.value == "kraken:AMDX/USD" }
        assertEquals("AMDxUSD", amd.nativeSymbol)
        assertEquals("AMD", amd.underlying)
        assertEquals(4, markets.first { it.key.value == "kraken:TQQQX/USD" }.pricePrecision)
        assertTrue(markets.none { it.nativeSymbol.contains("SPV") })
        assertTrue(markets.take(4).all { it.assetClass == AssetClass.CRYPTO && it.underlying == null })

        assertNull(server.takeRequest().url.query)
        val tokenized = server.takeRequest()
        assertEquals("/0/public/AssetPairs", tokenized.url.encodedPath)
        assertEquals("tokenized_asset", tokenized.url.queryParameter("aclass_base"))
    }

    @Test
    fun `listMarkets returns the crypto markets when Kraken refuses the tokenized class`() = runTest {
        val logs = ArrayList<String>()
        val logging = loggingAdapter(logs)
        server.enqueue(MockResponse.Builder().code(200).body(ASSET_PAIRS).build())
        // Kraken's live answer to an asset class it does not offer.
        server.enqueue(MockResponse.Builder().code(200).body(INVALID_ARGUMENTS_ERROR).build())

        val markets = logging.listMarkets()

        assertEquals(
            listOf("kraken:BTC/EUR", "kraken:ETH/BTC", "kraken:DOGE/EUR", "kraken:ADA/EUR"),
            markets.map { it.key.value },
        )
        assertEquals(2, server.requestCount)
        // Logged, not silent: a missing Stocks catalogue must be traceable.
        assertTrue(logs.toString(), logs.any { it.contains("EGeneral:Invalid arguments") })
    }

    /** Not offered in this region: the stocks could not be served there anyway, the coins still can. */
    @Test
    fun `listMarkets returns the crypto markets when the tokenized catalogue is blocked in this region`() = runTest {
        val logs = ArrayList<String>()
        val logging = loggingAdapter(logs)
        server.enqueue(MockResponse.Builder().code(200).body(ASSET_PAIRS).build())
        server.enqueue(MockResponse.Builder().code(451).body("blocked").build())

        val markets = logging.listMarkets()

        assertEquals(
            listOf("kraken:BTC/EUR", "kraken:ETH/BTC", "kraken:DOGE/EUR", "kraken:ADA/EUR"),
            markets.map { it.key.value },
        )
        assertTrue(logs.toString(), logs.any { it.contains("HTTP 451") })
    }

    /** Kraken's retryable outages arrive with HTTP 200 as well; none of them says the class is gone. */
    @Test
    fun `listMarkets fails when the tokenized catalogue hits a temporary Kraken error`() = runTest {
        for (code in listOf("EService:Unavailable", "EGeneral:Internal error")) {
            server.enqueue(MockResponse.Builder().code(200).body(ASSET_PAIRS).build())
            server.enqueue(MockResponse.Builder().code(200).body("""{"error":["$code"]}""").build())

            val error = runCatching { adapter.listMarkets() }.exceptionOrNull()

            assertTrue("expected ExchangeHttpException for $code, got $error", error is ExchangeHttpException)
            assertEquals(com.neatcode.tabgreater.core.exchange.ExchangeFailureKind.TRANSIENT, (error as ExchangeHttpException).kind)
            assertTrue(error!!.message!!, error.message!!.contains(code))
        }
        assertEquals(4, server.requestCount)
    }

    /** A crypto-only answer would make the repository delete every stored Kraken stock row. */
    @Test
    fun `listMarkets fails when the tokenized catalogue does not arrive`() = runTest {
        // No transparent retry, so the dropped connection reaches the adapter as it would in the field.
        val noRetry = client.newBuilder().retryOnConnectionFailure(false).build()
        val dropping = KrakenAdapter(
            client = noRetry,
            scope = scope,
            restBase = server.url("/").toString(),
            restBucket = TokenBucket(capacity = 64.0, refillPerSecond = 10_000.0),
        )
        server.enqueue(MockResponse.Builder().code(200).body(ASSET_PAIRS).build())
        server.enqueue(
            MockResponse.Builder().code(200).body(TOKENIZED_PAIRS)
                .onResponseStart(SocketEffect.ShutdownConnection).build(),
        )

        val error = runCatching { dropping.listMarkets() }.exceptionOrNull()

        assertTrue("expected IOException, got $error", error is IOException)
    }

    @Test
    fun `listMarkets fails when the tokenized catalogue gets an http error`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(ASSET_PAIRS).build())
        server.enqueue(MockResponse.Builder().code(503).body("EService:Unavailable").build())

        val error = runCatching { adapter.listMarkets() }.exceptionOrNull()

        assertTrue("expected ExchangeHttpException, got $error", error is ExchangeHttpException)
        assertEquals(503, (error as ExchangeHttpException).code)
    }

    @Test
    fun `listMarkets fails when the tokenized catalogue is throttled`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(ASSET_PAIRS).build())
        // Kraken's throttle arrives as an `E` error with HTTP 200, but it says nothing about the class.
        server.enqueue(MockResponse.Builder().code(200).body(RATE_LIMIT_ERROR).build())

        val error = runCatching { adapter.listMarkets() }.exceptionOrNull()

        assertTrue("expected ExchangeHttpException, got $error", error is ExchangeHttpException)
        assertTrue(error!!.message!!, error.message!!.contains("rate limit"))
    }

    @Test
    fun `fetchTickers asks for the native pair ids and maps the 24 hour columns`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(TICKERS).build())

        val tickers = adapter.fetchTickers(
            listOf(market("XXBTZEUR", "BTC", "EUR"), market("ADAEUR", "ADA", "EUR")),
        )

        val request = server.takeRequest()
        assertEquals("XXBTZEUR,ADAEUR", request.url.queryParameter("pair"))
        // The exact request line crypto pairs have always been fetched with; stock tokens must not change it.
        assertEquals("/0/public/Ticker?pair=XXBTZEUR%2CADAEUR", request.target)
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
        val requests = (1..2).map { server.takeRequest() }
        val chunkSizes = requests.map { request ->
            assertEquals("/0/public/Ticker", request.url.encodedPath)
            request.url.queryParameter("pair")!!.split(",").size
        }
        assertEquals(listOf(100, 50), chunkSizes)
        // Only `pair`, exactly as before stock tokens existed.
        val expected = listOf(1..100, 101..150).map { range ->
            "/0/public/Ticker?pair=" + range.joinToString("%2C") { "C${it}EUR" }
        }
        assertEquals(expected, requests.map { it.target })
    }

    /** Kraken answers a call mixing the two classes with an `E` error next to a partial result. */
    @Test
    fun `fetchTickers asks for crypto pairs and stock tokens in separate calls`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(TICKERS).build())
        server.enqueue(MockResponse.Builder().code(200).body(STOCK_TICKERS).build())

        val tickers = adapter.fetchTickers(
            listOf(market("XXBTZEUR", "BTC", "EUR"), AAPLX_USD, market("ADAEUR", "ADA", "EUR"), AMDX_USD),
        )

        // Crypto first, byte for byte the request it has always been; then the stock tokens.
        assertEquals("/0/public/Ticker?pair=XXBTZEUR%2CADAEUR", server.takeRequest().target)
        val stocks = server.takeRequest()
        assertEquals("/0/public/Ticker", stocks.url.encodedPath)
        assertEquals("AAPLxUSD,AMDxUSD", stocks.url.queryParameter("pair"))
        assertEquals("tokenized_asset", stocks.url.queryParameter("asset_class"))
        assertEquals(2, server.requestCount)

        assertEquals(4, tickers.size)
        val apple = tickers.first { it.key == AAPLX_USD.key }
        assertEquals("kraken:AAPLX/USD", apple.key.value)
        assertEquals(333.33, apple.last, 1e-9)
        assertEquals(333.26, apple.bid!!, 1e-9)
        assertEquals(335.02, apple.high24h!!, 1e-9)
        assertEquals(633.05, tickers.first { it.key == AMDX_USD.key }.last, 1e-9)
        assertEquals(65908.90, tickers.first { it.key.value == "kraken:BTC/EUR" }.last, 1e-9)
    }

    @Test
    fun `a failing stock token call still yields the crypto tickers`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(TICKERS).build())
        server.enqueue(MockResponse.Builder().code(200).body(INVALID_ARGUMENTS_ERROR).build())

        val tickers = adapter.fetchTickers(
            listOf(market("XXBTZEUR", "BTC", "EUR"), market("ADAEUR", "ADA", "EUR"), AAPLX_USD),
        )

        assertEquals(listOf("kraken:ADA/EUR", "kraken:BTC/EUR"), tickers.map { it.key.value }.sorted())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a failing crypto call still fails fetchTickers`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(UNKNOWN_PAIR_ERROR).build())

        val error = runCatching {
            adapter.fetchTickers(listOf(market("XXBTZEUR", "BTC", "EUR"), AAPLX_USD))
        }.exceptionOrNull()

        // Unchanged behaviour: the caller sees the failure, and the stock call is never made.
        assertTrue("expected ExchangeHttpException, got $error", error is ExchangeHttpException)
        assertEquals(1, server.requestCount)
    }

    /** A token Kraken has since delisted must not cost the other stock tiles of its call their prices. */
    @Test
    fun `stock tickers answered in part keep the pairs Kraken still lists`() = runTest {
        val logs = ArrayList<String>()
        server.enqueue(MockResponse.Builder().code(200).body(STOCK_TICKERS_IN_PART).build())

        val tickers = loggingAdapter(logs).fetchTickers(listOf(AAPLX_USD, stock("GONE")))

        assertEquals(listOf("kraken:AAPLX/USD"), tickers.map { it.key.value })
        assertEquals(333.20, tickers.single().last, 1e-9)
        assertTrue(logs.toString(), logs.any { it.contains("EQuery:Unknown asset pair") })
    }

    @Test
    fun `crypto tickers answered in part still fail as before`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(CRYPTO_TICKERS_IN_PART).build())

        val error = runCatching {
            adapter.fetchTickers(listOf(market("XXBTZEUR", "BTC", "EUR"), market("GONEEUR", "GONE", "EUR")))
        }.exceptionOrNull()

        assertTrue("expected ExchangeHttpException, got $error", error is ExchangeHttpException)
    }

    /** Recorded live on 2026-10-04: ADBEx had no trade at all, CRWVx one trade and an empty book. */
    @Test
    fun `a stock token's zeros are missing prices, not real ones`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(ZERO_TICKER).build())
        server.enqueue(MockResponse.Builder().code(200).body(ZERO_STOCK_TICKERS).build())
        val crwv = stock("CRWV")

        val tickers = adapter.fetchTickers(listOf(market("NEWEUR", "NEW", "EUR"), stock("ADBE"), crwv))

        // ADBEx gets no ticker at all, so its tile keeps the chart's last close instead of 0.00.
        assertEquals(listOf("kraken:CRWVX/USD", "kraken:NEW/EUR"), tickers.map { it.key.value }.sorted())
        val traded = tickers.first { it.key == crwv.key }
        assertEquals(86.96, traded.last, 1e-9)
        assertEquals(86.96, traded.high24h!!, 1e-9)
        assertEquals(86.96, traded.low24h!!, 1e-9)
        assertNull(traded.bid)
        assertNull(traded.ask)
        // Crypto values pass through exactly as before.
        val coin = tickers.first { it.key.value == "kraken:NEW/EUR" }
        assertEquals(0.0, coin.last, 0.0)
        assertEquals(0.0, coin.bid!!, 0.0)
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
        assertEquals("/0/public/OHLC?pair=XXBTZEUR&interval=15", request.target)
    }

    @Test
    fun `fetchOHLCV asks for a stock token with its asset class`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(STOCK_OHLC).build())

        val candles = adapter.fetchOHLCV(AAPLX_USD, Timeframe.M15, null, 720)

        assertEquals(listOf(1791117900000L, 1791118800000L), candles.map { it.openTime })
        assertEquals(333.33, candles.last().close, 1e-9)
        assertEquals(listOf(true, false), candles.map { it.closed })
        val request = server.takeRequest()
        assertEquals("/0/public/OHLC", request.url.encodedPath)
        assertEquals("AAPLxUSD", request.url.queryParameter("pair"))
        assertEquals("15", request.url.queryParameter("interval"))
        // Without it Kraken answers `EGeneral:Invalid arguments`.
        assertEquals("tokenized_asset", request.url.queryParameter("asset_class"))
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

    /** A stored Kraken stock token on [ticker], e.g. `kraken:CRWVX/USD` with pair id `CRWVxUSD`. */
    private fun stock(ticker: String) = Market(
        key = MarketKey.of(ExchangeId.KRAKEN, "${ticker}x", "USD"),
        nativeSymbol = "${ticker}xUSD",
        pricePrecision = 2,
        assetClass = AssetClass.STOCK,
        underlying = ticker,
    )

    private fun loggingAdapter(logs: MutableList<String>) = KrakenAdapter(
        client = client,
        scope = scope,
        restBase = server.url("/").toString(),
        logger = { logs += it },
        restBucket = TokenBucket(capacity = 64.0, refillPerSecond = 10_000.0),
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

        /**
         * Shaped like the live `aclass_base=tokenized_asset` catalogue of 2026-10-04: every share is
         * listed twice under one `altname`, as the share-equivalent id and as its SPV twin.
         */
        const val TOKENIZED_PAIRS = """
        {"error":[],"result":{
          "AAPLSPVUSD":{"altname":"AAPLxUSD","wsname":"AAPLx/USD","aclass_base":"tokenized_asset",
            "base":"AAPLx","aclass_quote":"currency","quote":"ZUSD","pair_decimals":2,"tick_size":"0.01",
            "status":"online"},
          "AAPLxUSD":{"altname":"AAPLxUSD","wsname":"AAPLx/USD","aclass_base":"tokenized_asset",
            "base":"AAPLx","aclass_quote":"currency","quote":"ZUSD","pair_decimals":2,"tick_size":"0.01",
            "status":"online"},
          "AMDSPVUSD":{"altname":"AMDxUSD","wsname":"AMDx/USD","aclass_base":"tokenized_asset",
            "base":"AMDx","aclass_quote":"currency","quote":"ZUSD","pair_decimals":2,"tick_size":"0.01",
            "status":"post_only"},
          "AMDxUSD":{"altname":"AMDxUSD","wsname":"AMDx/USD","aclass_base":"tokenized_asset",
            "base":"AMDx","aclass_quote":"currency","quote":"ZUSD","pair_decimals":2,"tick_size":"0.01",
            "status":"post_only"},
          "TQQQxUSD":{"altname":"TQQQxUSD","wsname":"TQQQx/USD","aclass_base":"tokenized_asset",
            "base":"TQQQx","aclass_quote":"currency","quote":"ZUSD","pair_decimals":4,"tick_size":"0.01",
            "status":"post_only"},
          "HALTxUSD":{"altname":"HALTxUSD","wsname":"HALTx/USD","aclass_base":"tokenized_asset",
            "base":"HALTx","aclass_quote":"currency","quote":"ZUSD","pair_decimals":2,"tick_size":"0.01",
            "status":"cancel_only"},
          "BRK.BxUSD":{"altname":"BRK.BxUSD","wsname":"BRK.Bx/USD","aclass_base":"tokenized_asset",
            "base":"BRK.Bx","aclass_quote":"currency","quote":"ZUSD","pair_decimals":2,"tick_size":"0.01",
            "status":"post_only"}
        }}
        """

        /** Keyed by the share-equivalent ids, as Kraken answers `asset_class=tokenized_asset`. */
        const val STOCK_TICKERS = """
        {"error":[],"result":{
          "AAPLxUSD":{"a":["333.27","2","2.000"],"b":["333.26","1","1.000"],"c":["333.33","0.022500"],
            "v":["3.737641","14.756912"],"p":["333.83","333.44"],"t":[19,41],"l":["333.20","333.18"],
            "h":["335.02","335.02"],"o":"333.31"},
          "AMDxUSD":{"a":["634.09000","2","2.000"],"b":["634.08000","1","1.000"],"c":["633.05000","0.969900"],
            "v":["106.767419","0.969900"],"p":["632.86265","633.05000"],"t":[91,1],"l":["619.25000","633.05000"],
            "h":["642.51000","633.05000"],"o":"621.33000"}
        }}
        """

        /** Kraken's live answer when one asked-for pair is unknown: an `E` error next to the others. */
        const val STOCK_TICKERS_IN_PART = """
        {"error":["EQuery:Unknown asset pair"],"result":{
          "AAPLxUSD":{"a":["333.23","2","2.000"],"b":["333.20","2","2.000"],"c":["333.20","0.022510"],
            "v":["4.088314","15.107585"],"p":["333.77","333.43"],"t":[25,47],"l":["333.07","333.07"],
            "h":["335.02","335.02"],"o":"333.31"}
        }}
        """

        const val CRYPTO_TICKERS_IN_PART = """
        {"error":["EQuery:Unknown asset pair"],"result":{
          "XXBTZEUR":{"a":["65926.20000","1","1.000"],"b":["65926.10000","1","1.000"],
            "c":["65908.90000","0.02077666"],"v":["452.78380117","627.83444338"],
            "p":["66153.82909","66266.23772"],"t":[17399,24763],"l":["65160.50000","65160.50000"],
            "h":["67389.20000","67389.20000"],"o":"66997.30000"}
        }}
        """

        /** Verbatim from `Ticker?pair=ADBExUSD,CRWVxUSD&asset_class=tokenized_asset` on 2026-10-04. */
        const val ZERO_STOCK_TICKERS = """
        {"error":[],"result":{
          "ADBExUSD":{"a":["0.00000","0","0.000"],"b":["224.15000","3","3.000"],"c":["0.00000","0.000000"],
            "v":["0.000000","0.000000"],"p":["0.00000","0.00000"],"t":[0,0],"l":["0.00000","0.00000"],
            "h":["0.00000","0.00000"],"o":"0.00000"},
          "CRWVxUSD":{"a":["0.00000","0","0.000"],"b":["0.00000","0","0.000"],"c":["86.96000","0.300000"],
            "v":["0.000000","0.300000"],"p":["0.00000","86.96000"],"t":[0,1],"l":["0.00000","86.96000"],
            "h":["0.00000","86.96000"],"o":"0.00000"}
        }}
        """

        /** The same zeros for a coin. */
        const val ZERO_TICKER = """
        {"error":[],"result":{"NEWEUR":{"a":["0.00000","0","0.000"],"b":["0.00000","0","0.000"],
          "c":["0.00000","0.00000000"],"v":["0.00000000","0.00000000"],"p":["0.00000","0.00000"],"t":[0,0],
          "l":["0.00000","0.00000"],"h":["0.00000","0.00000"],"o":"0.00000"}}}
        """

        const val STOCK_OHLC = """
        {"error":[],"result":{"AAPLxUSD":[
          [1791117900,"333.35","333.35","333.35","333.35","0.00","0.000000",0],
          [1791118800,"333.34","333.34","333.33","333.33","333.33","0.128892",2]
        ],"last":1791117900}}
        """

        val AAPLX_USD = Market(
            key = MarketKey.of(ExchangeId.KRAKEN, "AAPLx", "USD"),
            nativeSymbol = "AAPLxUSD",
            pricePrecision = 2,
            assetClass = AssetClass.STOCK,
            underlying = "AAPL",
        )

        val AMDX_USD = Market(
            key = MarketKey.of(ExchangeId.KRAKEN, "AMDx", "USD"),
            nativeSymbol = "AMDxUSD",
            pricePrecision = 2,
            assetClass = AssetClass.STOCK,
            underlying = "AMD",
        )

        const val EMPTY_RESULT = """{"error":[],"result":{}}"""

        /** Kraken's answer to an asset class it does not offer (checked live on 2026-10-04). */
        const val INVALID_ARGUMENTS_ERROR = """{"error":["EGeneral:Invalid arguments"]}"""

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
