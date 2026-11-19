package com.neatcode.tabgreater.ui.watchlist

import com.neatcode.tabgreater.core.exchange.ExchangeFailureKind
import com.neatcode.tabgreater.core.live.MarketDataRepository
import com.neatcode.tabgreater.core.live.MarketState
import com.neatcode.tabgreater.core.live.PriceFreshness
import com.neatcode.tabgreater.core.live.RefreshResult
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.InstrumentKind
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.Ticker
import com.neatcode.tabgreater.ui.testing.FakeAppSettings
import com.neatcode.tabgreater.ui.testing.FakeMarketDataRepository
import com.neatcode.tabgreater.ui.testing.FakeMarketRepository
import com.neatcode.tabgreater.ui.testing.FakeSparklineRepository
import com.neatcode.tabgreater.ui.testing.FakeWatchlistRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchlistPriceStatusTest {

    private val dispatcher = StandardTestDispatcher()
    private val watchlists = FakeWatchlistRepository()
    private val markets = FakeMarketRepository()
    private val sparklines = FakeSparklineRepository()
    private val live = StatusMarketDataRepository()
    private val settings = FakeAppSettings()

    private val btc = MarketKey("binance:BTC/USDT")
    private val eth = MarketKey("binance:ETH/USDT")

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `old prices refresh to current after same-price confirmation`() = runTest(dispatcher) {
        val listId = watchlists.ensureDefault()
        watchlists.addItems(listId, listOf(btc))
        live.states.value = mapOf(btc to marketState(btc, 100.0, PriceFreshness.OLD))

        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()

        assertEquals(PriceRefreshStatus.REFRESHING, vm.uiState.value.priceRefreshStatus)
        assertTrue("foreground recovery uses the existing observation", live.refreshRequests.isEmpty())

        // Confirmation refreshes age/freshness even though the displayed price is unchanged.
        live.states.value = mapOf(btc to marketState(btc, 100.0, PriceFreshness.CURRENT))
        runCurrent()

        assertEquals(PriceRefreshStatus.CURRENT, vm.uiState.value.priceRefreshStatus)
        assertTrue(vm.uiState.value.priceIssues.isEmpty())
    }

    @Test
    fun `unconfirmed prices become stale when foreground grace expires`() = runTest(dispatcher) {
        val listId = watchlists.ensureDefault()
        watchlists.addItems(listId, listOf(btc))
        live.states.value = mapOf(btc to marketState(btc, 100.0, PriceFreshness.OLD))

        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()

        assertEquals(PriceRefreshStatus.REFRESHING, vm.uiState.value.priceRefreshStatus)
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(PriceRefreshStatus.REFRESHING, vm.uiState.value.priceRefreshStatus)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(PriceRefreshStatus.STALE, vm.uiState.value.priceRefreshStatus)
        assertEquals(PriceIssueKind.OLD, vm.uiState.value.priceIssues.single().kind)
    }

    @Test
    fun `known market failure is error immediately during grace`() = runTest(dispatcher) {
        val listId = watchlists.ensureDefault()
        watchlists.addItems(listId, listOf(btc))
        live.states.value = mapOf(
            btc to marketState(btc, 100.0, PriceFreshness.CURRENT, ExchangeFailureKind.TRANSIENT),
        )

        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()

        assertEquals(PriceRefreshStatus.ERROR, vm.uiState.value.priceRefreshStatus)
        assertEquals(PriceIssueKind.ERROR, vm.uiState.value.priceIssues.single().kind)
    }

    @Test
    fun `mixed current and absent state reports only the unconfirmed market`() = runTest(dispatcher) {
        val listId = watchlists.ensureDefault()
        watchlists.addItems(listId, listOf(btc, eth))
        live.states.value = mapOf(btc to marketState(btc, 100.0, PriceFreshness.CURRENT))

        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()

        assertEquals(PriceRefreshStatus.REFRESHING, vm.uiState.value.priceRefreshStatus)
        assertEquals(listOf(eth), vm.uiState.value.priceIssues.map { it.key })
        assertEquals(PriceIssueKind.UNCONFIRMED, vm.uiState.value.priceIssues.single().kind)

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(PriceRefreshStatus.STALE, vm.uiState.value.priceRefreshStatus)
        assertEquals(listOf(eth), vm.uiState.value.priceIssues.map { it.key })
    }

    @Test
    fun `empty watchlist remains empty without a foreground spinner`() = runTest(dispatcher) {
        watchlists.ensureDefault()
        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()

        assertEquals(PriceRefreshStatus.EMPTY, vm.uiState.value.priceRefreshStatus)
        assertTrue(vm.uiState.value.priceIssues.isEmpty())
        assertFalse(vm.uiState.value.refreshInProgress)
    }

    @Test
    fun `returning to foreground starts a fresh grace period`() = runTest(dispatcher) {
        val listId = watchlists.ensureDefault()
        watchlists.addItems(listId, listOf(btc))
        live.states.value = mapOf(btc to marketState(btc, 100.0, PriceFreshness.OLD))

        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()
        advanceTimeBy(4_000)
        runCurrent()

        vm.onBackground()
        runCurrent()
        vm.onForeground()
        runCurrent()
        assertEquals(PriceRefreshStatus.REFRESHING, vm.uiState.value.priceRefreshStatus)

        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(PriceRefreshStatus.REFRESHING, vm.uiState.value.priceRefreshStatus)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(PriceRefreshStatus.STALE, vm.uiState.value.priceRefreshStatus)
    }

    @Test
    fun `watchlist change resets the grace period for its keys`() = runTest(dispatcher) {
        val first = watchlists.ensureDefault()
        val second = watchlists.createWatchlist("Second")
        watchlists.addItems(first, listOf(btc))
        watchlists.addItems(second, listOf(eth))
        live.states.value = mapOf(btc to marketState(btc, 100.0, PriceFreshness.OLD))

        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()
        advanceTimeBy(4_000)
        vm.selectWatchlist(second)
        runCurrent()

        assertEquals(PriceRefreshStatus.REFRESHING, vm.uiState.value.priceRefreshStatus)
        assertEquals(listOf(eth), vm.uiState.value.priceIssues.map { it.key })
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(PriceRefreshStatus.REFRESHING, vm.uiState.value.priceRefreshStatus)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(PriceRefreshStatus.STALE, vm.uiState.value.priceRefreshStatus)
    }

    @Test
    fun `retry is single flight and clears progress after result`() = runTest(dispatcher) {
        val listId = watchlists.ensureDefault()
        watchlists.addItems(listId, listOf(btc))
        live.states.value = mapOf(btc to marketState(btc, 100.0, PriceFreshness.OLD))
        val result = CompletableDeferred<RefreshResult>()
        live.refreshResultHandler = { result.await() }

        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()
        vm.refresh()
        vm.refresh()
        runCurrent()

        assertEquals(1, live.refreshRequests.size)
        assertTrue(vm.uiState.value.refreshInProgress)
        assertEquals(PriceRefreshStatus.REFRESHING, vm.uiState.value.priceRefreshStatus)

        live.states.value = mapOf(btc to marketState(btc, 100.0, PriceFreshness.CURRENT))
        result.complete(RefreshResult(successful = setOf(btc)))
        runCurrent()

        assertFalse(vm.uiState.value.refreshInProgress)
        assertEquals(PriceRefreshStatus.CURRENT, vm.uiState.value.priceRefreshStatus)
    }

    @Test
    fun `refresh result failures and thrown exceptions remain visible after cleanup`() = runTest(dispatcher) {
        val listId = watchlists.ensureDefault()
        watchlists.addItems(listId, listOf(btc))
        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()

        live.refreshResultHandler = { keys ->
            RefreshResult(failures = mapOf(keys.single() to IllegalStateException("REST failed")))
        }
        vm.refresh()
        runCurrent()
        assertFalse(vm.uiState.value.refreshInProgress)
        assertEquals(PriceRefreshStatus.ERROR, vm.uiState.value.priceRefreshStatus)
        assertEquals(PriceIssueKind.ERROR, vm.uiState.value.priceIssues.single().kind)

        live.refreshResultHandler = { keys -> RefreshResult(missing = keys.toSet()) }
        vm.refresh()
        runCurrent()
        assertFalse(vm.uiState.value.refreshInProgress)
        assertEquals(PriceRefreshStatus.ERROR, vm.uiState.value.priceRefreshStatus)
        assertEquals(PriceIssueKind.ERROR, vm.uiState.value.priceIssues.single().kind)

        live.refreshResultHandler = { throw IllegalStateException("refresh threw") }
        vm.refresh()
        runCurrent()
        assertFalse(vm.uiState.value.refreshInProgress)
        assertEquals(PriceRefreshStatus.ERROR, vm.uiState.value.priceRefreshStatus)
        assertEquals(PriceIssueKind.ERROR, vm.uiState.value.priceIssues.single().kind)
    }

    @Test
    fun `watchlist switch cancels an in-flight retry and clears progress`() = runTest(dispatcher) {
        val first = watchlists.ensureDefault()
        val second = watchlists.createWatchlist("Second")
        watchlists.addItems(first, listOf(btc))
        watchlists.addItems(second, listOf(eth))
        val pending = CompletableDeferred<RefreshResult>()
        live.refreshResultHandler = { pending.await() }

        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()
        vm.refresh()
        runCurrent()
        assertTrue(vm.uiState.value.refreshInProgress)

        vm.selectWatchlist(second)
        runCurrent()

        assertFalse(vm.uiState.value.refreshInProgress)
        assertEquals(listOf(eth), vm.uiState.value.tiles.map { it.key })
    }

    @Test
    fun `failed retry clears immediately on a new confirmation at the same price`() = runTest(dispatcher) {
        val id = watchlists.ensureDefault()
        watchlists.addItems(id, listOf(btc))
        val confirmed = marketState(btc, 100.0, PriceFreshness.CURRENT)
        live.states.value = mapOf(btc to confirmed)
        live.refreshResultHandler = { RefreshResult(failures = mapOf(btc to IllegalStateException("failed"))) }
        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()
        vm.refresh()
        runCurrent()
        assertEquals(PriceRefreshStatus.ERROR, vm.uiState.value.priceRefreshStatus)
        live.states.value = mapOf(btc to confirmed.copy(confirmedAtElapsedMs = 1))
        runCurrent()
        assertEquals(PriceRefreshStatus.CURRENT, vm.uiState.value.priceRefreshStatus)
        assertTrue(vm.uiState.value.priceIssues.isEmpty())
    }

    @Test
    fun `Hyperliquid successful price response can resolve an error while its last trade stays old`() = runTest(dispatcher) {
        val key = MarketKey("hyperliquid:OUTCOME_37275_YES/USDC")
        val id = watchlists.ensureDefault()
        watchlists.addItems(id, listOf(key))
        markets.put(
            Market(
                key = key,
                nativeSymbol = "#372750",
                pricePrecision = 3,
                instrumentKind = InstrumentKind.OUTCOME,
                displayName = "Will Team A win? · Yes",
                outcomeSide = "Yes",
                unit = "USDC per outcome token",
            ),
        )
        val original = marketState(key, 0.42, PriceFreshness.CURRENT, tradeTimestamp = 1_000L)
        live.states.value = mapOf(key to original)
        live.refreshResultHandler = { RefreshResult(failures = mapOf(key to IllegalStateException("offline"))) }

        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()
        vm.refresh()
        runCurrent()

        val issue = vm.uiState.value.priceIssues.single()
        assertEquals("Yes · Will Team A win?", issue.marketLabel)
        assertEquals(1_000L, issue.lastTradeAtEpochMs)
        assertEquals("0.42 USDC/token", issue.lastTradePrice)
        assertEquals(ExchangeFailureKind.TRANSIENT, issue.failure)
        assertEquals(PriceIssueKind.ERROR, issue.kind)

        // An earlier execution still merits an age warning after a successful price request.
        live.states.value = mapOf(
            key to original.copy(confirmedAtElapsedMs = 1L, confirmedAtEpochMs = 2_000L, freshness = PriceFreshness.OLD),
        )
        runCurrent()
        assertEquals(PriceIssueKind.OLD, vm.uiState.value.priceIssues.single().kind)
        assertEquals(null, vm.uiState.value.priceIssues.single().failure)

        live.states.value = mapOf(
            key to original.copy(
                ticker = original.ticker!!.copy(timestamp = 1_001L),
                confirmedAtElapsedMs = 2L,
                confirmedAtEpochMs = 2_001L,
            ),
        )
        runCurrent()
        assertTrue(vm.uiState.value.priceIssues.isEmpty())
        assertEquals(PriceRefreshStatus.CURRENT, vm.uiState.value.priceRefreshStatus)
    }

    @Test
    fun `event card carries side first label unit and explicit inactive status`() = runTest(dispatcher) {
        val key = MarketKey("hyperliquid:OUTCOME_37275_NO/USDC")
        val secondKey = MarketKey("hyperliquid:OUTCOME_37276_NO/USDC")
        val id = watchlists.ensureDefault()
        watchlists.addItems(id, listOf(key, secondKey))
        markets.put(
            Market(
                key = key,
                nativeSymbol = "#372751",
                pricePrecision = 4,
                instrumentKind = InstrumentKind.OUTCOME,
                displayName = "2026/2027 NBA winner · Oklahoma City Thunder · No",
                outcomeSide = "No",
                unit = "USDC per outcome token",
                eventId = "nba-2026-winner",
                active = false,
            ),
        )
        markets.put(
            Market(
                key = secondKey,
                nativeSymbol = "#372761",
                pricePrecision = 4,
                instrumentKind = InstrumentKind.OUTCOME,
                displayName = "2026/2027 NBA winner · Boston Celtics · No",
                outcomeSide = "No",
                unit = "USDC per outcome token",
                eventId = "nba-2026-winner",
                active = false,
            ),
        )
        live.states.value = mapOf(
            key to marketState(key, 0.5, PriceFreshness.OLD, tradeTimestamp = 1_000L),
            secondKey to marketState(secondKey, 0.4, PriceFreshness.OLD, tradeTimestamp = 1_000L),
        )
        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()

        val tiles = vm.uiState.value.tiles.associateBy { it.key }
        val tile = tiles.getValue(key)
        assertEquals("No · Oklahoma City Thunder\n2026/2027 NBA winner", tile.pair)
        assertEquals("No · Boston Celtics\n2026/2027 NBA winner", tiles.getValue(secondKey).pair)
        assertTrue(tile.isEventOutcome)
        assertEquals("USDC", tile.marketContext)
        assertEquals("0.5", tile.priceText)
        assertEquals("Inactive", tile.statusLabel)
        assertTrue(tile.statusDescription.contains("inactive"))
        assertTrue("inactive listings do not keep active-price warnings lit", vm.uiState.value.priceIssues.isEmpty())
    }

    @Test
    fun `expired daily outcome is marked ended and excluded from active price issues`() = runTest(dispatcher) {
        val key = MarketKey("hyperliquid:OUTCOME_95_SIDE_0/USDC")
        val expiry = java.time.Instant.parse("2026-11-20T06:00:00Z").toEpochMilli()
        val now = expiry + 1L
        val id = watchlists.ensureDefault()
        watchlists.addItems(id, listOf(key))
        markets.put(
            Market(
                key = key,
                nativeSymbol = "#950",
                pricePrecision = 4,
                instrumentKind = InstrumentKind.OUTCOME,
                displayName = "BTC above 63,330 · Yes",
                outcomeSide = "Yes",
                expiresAt = expiry,
                rolloverSeriesId = "priceBinary:BTC:1d:21600000",
            ),
        )
        live.states.value = mapOf(key to marketState(key, 0.42, PriceFreshness.OLD, tradeTimestamp = expiry - 1L))
        val vm = viewModel(nowEpochMs = { now })
        collectState(vm)
        vm.onForeground()
        runCurrent()

        assertEquals("Ended", vm.uiState.value.tiles.single().statusLabel)
        assertTrue("an expired contract should not be reported as a live price issue", vm.uiState.value.priceIssues.none { it.key == key })
        vm.onBackground()
    }

    @Test
    fun `ordinary long market names also get a two-line card label`() = runTest(dispatcher) {
        val key = MarketKey("binance:GOOGL/USDT")
        val id = watchlists.ensureDefault()
        watchlists.addItems(id, listOf(key))
        markets.put(
            Market(
                key = key,
                nativeSymbol = "GOOGL",
                pricePrecision = 3,
                displayName = "Alphabet Inc. Class A",
                unit = "USD per share",
            ),
        )
        live.states.value = mapOf(key to marketState(key, 100.5, PriceFreshness.CURRENT))
        val vm = viewModel()
        collectState(vm)
        runCurrent()

        val tile = vm.uiState.value.tiles.single()
        assertEquals("Alphabet Inc. Class A", tile.pair)
        assertTrue(tile.twoLineLabel)
        assertEquals("USD", tile.marketContext)
        assertEquals("100.5", tile.priceText)
    }

    @Test
    fun `visible market metadata refreshes per source while foreground and stops in background`() = runTest(dispatcher) {
        val id = watchlists.ensureDefault()
        watchlists.addItems(id, listOf(btc, eth))
        markets.put(btc)
        markets.put(eth)
        val refreshSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val vm = viewModel(refreshSignals)
        collectState(vm)
        vm.onForeground()
        runCurrent()

        assertEquals(listOf(ExchangeId.BINANCE to false), markets.refreshRequests)
        assertTrue("fresh metadata is read after the source refresh", markets.getMarketsRequests.size >= 2)

        refreshSignals.emit(Unit)
        runCurrent()
        assertEquals("a timer signal refreshes the visible source again", 2, markets.refreshRequests.size)

        vm.onBackground()
        runCurrent()
        refreshSignals.emit(Unit)
        runCurrent()
        assertEquals("background cancels periodic catalogue work", 2, markets.refreshRequests.size)
    }

    @Test
    fun `switching watchlists with identical keys also starts a bounded grace period`() = runTest(dispatcher) {
        val first = watchlists.ensureDefault()
        val second = watchlists.createWatchlist("Same pairs")
        watchlists.addItems(first, listOf(btc))
        watchlists.addItems(second, listOf(btc))
        live.states.value = mapOf(btc to marketState(btc, 100.0, PriceFreshness.OLD))
        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(PriceRefreshStatus.STALE, vm.uiState.value.priceRefreshStatus)
        vm.selectWatchlist(second)
        runCurrent()
        assertEquals(PriceRefreshStatus.REFRESHING, vm.uiState.value.priceRefreshStatus)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(PriceRefreshStatus.STALE, vm.uiState.value.priceRefreshStatus)
    }

    @Test
    fun `slow retry stops showing a spinner at the deadline but remains single flight`() = runTest(dispatcher) {
        val id = watchlists.ensureDefault()
        watchlists.addItems(id, listOf(btc))
        val pending = CompletableDeferred<RefreshResult>()
        live.refreshResultHandler = { pending.await() }
        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()
        vm.refresh()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(PriceRefreshStatus.STALE, vm.uiState.value.priceRefreshStatus)
        assertTrue(vm.uiState.value.refreshInProgress)
        vm.refresh()
        runCurrent()
        assertEquals(1, live.refreshRequests.size)
        pending.complete(RefreshResult(missing = setOf(btc)))
        runCurrent()
        assertEquals(PriceRefreshStatus.ERROR, vm.uiState.value.priceRefreshStatus)
        assertFalse(vm.uiState.value.refreshInProgress)
    }

    @Test
    fun `cancelled retry cannot clear or fail a replacement retry`() = runTest(dispatcher) {
        val first = watchlists.ensureDefault()
        val second = watchlists.createWatchlist("Same pair")
        watchlists.addItems(first, listOf(btc))
        watchlists.addItems(second, listOf(btc))
        live.states.value = mapOf(btc to marketState(btc, 100.0, PriceFreshness.CURRENT))

        val cancelledResult = CompletableDeferred<RefreshResult>()
        val replacementResult = CompletableDeferred<RefreshResult>()
        live.refreshResultHandler = {
            when (live.refreshRequests.size) {
                1 -> withContext(NonCancellable) { cancelledResult.await() }
                else -> replacementResult.await()
            }
        }

        val vm = viewModel()
        collectState(vm)
        vm.onForeground()
        runCurrent()

        vm.refresh()
        runCurrent()
        assertTrue(vm.uiState.value.refreshInProgress)

        vm.selectWatchlist(second)
        runCurrent()
        assertEquals(second, vm.uiState.value.selectedId)

        // Hold the cancelled request past the watchlist switch so its finalizer competes with a
        // replacement request for the same market key.
        vm.refresh()
        runCurrent()
        assertEquals(2, live.refreshRequests.size)
        assertTrue(vm.uiState.value.refreshInProgress)

        cancelledResult.complete(RefreshResult(failures = mapOf(btc to IllegalStateException("late failure"))))
        runCurrent()

        assertTrue("the old finally must not clear the replacement request", vm.uiState.value.refreshInProgress)
        assertEquals("the cancelled result must not be recorded against the replacement", PriceRefreshStatus.REFRESHING, vm.uiState.value.priceRefreshStatus)
        assertTrue(vm.uiState.value.priceIssues.isEmpty())

        replacementResult.complete(RefreshResult(successful = setOf(btc)))
        runCurrent()
        assertFalse(vm.uiState.value.refreshInProgress)
        assertEquals(PriceRefreshStatus.CURRENT, vm.uiState.value.priceRefreshStatus)
    }

    private fun marketState(
        key: MarketKey,
        price: Double,
        freshness: PriceFreshness,
        failure: ExchangeFailureKind? = null,
        tradeTimestamp: Long = 0L,
    ) = MarketState(
        ticker = Ticker(key = key, last = price, changePct24h = 1.0, timestamp = tradeTimestamp),
        confirmedAtElapsedMs = 0L.takeIf { freshness == PriceFreshness.CURRENT || freshness == PriceFreshness.OLD },
        confirmedAtEpochMs = 1_000L.takeIf { freshness == PriceFreshness.CURRENT || freshness == PriceFreshness.OLD },
        freshness = freshness,
        failure = failure,
    )

    private fun viewModel(
        catalogRefreshSignals: Flow<Unit> = emptyFlow(),
        nowEpochMs: () -> Long = System::currentTimeMillis,
    ) = WatchlistViewModel(
        watchlistRepository = watchlists,
        marketRepository = markets,
        sparklineRepository = sparklines,
        marketDataRepository = live,
        settingsStore = settings,
        marketCatalogRefreshSignals = catalogRefreshSignals,
        nowEpochMs = nowEpochMs,
    )

    private fun TestScope.collectState(vm: WatchlistViewModel) {
        backgroundScope.launch { vm.uiState.collect { } }
    }

    /** Keeps tile repositories fake while providing deterministic confirmed-price transitions. */
    private class StatusMarketDataRepository : MarketDataRepository by FakeMarketDataRepository() {
        val states = MutableStateFlow<Map<MarketKey, MarketState>>(emptyMap())
        val refreshRequests = mutableListOf<List<MarketKey>>()
        var refreshResultHandler: suspend (List<MarketKey>) -> RefreshResult = { keys ->
            RefreshResult(successful = keys.toSet())
        }

        override fun observeMarketState(keys: Set<MarketKey>): Flow<Map<MarketKey, MarketState>> =
            states.map { current -> keys.associateWith { key -> current[key] ?: MarketState() } }

        override fun currentState(key: MarketKey): MarketState? = states.value[key]

        override suspend fun refresh(keys: Collection<MarketKey>) = Unit

        override suspend fun refreshResult(keys: Collection<MarketKey>): RefreshResult {
            val requested = keys.toList()
            refreshRequests += requested
            return refreshResultHandler(requested)
        }
    }
}
