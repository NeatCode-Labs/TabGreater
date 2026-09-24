package com.neatcode.tabgreater.feature.chart

import androidx.webkit.JavaScriptExecutionException
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewOutcomeReceiver
import com.neatcode.tabgreater.core.data.repo.ChartDrawingRepository
import com.neatcode.tabgreater.core.data.repo.MarketRepository
import com.neatcode.tabgreater.core.exchange.ExchangeAdapter
import com.neatcode.tabgreater.core.exchange.ExchangeRegistry
import com.neatcode.tabgreater.core.model.Candle
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.Ticker
import com.neatcode.tabgreater.core.model.Timeframe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * The bridge halves that are pure Kotlin: the ready gate ([ChartBridge.awaitReady]), the live
 * bar stream's pause/resume and the drawing notices (persistence and [ChartBridge.drawingState]). The RPC replies themselves go through a main-looper `Handler`, which
 * the JVM stubs turn into a no-op, so they are covered by `ChartProtocolTest` instead.
 */
class ChartBridgeTest {

    private lateinit var scope: CoroutineScope
    private lateinit var adapter: FakeAdapter
    private lateinit var drawings: FakeDrawings
    private lateinit var bridge: ChartBridge

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        adapter = FakeAdapter()
        drawings = FakeDrawings()
        bridge = ChartBridge(
            scope = scope,
            registry = ExchangeRegistry(listOf(adapter)),
            markets = FakeMarkets(BTC_EUR),
            drawings = drawings,
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    // ------------------------------------------------------------------ ready gate

    @Test
    fun `every waiter is resumed when the page reports ready`() = runBlocking {
        val first = async { bridge.awaitReady() }
        val second = async { bridge.awaitReady() }
        yield()

        bridge.handle(READY_MESSAGE, NoReply)

        withTimeout(TIMEOUT_MS) { first.await() }
        withTimeout(TIMEOUT_MS) { second.await() }
        assertTrue(bridge.isReady)
    }

    @Test
    fun `cancelling one waiter leaves the others waiting for the same page`() = runBlocking {
        val abandoned = launch { bridge.awaitReady() }
        val survivor = async { bridge.awaitReady() }
        yield()
        // Exactly the F4 sequence: chart A is disposed at the end of the nav transition while
        // chart B, composed before it, is still waiting for the very first page load.
        abandoned.cancelAndJoin()

        bridge.handle(READY_MESSAGE, NoReply)

        withTimeout(TIMEOUT_MS) { survivor.await() }
    }

    @Test
    fun `awaitReady returns at once when ready and blocks again after a reload`() = runBlocking {
        bridge.handle(READY_MESSAGE, NoReply)
        withTimeout(TIMEOUT_MS) { bridge.awaitReady() }

        bridge.onPageStarted()

        assertFalse(bridge.isReady)
        assertNull(withTimeoutOrNull(SHORT_MS) { bridge.awaitReady() })
        bridge.handle(READY_MESSAGE, NoReply)
        withTimeout(TIMEOUT_MS) { bridge.awaitReady() }
    }

    // ---------------------------------------------------------------- live stream

    @Test
    fun `pauseLive stops the kline stream and resumeLive replays the same subscription`() = runBlocking {
        bridge.handle(subscribeMessage(), NoReply)
        assertEquals(Timeframe.H1 to BTC_EUR.key, awaitSubscription())

        bridge.pauseLive()
        assertTrue(withTimeout(TIMEOUT_MS) { adapter.cancellations.receive() })

        bridge.resumeLive()
        // No getBars round is involved: the remembered request re-opens the same kline stream.
        assertEquals(Timeframe.H1 to BTC_EUR.key, awaitSubscription())
        assertEquals(2, adapter.subscribeCount)
    }

    @Test
    fun `resumeLive does nothing when the chart was never subscribed or was closed`() = runBlocking {
        bridge.resumeLive()
        assertNull(withTimeoutOrNull(SHORT_MS) { adapter.subscriptions.receive() })

        bridge.handle(subscribeMessage(), NoReply)
        awaitSubscription()
        bridge.pauseLive()
        withTimeout(TIMEOUT_MS) { adapter.cancellations.receive() }

        // `close()` is the real teardown (screen disposed / page reloaded): nothing to resume.
        bridge.close()
        bridge.resumeLive()
        assertNull(withTimeoutOrNull(SHORT_MS) { adapter.subscriptions.receive() })
        assertEquals(1, adapter.subscribeCount)
    }

    @Test
    fun `unsubscribeBar clears the remembered request`() = runBlocking {
        bridge.handle(subscribeMessage(), NoReply)
        awaitSubscription()

        bridge.handle("""{"id":"r2","action":"unsubscribeBar","payload":{}}""", NoReply)
        withTimeout(TIMEOUT_MS) { adapter.cancellations.receive() }

        bridge.resumeLive()
        assertNull(withTimeoutOrNull(SHORT_MS) { adapter.subscriptions.receive() })
    }

    // ------------------------------------------------------------------- drawings

    @Test
    fun `drawingsChanged persists the sanitised set under the canonical market key`() = runBlocking {
        bridge.handle(drawingsChanged("binance", "BTC/EUR", "$SEGMENT,$UNKNOWN_TOOL"), NoReply)

        val (key, json) = withTimeout(TIMEOUT_MS) { drawings.saved.receive() }
        assertEquals(MarketKey("binance:BTC/EUR"), key)
        val stored = DrawingsCodec.decode(json)
        assertEquals(listOf("segment"), stored.map { it.name })
        assertEquals(63120.5, stored.single().points.first().value!!, 0.0)
    }

    @Test
    fun `saves keep the order the page reported them in`() = runBlocking {
        bridge.handle(drawingsChanged("binance", "BTC/EUR", SEGMENT), NoReply)
        bridge.handle(drawingsChanged("binance", "BTC/EUR", ""), NoReply)

        withTimeout(TIMEOUT_MS) { drawings.saved.receive() }
        val (_, last) = withTimeout(TIMEOUT_MS) { drawings.saved.receive() }
        assertEquals("[]", last)
    }

    @Test
    fun `drawingsChanged without a valid market is ignored`() = runBlocking {
        bridge.handle(drawingsChanged("", "BTC/EUR", SEGMENT), NoReply)
        bridge.handle(drawingsChanged("binance", "BTCEUR", SEGMENT), NoReply)
        bridge.handle("""{"action":"drawingsChanged","payload":{"drawings":[]}}""", NoReply)

        assertNull(withTimeoutOrNull(SHORT_MS) { drawings.saved.receive() })
    }

    @Test
    fun `drawingState notices update the flow and reloads or market swaps reset it`() = runBlocking {
        bridge.handle(
            """{"action":"drawingState","payload":{"drawing":false,"tool":null,"selectedId":"o7",""" +
                """"selectedName":"rect","selectedLocked":true,"count":3,"needsTextId":null}}""",
            NoReply,
        )
        assertEquals(
            DrawingState(selectedId = "o7", selectedName = "rect", selectedLocked = true, count = 3),
            bridge.drawingState.value,
        )

        bridge.onMarketChanged()
        assertEquals(DrawingState.IDLE, bridge.drawingState.value)

        // Missing fields decode to their defaults.
        bridge.handle("""{"action":"drawingState","payload":{"drawing":true,"tool":"segment"}}""", NoReply)
        assertEquals(DrawingState(drawing = true, tool = "segment"), bridge.drawingState.value)

        bridge.onPageStarted()
        assertEquals(DrawingState.IDLE, bridge.drawingState.first())
    }

    @Test
    fun `drawingState carries the text tool and the plot area the strip is placed against`() = runBlocking {
        bridge.handle(
            """{"action":"drawingState","payload":{"drawing":false,"selectedId":"o9","selectedName":"simpleTag",""" +
                """"count":1,"needsTextId":"o9","needsTextTool":"simpleTag","visible":true,""" +
                """"plotLeft":0,"plotBottom":452,"plotWidth":303,"legendBottom":39}}""",
            NoReply,
        )
        val state = bridge.drawingState.value
        assertEquals("simpleTag", state.needsTextTool)
        assertEquals(0, state.plotLeft)
        assertEquals(452, state.plotBottom)
        assertEquals(303, state.plotWidth)
        assertEquals(39, state.legendBottom)
        assertEquals(true, state.busy)
        assertEquals(false, DrawingState.IDLE.busy)
    }

    @Test
    fun `drawingsPayloadFor reads Room and names the market the way the page does`() = runBlocking {
        drawings.rows[BTC_EUR.key] = "[$SEGMENT]"

        val payload = decodePayload(bridge.drawingsPayloadFor(BTC_EUR))

        assertEquals("binance", payload.exchange)
        assertEquals("BTC/EUR", payload.ticker)
        assertEquals(listOf("segment"), payload.drawings.map { it.name })
    }

    @Test
    fun `drawingsPayloadFor prefers the newest reported set over a save still in flight`() = runBlocking {
        drawings.rows[BTC_EUR.key] = "[$SEGMENT]"
        drawings.blockSaves = true

        bridge.handle(drawingsChanged("binance", "BTC/EUR", ""), NoReply)

        assertEquals(emptyList<Drawing>(), decodePayload(bridge.drawingsPayloadFor(BTC_EUR)).drawings)
    }

    @Test
    fun `drawingsPayloadFor is empty for a market without drawings and applies the magnet`() = runBlocking {
        assertEquals(emptyList<Drawing>(), decodePayload(bridge.drawingsPayloadFor(BTC_EUR)).drawings)

        drawings.rows[BTC_EUR.key] = "[$SEGMENT]"
        val restored = decodePayload(bridge.drawingsPayloadFor(BTC_EUR, MagnetMode.NONE)).drawings
        assertEquals(listOf("normal"), restored.map { it.mode })
    }

    @Test
    fun `a failing Room read restores nothing instead of crashing the swap`() = runBlocking {
        drawings.failLoads = true
        assertEquals(emptyList<Drawing>(), decodePayload(bridge.drawingsPayloadFor(BTC_EUR)).drawings)
    }

    private fun decodePayload(raw: String): DrawingsPayload =
        ChartProtocol.json.decodeFromString(DrawingsPayload.serializer(), raw)

    private fun drawingsChanged(exchange: String, ticker: String, drawingsJson: String): String =
        """{"action":"drawingsChanged","payload":{"exchange":"$exchange","ticker":"$ticker",""" +
            """"drawings":[$drawingsJson]}}"""

    private suspend fun awaitSubscription(): Pair<Timeframe, MarketKey> =
        withTimeout(TIMEOUT_MS) { adapter.subscriptions.receive() }

    private fun subscribeMessage(): String =
        """{"id":"r1","action":"subscribeBar","payload":""" +
            """{"exchange":"binance","ticker":"BTC/EUR","instId":"BTCEUR","span":1,"unit":"hour"}}"""

    /** Records every `watchKlines` subscription and parks until cancelled. */
    private class FakeAdapter : ExchangeAdapter {
        val subscriptions = Channel<Pair<Timeframe, MarketKey>>(Channel.UNLIMITED)
        val cancellations = Channel<Boolean>(Channel.UNLIMITED)

        @Volatile
        var subscribeCount = 0

        override val id: ExchangeId = ExchangeId.BINANCE
        override val nativeTimeframes: Set<Timeframe> = Timeframe.entries.toSet()

        override suspend fun listMarkets(): List<Market> = emptyList()
        override suspend fun fetchTickers(markets: List<Market>): List<Ticker> = emptyList()
        override suspend fun fetchOHLCV(
            market: Market,
            timeframe: Timeframe,
            endTime: Long?,
            limit: Int,
        ): List<Candle> = emptyList()

        override fun watchTickers(markets: List<Market>): Flow<Ticker> = emptyFlow()

        override fun watchKlines(market: Market, timeframe: Timeframe): Flow<Candle> = channelFlow {
            subscribeCount++
            subscriptions.send(timeframe to market.key)
            try {
                awaitCancellation()
            } finally {
                cancellations.trySend(true)
            }
        }
    }

    private class FakeMarkets(private vararg val known: Market) : MarketRepository {
        override suspend fun refreshMarkets(exchange: ExchangeId, force: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun refreshAll(force: Boolean) = Unit
        override suspend fun getMarket(key: MarketKey): Market? = known.firstOrNull { it.key == key }
        override suspend fun getMarkets(keys: Collection<MarketKey>): Map<MarketKey, Market> =
            known.filter { it.key in keys }.associateBy { it.key }

        override suspend fun search(query: String, limit: Int): List<Market> = emptyList()
    }

    /** In-memory drawings; every save is also reported on [saved], in order. */
    private class FakeDrawings : ChartDrawingRepository {
        val rows = ConcurrentHashMap<MarketKey, String>()
        val saved = Channel<Pair<MarketKey, String>>(Channel.UNLIMITED)

        @Volatile
        var blockSaves = false

        @Volatile
        var failLoads = false

        override suspend fun load(key: MarketKey): String? {
            check(!failLoads) { "disk unreadable" }
            return rows[key]
        }

        override suspend fun save(key: MarketKey, drawingsJson: String, now: Long) {
            if (blockSaves) awaitCancellation()
            rows[key] = drawingsJson
            saved.send(key to drawingsJson)
        }

        override suspend fun clear(key: MarketKey) {
            rows.remove(key)
        }
    }

    /** `handle` needs a proxy for the reply path; the ready/subscribe cases never read it. */
    private object NoReply : JavaScriptReplyProxy() {
        override fun postMessage(message: String) = Unit
        override fun postMessage(message: ByteArray) = Unit
        override fun executeJavaScript(
            script: String,
            receiver: WebViewOutcomeReceiver<String, JavaScriptExecutionException>?,
        ) = Unit
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val SHORT_MS = 200L
        const val READY_MESSAGE = """{"action":"ready","payload":{}}"""
        const val SEGMENT =
            """{"name":"segment","points":[{"timestamp":1727100000000,"value":63120.5},""" +
                """{"timestamp":1727200000000,"value":64000}],"lock":false,"mode":"weak_magnet","text":null}"""
        const val UNKNOWN_TOOL = """{"name":"spaceship","points":[{"timestamp":1,"value":2}]}"""

        val BTC_EUR = Market(
            key = MarketKey.of(ExchangeId.BINANCE, "BTC", "EUR"),
            nativeSymbol = "BTCEUR",
            pricePrecision = 2,
        )
    }
}
