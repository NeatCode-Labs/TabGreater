package com.neatcode.tabgreater.ui.search

import com.neatcode.tabgreater.core.data.popular.CachedPopularPairs
import com.neatcode.tabgreater.core.data.popular.DEFAULT_POPULAR_PAIRS
import com.neatcode.tabgreater.core.data.popular.PopularPairsCache
import com.neatcode.tabgreater.core.data.popular.PopularPairsRepository
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.InstrumentKind
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.ui.testing.FakeMarketRepository
import com.neatcode.tabgreater.ui.testing.FakeWatchlistRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

/** Search, filters, selection and incremental paging across the full market catalogue. */
@OptIn(ExperimentalCoroutinesApi::class)
class TickerSearchViewModelTest {

    private val markets = FakeMarketRepository()
    private val watchlists = FakeWatchlistRepository()
    private val popularPairs = PopularPairsRepository(cache = EmptyCache, source = { null })
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `blank query loads its first page without requiring typing`() = searchTest(::seedCatalogue) { viewModel ->
        val state = viewModel.uiState.value

        assertEquals(null, state.instrumentKind)
        assertEquals(null, state.source)
        assertEquals(6, state.results.size)
        assertFalse(state.pending)
        assertEquals(DEFAULT_POPULAR_PAIRS, state.popularPairs)
        assertEquals("", markets.searchPageRequests.first().query)
        assertEquals(0, markets.searchPageRequests.first().offset)
        assertEquals(PAGE_SIZE + 1, markets.searchPageRequests.first().limit)
    }

    @Test
    fun `unfiltered blank search pages beyond three hundred markets`() = searchTest(::seedManyOutcomeMarkets) { viewModel ->
        assertEquals(PAGE_SIZE, viewModel.uiState.value.results.size)
        assertTrue(viewModel.uiState.value.hasMore)

        while (viewModel.uiState.value.hasMore) {
            viewModel.loadNextPage()
            runCurrent()
        }

        assertEquals(650, viewModel.uiState.value.results.size)
        assertFalse(viewModel.uiState.value.hasMore)
        assertEquals((0..12).map { it * PAGE_SIZE }, markets.searchPageRequests.map { it.offset }.distinct())
        assertEquals(650, viewModel.uiState.value.results.map { it.key }.toSet().size)
    }

    @Test
    fun `catalogue refresh keeps loaded rows and scrollable page count`() = runTest(dispatcher) {
        val refreshGate = CompletableDeferred<Unit>()
        val recheckGate = CompletableDeferred<Unit>()
        listId = watchlists.seed("Main")
        seedManyOutcomeMarkets()
        markets.onRefreshAll = { refreshGate.await() }
        var blankPageRequests = 0
        markets.onSearchPage = { call ->
            if (call.query.isBlank() && call.offset == 0 && ++blankPageRequests == 2) recheckGate.await()
        }
        val viewModel = TickerSearchViewModel(listId, markets, watchlists, popularPairs)
        backgroundScope.launch { viewModel.uiState.collect { } }

        assertTrue(viewModel.uiState.value.loading)
        assertEquals(PAGE_SIZE, viewModel.uiState.value.results.size)
        val selected = viewModel.uiState.value.results.first().key
        viewModel.toggle(selected)

        viewModel.loadNextPage()
        runCurrent()
        assertEquals(PAGE_SIZE * 2, viewModel.uiState.value.results.size)

        val addedDuringRefresh = MarketKey("binance:ZZZ/USDT")
        markets.put(addedDuringRefresh)
        refreshGate.complete(Unit)
        runCurrent()

        assertFalse(viewModel.uiState.value.loading)
        assertTrue(viewModel.uiState.value.pending)
        assertEquals(PAGE_SIZE * 2, viewModel.uiState.value.results.size)
        assertFalse(viewModel.uiState.value.hasMore)
        assertEquals(0, markets.searchPageRequests.last().offset)
        assertEquals(PAGE_SIZE * 2 + 1, markets.searchPageRequests.last().limit)

        recheckGate.complete(Unit)
        runCurrent()

        assertTrue(viewModel.uiState.value.results.any { it.key == addedDuringRefresh })
        assertEquals(setOf(selected), viewModel.uiState.value.selected)
        assertTrue(viewModel.uiState.value.hasMore)
    }

    @Test
    fun `typed query matches event names and outcomes across the whole catalogue`() = searchTest(::seedCatalogue) { viewModel ->
        type(viewModel, "Will Team A win")

        assertEquals(
            listOf("hyperliquid:OUTCOME_37275_NO/USDC", "hyperliquid:OUTCOME_37275_YES/USDC"),
            viewModel.uiState.value.results.map { it.key.value },
        )
        assertTrue(viewModel.uiState.value.results.all { it.instrumentKind == InstrumentKind.OUTCOME })
        assertEquals("Will Team A win", markets.searchPageRequests.last().query)
    }

    @Test
    fun `type and source filters combine while retaining event search access`() = searchTest(::seedCatalogue) { viewModel ->
        viewModel.onInstrumentKindChange(InstrumentKind.PERPETUAL)
        viewModel.onSourceChange(ExchangeId.HYPERLIQUID)
        runCurrent()

        assertEquals(listOf("hyperliquid:TSLA/USD"), viewModel.uiState.value.results.map { it.key.value })
        assertEquals(InstrumentKind.PERPETUAL, markets.searchPageRequests.last().instrumentKind)
        assertEquals(ExchangeId.HYPERLIQUID, markets.searchPageRequests.last().exchange)

        viewModel.onInstrumentKindChange(null)
        viewModel.onSourceChange(null)
        type(viewModel, "Will Team A win")
        assertEquals(2, viewModel.uiState.value.results.size)
    }

    @Test
    fun `stale query rows stay hidden and a new query starts at offset zero`() = searchTest(::seedCatalogue) { viewModel ->
        val slowQuery = CompletableDeferred<Unit>()
        markets.onSearchPage = { call -> if (call.query == "slow") slowQuery.await() }

        viewModel.onQueryChange("slow")
        advanceTimeBy(DEBOUNCE_SETTLE_MS)
        runCurrent()
        assertTrue(viewModel.uiState.value.pending)
        assertTrue(viewModel.uiState.value.results.isEmpty())

        viewModel.onQueryChange("Will Team A win")
        advanceTimeBy(DEBOUNCE_SETTLE_MS)
        runCurrent()

        assertFalse(viewModel.uiState.value.pending)
        assertEquals(2, viewModel.uiState.value.results.size)
        assertEquals("Will Team A win", markets.searchPageRequests.last().query)
        assertEquals(0, markets.searchPageRequests.last().offset)
    }

    @Test
    fun `failed first page remains visible as retryable and retry reloads the query`() = runTest(dispatcher) {
        val refreshGate = CompletableDeferred<Unit>()
        listId = watchlists.seed("Main")
        seedCatalogue()
        markets.onRefreshAll = { refreshGate.await() }
        var failOnce = true
        markets.onSearchPage = { call ->
            if (call.offset == 0 && failOnce) {
                failOnce = false
                throw IllegalStateException("temporary catalogue read failure")
            }
        }
        val viewModel = TickerSearchViewModel(listId, markets, watchlists, popularPairs)
        backgroundScope.launch { viewModel.uiState.collect { } }

        assertTrue(viewModel.uiState.value.pageError)
        assertTrue(viewModel.uiState.value.results.isEmpty())
        assertFalse(viewModel.uiState.value.pending)

        viewModel.retrySearch()
        runCurrent()

        assertFalse(viewModel.uiState.value.pageError)
        assertEquals(6, viewModel.uiState.value.results.size)
        assertEquals(listOf(0, 0), markets.searchPageRequests.map { it.offset })
        refreshGate.complete(Unit)
        runCurrent()
    }

    @Test
    fun `repeated near-end callbacks request one page and changed filters reset paging`() = searchTest(::seedManyOutcomeMarkets) { viewModel ->
        val pageGate = CompletableDeferred<Unit>()
        markets.onSearchPage = { call -> if (call.offset == PAGE_SIZE) pageGate.await() }

        viewModel.loadNextPage()
        runCurrent()
        viewModel.loadNextPage()
        runCurrent()
        assertEquals(1, markets.searchPageRequests.count { it.offset == PAGE_SIZE })

        pageGate.complete(Unit)
        runCurrent()
        assertEquals(PAGE_SIZE * 2, viewModel.uiState.value.results.size)

        viewModel.onInstrumentKindChange(InstrumentKind.OUTCOME)
        runCurrent()

        assertEquals(0, markets.searchPageRequests.last().offset)
        assertEquals(InstrumentKind.OUTCOME, markets.searchPageRequests.last().instrumentKind)
        assertEquals(PAGE_SIZE, viewModel.uiState.value.results.size)
        assertTrue(viewModel.uiState.value.hasMore)
    }

    @Test
    fun `failed next page stays retryable`() = searchTest(::seedManyOutcomeMarkets) { viewModel ->
        var failOnce = true
        markets.onSearchPage = { call ->
            if (call.offset == PAGE_SIZE && failOnce) {
                failOnce = false
                throw IllegalStateException("temporary catalogue read failure")
            }
        }

        viewModel.loadNextPage()
        runCurrent()

        assertTrue(viewModel.uiState.value.pageError)
        assertTrue(viewModel.uiState.value.hasMore)
        assertFalse(viewModel.uiState.value.loadingMore)

        viewModel.retrySearch()
        runCurrent()

        assertFalse(viewModel.uiState.value.pageError)
        assertEquals(PAGE_SIZE * 2, viewModel.uiState.value.results.size)
        assertTrue(viewModel.uiState.value.hasMore)
    }

    @Test
    fun `selection survives filter changes and Add includes different instrument types`() = searchTest(::seedCatalogue) { viewModel ->
        val btc = MarketKey("binance:BTC/USDT")
        val event = MarketKey("hyperliquid:OUTCOME_37275_YES/USDC")
        viewModel.toggle(btc)

        viewModel.onInstrumentKindChange(InstrumentKind.OUTCOME)
        runCurrent()
        assertEquals(setOf(btc), viewModel.uiState.value.selected)
        viewModel.toggle(event)
        viewModel.addSelected()

        assertEquals(setOf(btc, event), watchlists.itemsOf(listId).map { it.key }.toSet())
        assertTrue(viewModel.uiState.value.finished)
    }

    private fun seedCatalogue() {
        markets.put(MarketKey("binance:BTC/USDT"))
        markets.put(MarketKey("binance:ETH/USDT"))
        markets.put(MarketKey("binance:TSLAB/USDT"), underlying = "TSLA")
        markets.put(
            Market(
                key = MarketKey("hyperliquid:TSLA/USD"),
                nativeSymbol = "TSLA",
                pricePrecision = 2,
                underlying = "TSLA",
                instrumentKind = InstrumentKind.PERPETUAL,
                displayName = "TSLA",
                unit = "USD per share",
                venue = "xyz",
            ),
        )
        for (side in listOf("YES", "NO")) {
            markets.put(
                Market(
                    key = MarketKey("hyperliquid:OUTCOME_37275_${side}/USDC"),
                    nativeSymbol = if (side == "YES") "#372750" else "#372751",
                    pricePrecision = 3,
                    instrumentKind = InstrumentKind.OUTCOME,
                    displayName = "Will Team A win? · ${side.lowercase().replaceFirstChar(Char::uppercase)}",
                    description = "Will Team A win?",
                    outcomeSide = side.lowercase().replaceFirstChar(Char::uppercase),
                    outcomeId = "37275",
                ),
            )
        }
    }

    private fun seedManyOutcomeMarkets() {
        repeat(325) { index ->
            for (side in listOf("YES", "NO")) {
                markets.put(
                    Market(
                        key = MarketKey("hyperliquid:OUTCOME_${index}_SIDE_${side}/USDC"),
                        nativeSymbol = "#${index}${if (side == "YES") 0 else 1}",
                        pricePrecision = 3,
                        instrumentKind = InstrumentKind.OUTCOME,
                        displayName = "Event $index · ${side.lowercase().replaceFirstChar(Char::uppercase)}",
                        description = "Event $index",
                        outcomeSide = side.lowercase().replaceFirstChar(Char::uppercase),
                        outcomeId = index.toString(),
                    ),
                )
            }
        }
    }

    private fun TestScope.type(viewModel: TickerSearchViewModel, text: String) {
        viewModel.onQueryChange(text)
        advanceTimeBy(DEBOUNCE_SETTLE_MS)
        runCurrent()
    }

    private var listId = 0L

    private fun searchTest(
        seed: () -> Unit,
        block: suspend TestScope.(TickerSearchViewModel) -> Unit,
    ) = runTest(dispatcher) {
        listId = watchlists.seed("Main")
        seed()
        val viewModel = TickerSearchViewModel(listId, markets, watchlists, popularPairs)
        backgroundScope.launch { viewModel.uiState.collect { } }
        block(viewModel)
    }

    private object EmptyCache : PopularPairsCache {
        override suspend fun read(): CachedPopularPairs? = null
        override suspend fun write(pairs: List<String>, fetchedAtMs: Long) = Unit
    }

    private companion object {
        const val DEBOUNCE_SETTLE_MS = 250L
        const val PAGE_SIZE = 50
    }
}
