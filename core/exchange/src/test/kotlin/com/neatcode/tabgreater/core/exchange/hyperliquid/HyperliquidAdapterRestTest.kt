package com.neatcode.tabgreater.core.exchange.hyperliquid

import com.neatcode.tabgreater.core.exchange.ExchangeFailureKind
import com.neatcode.tabgreater.core.exchange.ExchangeHttpException
import com.neatcode.tabgreater.core.exchange.ratelimit.TokenBucket
import com.neatcode.tabgreater.core.model.ExchangeId

import com.neatcode.tabgreater.core.model.InstrumentKind
import com.neatcode.tabgreater.core.model.Market

import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.Timeframe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class HyperliquidAdapterRestTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var scope: CoroutineScope
    private lateinit var adapter: HyperliquidAdapter
    private val requestBodies = ConcurrentLinkedQueue<String>()
    private val failOutcomeMeta = AtomicBoolean(false)

    private val infoDispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val body = request.body?.utf8().orEmpty()
            requestBodies += body
            val payload = Json.parseToJsonElement(body).jsonObject
            return when (payload.getValue("type").jsonPrimitive.content) {
                "spotMetaAndAssetCtxs" -> ok(SPOT_META_AND_CONTEXTS)
                "perpDexs" -> ok(PERP_DEXES)
                "outcomeMeta" -> if (failOutcomeMeta.getAndSet(false)) {
                    MockResponse.Builder().code(503).body("temporary failure").build()
                } else ok(OUTCOME_META)
                "outcomeTemplates" -> ok(OUTCOME_TEMPLATES)
                "perpCategories" -> ok(PERP_CATEGORIES)
                "metaAndAssetCtxs" -> ok(perpMetadata(payload["dex"]?.jsonPrimitive?.content.orEmpty()))
                "recentTrades" -> {
                    when (val coin = payload["coin"]?.jsonPrimitive?.content.orEmpty()) {
                        "NO_TRADES" -> ok("[]")
                        "BROKEN" -> MockResponse.Builder().code(503).body("upstream unavailable").build()
                        else -> ok(trades(coin))
                    }
                }
                "candleSnapshot" -> ok(CANDLE_SNAPSHOT)
                else -> MockResponse.Builder().code(400).body("unknown info type").build()
            }
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = infoDispatcher
        server.start()
        client = OkHttpClient()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        adapter = HyperliquidAdapter(
            client = client,
            scope = scope,
            restBase = server.url("/").toString(),
            wsBase = "ws://${server.hostName}:${server.port}/ws",
            restBucket = TokenBucket(capacity = 200.0, refillPerSecond = 10_000.0),
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
    fun `catalog covers spot core HIP3 and outcome-side markets with stable identities`() = runTest {
        val markets = adapter.listMarkets()
        val byKey = markets.associateBy { it.key.value }

        val spot = byKey.getValue("hyperliquid:SPOT_107/USDC")
        assertEquals("@107", spot.nativeSymbol)
        assertEquals("ANT/USDC", spot.displayName)
        assertEquals(InstrumentKind.SPOT, spot.instrumentKind)
        assertEquals(6, spot.pricePrecision) // spot = 8 - szDecimals

        val tokenizedStock = byKey.getValue("hyperliquid:SPOT_264/USDC")
        assertEquals(2, tokenizedStock.pricePrecision)

        val core = byKey.getValue("hyperliquid:PERP_BTC/USD")
        assertEquals("BTC", core.nativeSymbol)
        assertEquals("BTC/USD", core.displayName)
        assertEquals(InstrumentKind.PERPETUAL, core.instrumentKind)
        assertEquals(1, core.pricePrecision) // perp = 6 - szDecimals

        val unknownCore = byKey.getValue("hyperliquid:PERP_FUTURECOIN/USD")
        assertEquals(InstrumentKind.PERPETUAL, unknownCore.instrumentKind)

        val stockPerp = byKey.getValue("hyperliquid:HIP3_XYZ_ANT/USD")
        assertEquals("xyz:ANT", stockPerp.nativeSymbol)
        assertEquals("xyz:ANT/USD", stockPerp.displayName)
        assertEquals("USD/share", stockPerp.unit)
        assertEquals("xyz", stockPerp.venue)
        assertFalse(markets.any { it.nativeSymbol == "xyz:DELISTED" })

        val rate = byKey.getValue("hyperliquid:HIP3_PARA_10Y/USD")
        assertEquals("%", rate.unit)

        val palaceYes = byKey.getValue("hyperliquid:OUTCOME_801_SIDE_0/USDC")
        val palaceNo = byKey.getValue("hyperliquid:OUTCOME_801_SIDE_1/USDC")
        assertEquals("#8010", palaceYes.nativeSymbol)
        assertEquals("#8011", palaceNo.nativeSymbol)
        assertEquals(InstrumentKind.OUTCOME, palaceYes.instrumentKind)
        assertEquals("901", palaceYes.eventId)
        assertEquals("801", palaceYes.outcomeId)
        assertEquals("Yes", palaceYes.outcomeSide)
        assertEquals("No", palaceNo.outcomeSide)
        assertEquals(InstantTimes.EVENT_EXPIRY, palaceYes.expiresAt)
        assertTrue(palaceYes.displayName!!.contains("Coventry v Palace"))
        assertTrue(palaceYes.displayName!!.contains("Coventry · Yes"))
        assertTrue(palaceNo.displayName!!.contains("Coventry · No"))

        val fallback = byKey.getValue("hyperliquid:OUTCOME_802_SIDE_0/USDC")
        assertFalse(fallback.active)
        assertTrue(fallback.displayName!!.contains("Draw"))
        assertTrue(fallback.displayName!!.contains("Coventry v Palace"))

        val recurring = byKey.getValue("hyperliquid:OUTCOME_803_SIDE_0/USDC")
        assertTrue(recurring.displayName!!.contains("BTC price below \$80,000"))
        assertTrue(recurring.displayName!!.contains("Dec 31, 2026 17:00 UTC"))
        assertEquals(InstantTimes.RECURRING_EXPIRY, recurring.expiresAt)
        assertFalse(recurring.displayName!!.contains("20261231-1200")) // kickoff is not settlement

        val fallbackTemplate = byKey.getValue("hyperliquid:OUTCOME_804_SIDE_0/USDC")
        assertTrue(fallbackTemplate.displayName!!.contains("2026/2027 NBA winner"))
        assertTrue(fallbackTemplate.displayName!!.contains("Other outcome"))
        assertFalse(fallbackTemplate.displayName!!.contains("template fallback", ignoreCase = true))

        val bitcoinBucket0 = byKey.getValue("hyperliquid:OUTCOME_40269_SIDE_0/USDC")
        val bitcoinBucket1 = byKey.getValue("hyperliquid:OUTCOME_40270_SIDE_0/USDC")
        val bitcoinBucket2 = byKey.getValue("hyperliquid:OUTCOME_40271_SIDE_0/USDC")
        val bitcoinOther = byKey.getValue("hyperliquid:OUTCOME_40268_SIDE_0/USDC")
        assertTrue(bitcoinBucket0.displayName!!.contains("BTC price below \$62,063"))
        assertTrue(bitcoinBucket1.displayName!!.contains("BTC price from \$62,063 to below \$64,596"))
        assertTrue(bitcoinBucket2.displayName!!.contains("BTC price at least \$64,596"))
        assertTrue(bitcoinOther.displayName!!.contains("BTC price in other bucket"))
        assertTrue(bitcoinBucket0.displayName!!.contains("Nov 20, 2026 06:00 UTC"))
        assertEquals(InstantTimes.PRICE_BUCKET_EXPIRY, bitcoinBucket0.expiresAt)

        val standaloneEth = byKey.getValue("hyperliquid:OUTCOME_40265_SIDE_0/USDC")
        val standaloneBtc = byKey.getValue("hyperliquid:OUTCOME_40264_SIDE_0/USDC")
        assertTrue(standaloneEth.displayName!!.contains("ETH at least \$1,799.100000001"))
        assertTrue(standaloneBtc.displayName!!.contains("BTC at least \$63,330"))
        assertFalse(standaloneEth.displayName!!.startsWith("Recurring"))
        assertEquals("priceBinary:BTC:1d:21600000", standaloneBtc.rolloverSeriesId)
        assertEquals(standaloneBtc.rolloverSeriesId, byKey.getValue("hyperliquid:OUTCOME_40264_SIDE_1/USDC").rolloverSeriesId)
        assertEquals(standaloneBtc.rolloverSeriesId, byKey.getValue("hyperliquid:OUTCOME_40266_SIDE_0/USDC").rolloverSeriesId)
        assertNotEquals(standaloneBtc.rolloverSeriesId, byKey.getValue("hyperliquid:OUTCOME_40267_SIDE_0/USDC").rolloverSeriesId)
        assertEquals("priceBinary:ETH:1d:21600000", standaloneEth.rolloverSeriesId)
        assertNull(byKey.getValue("hyperliquid:OUTCOME_40269_SIDE_0/USDC").rolloverSeriesId) // priceBucket

        assertTrue(requestBodies.any { it.contains("\"dex\":\"\"") }) // core perp metadata is required
        assertTrue(requestBodies.any { it.contains("\"type\":\"outcomeTemplates\"") })
    }

    @Test
    fun `recent trades return actual prices and separate no data from request failures`() = runTest {
        val markets = adapter.listMarkets().associateBy { it.nativeSymbol }
        val btc = markets.getValue("BTC")
        val spot = markets.getValue("@107")
        val event = markets.getValue("#8010")
        val noTrades = Market(MarketKey.of(ExchangeId.HYPERLIQUID, "TEST_NO_TRADES", "USD"), "NO_TRADES", 2)
        val broken = Market(MarketKey.of(ExchangeId.HYPERLIQUID, "TEST_BROKEN", "USD"), "BROKEN", 2)

        val batch = adapter.fetchTickerBatch(listOf(btc, spot, event, noTrades, broken))

        assertEquals(setOf(btc.key, spot.key, event.key), batch.values.keys)
        assertEquals(12.5, batch.values.getValue(btc.key).last, 1e-12)
        assertEquals(60_000.0, batch.values.getValue(btc.key).open24h!!, 1e-12)
        assertEquals(0.9, batch.values.getValue(spot.key).open24h!!, 1e-12)
        assertEquals(0.625, batch.values.getValue(event.key).last, 1e-12)
        assertEquals(setOf(noTrades.key), batch.missing)
        assertTrue(batch.failures.getValue(broken.key) is ExchangeHttpException)
        assertEquals(ExchangeFailureKind.TRANSIENT, (batch.failures.getValue(broken.key) as ExchangeHttpException).kind)
    }

    @Test
    fun `candle snapshot converts the exclusive end and candle fields`() = runTest {
        val market = Market(MarketKey.of(ExchangeId.HYPERLIQUID, "BTC", "USD"), "BTC", 1)

        val candles = adapter.fetchOHLCV(market, Timeframe.M1, endTime = 100_000L, limit = 2)

        assertEquals(1, candles.size)
        assertEquals(1_000L, candles.single().openTime)
        assertEquals(10.0, candles.single().open, 1e-12)
        assertEquals(15.0, candles.single().high, 1e-12)
        assertEquals(12.0, candles.single().close, 1e-12)
        assertEquals(3.0, candles.single().volume, 1e-12)
        assertTrue(candles.single().closed)
        val request = requestBodies.map { Json.parseToJsonElement(it) }
            .first { it.jsonObject["type"]?.jsonPrimitive?.content == "candleSnapshot" }.jsonObject
        val req = request.getValue("req").jsonObject
        assertEquals("1m", req.getValue("interval").jsonPrimitive.content)
        assertEquals("0", req.getValue("startTime").jsonPrimitive.content)
        assertEquals("99999", req.getValue("endTime").jsonPrimitive.content)
    }

    @Test
    fun `required metadata failure aborts the catalog snapshot`() = runTest {
        failOutcomeMeta.set(true)

        val error = runCatching { adapter.listMarkets() }.exceptionOrNull()

        assertTrue(error is ExchangeHttpException)
        assertEquals(503, (error as ExchangeHttpException).code)
    }

    private fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()

    private fun trades(coin: String) = """
        [
          {"coin":"$coin","px":"${tradePrice(coin, newest = false)}","time":1787415255000},
          {"coin":"$coin","px":"${tradePrice(coin, newest = true)}","time":1787415256000}
        ]
    """.trimIndent()

    private fun tradePrice(coin: String, newest: Boolean): String = when (coin) {
        "#8010" -> if (newest) "0.625" else "0.6"
        else -> if (newest) "12.5" else "11.5"
    }

    private fun perpMetadata(dex: String): String = when (dex) {
        "" -> """
            [{"universe":[{"name":"BTC","szDecimals":5},{"name":"ETH","szDecimals":3},
                            {"name":"FUTURECOIN","szDecimals":2}]},
             [{"prevDayPx":"60000","dayNtlVlm":"1000","dayBaseVlm":"0.5"},
              {"prevDayPx":"3000","dayNtlVlm":"2000","dayBaseVlm":"2"},
              {"prevDayPx":"1","dayNtlVlm":"0","dayBaseVlm":"0"}]]
        """.trimIndent()
        "xyz" -> """
            [{"universe":[{"name":"xyz:ANT","szDecimals":2},
                            {"name":"xyz:DELISTED","szDecimals":2,"isDelisted":true}]},
             [{"prevDayPx":"10","dayNtlVlm":"200","dayBaseVlm":"20"},
              {"prevDayPx":"1","dayNtlVlm":"0","dayBaseVlm":"0"}]]
        """.trimIndent()
        "para" -> """
            [{"universe":[{"name":"para:10Y","szDecimals":1}]},
             [{"prevDayPx":"4.2","dayNtlVlm":"300","dayBaseVlm":"50"}]]
        """.trimIndent()
        "mkts" -> """
            [{"universe":[{"name":"mkts:BVIV","szDecimals":2}]},
             [{"prevDayPx":"50","dayNtlVlm":"400","dayBaseVlm":"8"}]]
        """.trimIndent()
        else -> error("unexpected dex $dex")
    }

    private companion object {
        const val SPOT_META_AND_CONTEXTS = """
            [
              {"universe":[
                {"tokens":[2,0],"name":"@107","index":107,"isCanonical":false},
                {"tokens":[1,0],"name":"@264","index":264,"isCanonical":false}],
               "tokens":[
                {"name":"USDC","index":0,"szDecimals":6,"tokenId":"0x0000000000000000000000000000000000000001"},
                {"name":"TSLA","index":1,"szDecimals":6,"tokenId":"0x0000000000000000000000000000000000000264","fullName":"Tesla","deployerLabel":"eqx"},
                {"name":"ANT","index":2,"szDecimals":2,"tokenId":"0x0000000000000000000000000000000000000107","fullName":"ANT token"}]},
              [
                {"coin":"@264","prevDayPx":"100","dayNtlVlm":"200","dayBaseVlm":"2"},
                {"coin":"#9990","prevDayPx":"0.5","dayNtlVlm":"1","dayBaseVlm":"2"},
                {"coin":"@107","prevDayPx":"0.9","dayNtlVlm":"90","dayBaseVlm":"100"}]
            ]
        """

        const val PERP_DEXES = """[null,{"name":"xyz"},{"name":"para"},{"name":"mkts"}]"""

        const val PERP_CATEGORIES = """
            [["xyz:ANT","preipo"],["para:10Y","rates"],["mkts:BVIV","crypto"]]
        """

        const val OUTCOME_META = """
            {
              "outcomes":[
                {"outcome":801,"name":"sportsContestParticipant2:Coventry","description":"participant:Coventry",
                 "sideSpecs":[{"name":"Yes"},{"name":"No"}],"quoteToken":"USDC","venue":"sports"},
                {"outcome":802,"name":"fallback:Draw","description":"participant:Draw",
                 "sideSpecs":[{"name":"Yes"},{"name":"No"}],"quoteToken":"USDC","venue":"sports"},
                {"outcome":803,"name":"template:priceBinary","description":"index:0",
                 "sideSpecs":[{"name":"Yes"},{"name":"No"}],"quoteToken":"USDC","venue":"crypto"},
                {"outcome":804,"name":"template fallback","description":"",
                 "sideSpecs":[{"name":"Yes"},{"name":"No"}],"quoteToken":"USDC","venue":"sports"},
                {"outcome":40268,"name":"Recurring Fallback","description":"other",
                 "sideSpecs":[{"name":"Yes"},{"name":"No"}],"quoteToken":"USDC","venue":"crypto"},
                {"outcome":40269,"name":"Recurring Named Outcome","description":"index:0",
                 "sideSpecs":[{"name":"Yes"},{"name":"No"}],"quoteToken":"USDC","venue":"crypto"},
                {"outcome":40270,"name":"Recurring Named Outcome","description":"index:1",
                 "sideSpecs":[{"name":"Yes"},{"name":"No"}],"quoteToken":"USDC","venue":"crypto"},
                {"outcome":40271,"name":"Recurring Named Outcome","description":"index:2",
                 "sideSpecs":[{"name":"Yes"},{"name":"No"}],"quoteToken":"USDC","venue":"crypto"},
                {"outcome":40264,"name":"Recurring","description":"class:priceBinary|underlying:BTC|expiry:20261120-0600|targetPrice:63330|period:1d",
                 "sideSpecs":[{"name":"Yes"},{"name":"No"}],"quoteToken":"USDC","venue":"crypto"},
                {"outcome":40265,"name":"Recurring","description":"class:priceBinary|underlying:ETH|expiry:20261120-0600|targetPrice:1799.100000001|period:1d",
                 "sideSpecs":[{"name":"Yes"},{"name":"No"}],"quoteToken":"USDC","venue":"crypto"},
                {"outcome":40266,"name":"Recurring","description":"class:priceBinary|underlying:BTC|expiry:20261121-0600|targetPrice:64000|period:1d",
                 "sideSpecs":[{"name":"Yes"},{"name":"No"}],"quoteToken":"USDC","venue":"crypto"},
                {"outcome":40267,"name":"Recurring","description":"class:priceBinary|underlying:BTC|expiry:20261121-1200|targetPrice:64000|period:1d",
                 "sideSpecs":[{"name":"Yes"},{"name":"No"}],"quoteToken":"USDC","venue":"crypto"}
              ],
              "questions":[
                {"question":901,"name":"Coventry v Palace: match winner","description":"expiry:20261120-1200|scheduledStart:20261120-0900",
                 "fallbackOutcome":801,"namedOutcomes":[801],"settledNamedOutcomes":[]},
                {"question":902,"name":"Coventry v Palace: draw","description":"expiry:20261120-1200",
                 "fallbackOutcome":802,"namedOutcomes":[],"settledNamedOutcomes":[802]},
                {"question":903,"name":"Recurring","description":"class:priceBinary|underlying:BTC|priceThresholds:80000,90000|expiry:20261231-1700|scheduledStart:20261231-1200",
                 "fallbackOutcome":803,"namedOutcomes":[803],"settledNamedOutcomes":[]},
                {"question":904,"name":"2026/2027 NBA winner","description":"expiry:20270630-0000",
                 "fallbackOutcome":804,"namedOutcomes":[805,806],"settledNamedOutcomes":[]},
                {"question":674,"name":"Recurring","description":"class:priceBucket|underlying:BTC|expiry:20261120-0600|priceThresholds:62063,64596|period:1d",
                 "fallbackOutcome":40268,"namedOutcomes":[40269,40270,40271],"settledNamedOutcomes":[]}
              ]
            }
        """

        const val OUTCOME_TEMPLATES = """
            [
              {"id":"sportsContestParticipant2","name":"{participant}","description":"Participant in event"},
              {"id":"priceBinary","name":"{underlying} price bucket {priceThresholds}","description":"At {expiry} UTC"},
              {"id":"priceBucket","name":"{underlying} price bucket {priceThresholds}","description":"At {expiry} UTC"}
            ]
        """

        const val CANDLE_SNAPSHOT = """
            [{"t":1000,"T":1999,"s":"BTC","i":"1m","o":"10","h":"15","l":"9","c":"12","v":"3"}]
        """

        object InstantTimes {
            const val EVENT_EXPIRY = 1_795_176_000_000L
            const val RECURRING_EXPIRY = 1_798_736_400_000L
            const val PRICE_BUCKET_EXPIRY = 1_795_154_400_000L
        }
    }
}
