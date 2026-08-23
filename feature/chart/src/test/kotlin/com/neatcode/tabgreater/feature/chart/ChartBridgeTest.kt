package com.neatcode.tabgreater.feature.chart

import androidx.webkit.JavaScriptExecutionException
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewOutcomeReceiver
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

/**
 * The bridge halves that are pure Kotlin: the ready gate ([ChartBridge.awaitReady]) and the live
 * bar stream's pause/resume. The RPC replies themselves go through a main-looper `Handler`, which
 * the JVM stubs turn into a no-op, so they are covered by `ChartProtocolTest` instead.
 */
class ChartBridgeTest {

    private lateinit var scope: CoroutineScope
    private lateinit var adapter: FakeAdapter
    private lateinit var bridge: ChartBridge

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        adapter = FakeAdapter()
        bridge = ChartBridge(
            scope = scope,
            registry = ExchangeRegistry(listOf(adapter)),
            markets = FakeMarkets(BTC_EUR),
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

        val BTC_EUR = Market(
            key = MarketKey.of(ExchangeId.BINANCE, "BTC", "EUR"),
            nativeSymbol = "BTCEUR",
            pricePrecision = 2,
        )
    }
}
