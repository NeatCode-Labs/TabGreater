package com.neatcode.tabgreater.ui.chart

import com.neatcode.tabgreater.core.model.Limits
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.PriceFormat
import com.neatcode.tabgreater.core.model.SparkPeriod
import com.neatcode.tabgreater.core.model.Ticker
import com.neatcode.tabgreater.core.model.Timeframe
import com.neatcode.tabgreater.feature.chart.CandleType
import com.neatcode.tabgreater.feature.chart.IndicatorCatalogue
import com.neatcode.tabgreater.ui.testing.FakeAppSettings
import com.neatcode.tabgreater.ui.testing.FakeChartPreferences
import com.neatcode.tabgreater.ui.testing.FakeMarketDataRepository
import com.neatcode.tabgreater.ui.testing.FakeMarketRepository
import com.neatcode.tabgreater.ui.testing.FakeSparklineRepository
import com.neatcode.tabgreater.ui.testing.FakeWatchlistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** What the chart header shows and what the toolbar's actions persist. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChartViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val markets = FakeMarketRepository()
    private val watchlists = FakeWatchlistRepository()
    private val live = FakeMarketDataRepository()
    private val sparklines = FakeSparklineRepository()
    private val chartSettings = FakeChartPreferences()
    private var appSettings = FakeAppSettings()

    private val key = MarketKey("kraken:BTC/EUR")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        markets.put(key, pricePrecision = 1)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ChartViewModel(key, markets, watchlists, live, sparklines, chartSettings, appSettings)

    /** Collects `state` so the `WhileSubscribed` pipeline actually runs. */
    private fun TestScope.collecting(viewModel: ChartViewModel) {
        backgroundScope.launch(dispatcher) { viewModel.state.collect {} }
    }

    /**
     * The next one-shot event, or `null` when none arrives. The channel is buffered, so an event
     * emitted before this call is still there; the timeout runs on virtual time.
     */
    private suspend fun ChartViewModel.nextEvent(): ChartEvent? =
        withTimeoutOrNull(EVENT_TIMEOUT_MS) { events.first() }

    @Test
    fun `header formats the ticker with the market's price precision`() = runTest(dispatcher) {
        live.tickers.value = mapOf(
            key to Ticker(
                key = key,
                last = 65609.75,
                high24h = 67403.1,
                low24h = 65085.895,
                volumeBase24h = 1_234_567.0,
                changePct24h = -2.873,
                bid = 65609.7,
                ask = 65609.8,
                timestamp = 1L,
            ),
        )
        val viewModel = viewModel()
        collecting(viewModel)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("65,609.8", state.priceText)
        assertEquals("-2.87%", state.changeText)
        assertFalse(state.isUp)
        assertTrue(state.hasTrend)
        assertEquals("67,403.1", state.highText)
        assertEquals("65,085.9", state.lowText)
        assertEquals("65,609.7", state.bidText)
        assertEquals("65,609.8", state.askText)
        assertEquals("1.2M", state.volumeText)
        assertEquals("KRAKEN", state.exchangeLabel)
        assertEquals("BTC/EUR", state.pair)
    }

    @Test
    fun `a ticker without a 24h figure takes the change from the sparkline window`() = runTest(dispatcher) {
        // Kraken over REST: today's open is not a 24 h open, so the adapter sends neither field.
        live.tickers.value = mapOf(key to Ticker(key = key, last = 110.0, timestamp = 1L))
        sparklines.setPoints(key, 100f, 104f, 110f)
        val viewModel = viewModel()
        collecting(viewModel)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("+10.00%", state.changeText)
        assertTrue(state.isUp)
        assertTrue(state.hasTrend)
        assertEquals(listOf(key to SparkPeriod.HOURS_24), sparklines.starts)

        // The socket's rolling figure wins as soon as it arrives.
        live.tickers.value = mapOf(key to Ticker(key = key, last = 110.0, changePct24h = 8.25, timestamp = 2L))
        advanceUntilIdle()
        assertEquals("+8.25%", viewModel.state.value.changeText)
    }

    @Test
    fun `the window alone does not invent a change for a missing ticker`() = runTest(dispatcher) {
        sparklines.setPoints(key, 100f, 110f)
        val viewModel = viewModel()
        collecting(viewModel)
        advanceUntilIdle()

        assertEquals(PriceFormat.NO_VALUE, viewModel.state.value.changeText)
        assertFalse(viewModel.state.value.hasTrend)
    }

    @Test
    fun `without a ticker the header shows placeholders and no caret`() = runTest(dispatcher) {
        val viewModel = viewModel()
        collecting(viewModel)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(PriceFormat.NO_VALUE, state.priceText)
        assertEquals(PriceFormat.NO_VALUE, state.askText)
        assertFalse(state.hasTrend)
        assertFalse(state.unavailable)
        assertEquals(key, state.market?.key)
    }

    @Test
    fun `a pair the exchange does not list is reported as unavailable`() = runTest(dispatcher) {
        val unknown = MarketKey("kraken:FOO/EUR")
        val viewModel = ChartViewModel(unknown, markets, watchlists, live, sparklines, chartSettings, appSettings)
        backgroundScope.launch(dispatcher) { viewModel.state.collect {} }
        advanceUntilIdle()

        assertTrue(viewModel.state.value.unavailable)
        assertEquals(null, viewModel.state.value.market)
    }

    @Test
    fun `the star adds to and removes from the selected watchlist`() = runTest(dispatcher) {
        watchlists.seed("Main")
        val second = watchlists.seed("Alt")
        appSettings = FakeAppSettings(selectedWatchlistId = second)
        val viewModel = viewModel()
        collecting(viewModel)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.starred)
        assertTrue(viewModel.state.value.canStar)

        viewModel.toggleStar()
        advanceUntilIdle()
        assertEquals(listOf(key), watchlists.items(second).map { it.key })
        assertTrue(viewModel.state.value.starred)

        viewModel.toggleStar()
        advanceUntilIdle()
        assertEquals(emptyList<MarketKey>(), watchlists.items(second).map { it.key })
        assertFalse(viewModel.state.value.starred)
    }

    @Test
    fun `with no watchlist at all the star is disabled instead of crashing`() = runTest(dispatcher) {
        val viewModel = viewModel()
        collecting(viewModel)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.canStar)

        viewModel.toggleStar()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.starred)
    }

    @Test
    fun `with no watchlist the star reports it instead of doing nothing silently`() = runTest(dispatcher) {
        val viewModel = viewModel()
        collecting(viewModel)
        advanceUntilIdle()

        viewModel.toggleStar()
        advanceUntilIdle()
        assertEquals(ChartEvent.NoWatchlist, viewModel.nextEvent())
    }

    @Test
    fun `a full watchlist disables the star and reports the cap`() = runTest(dispatcher) {
        val full = watchlists.seed("Main", keys = (1..Limits.MAX_ITEMS_PER_WATCHLIST).map { "kraken:C$it/EUR" })
        appSettings = FakeAppSettings(selectedWatchlistId = full)
        val viewModel = viewModel()
        collecting(viewModel)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.canStar)

        viewModel.toggleStar()
        advanceUntilIdle()
        assertEquals(ChartEvent.WatchlistFull(Limits.MAX_ITEMS_PER_WATCHLIST), viewModel.nextEvent())
        assertEquals(Limits.MAX_ITEMS_PER_WATCHLIST, watchlists.items(full).size)
        assertFalse(viewModel.state.value.starred)
    }

    @Test
    fun `un-starring stays possible on a full watchlist`() = runTest(dispatcher) {
        val keys = (1 until Limits.MAX_ITEMS_PER_WATCHLIST).map { "kraken:C$it/EUR" } + key.value
        val full = watchlists.seed("Main", keys = keys)
        appSettings = FakeAppSettings(selectedWatchlistId = full)
        val viewModel = viewModel()
        collecting(viewModel)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.canStar)
        assertTrue(viewModel.state.value.starred)

        viewModel.toggleStar()
        advanceUntilIdle()
        assertNull(viewModel.nextEvent())
        assertEquals(Limits.MAX_ITEMS_PER_WATCHLIST - 1, watchlists.items(full).size)
        assertFalse(viewModel.state.value.starred)
    }

    @Test
    fun `the shrink-zeros setting reaches the chart header`() = runTest(dispatcher) {
        appSettings = FakeAppSettings(shrinkZeros = false)
        val viewModel = viewModel()
        collecting(viewModel)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.shrinkZeros)

        appSettings.setShrinkZeros(true)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.shrinkZeros)
    }

    @Test
    fun `base volume is printed in full below a million and compacted above it`() = runTest(dispatcher) {
        val viewModel = viewModel()
        collecting(viewModel)

        live.tickers.value = mapOf(key to Ticker(key = key, last = 1.0, volumeBase24h = 32_085.6445, timestamp = 1L))
        advanceUntilIdle()
        assertEquals("32,085.64", viewModel.state.value.volumeText)

        live.tickers.value = mapOf(key to Ticker(key = key, last = 1.0, volumeBase24h = 225.4, timestamp = 2L))
        advanceUntilIdle()
        assertEquals("225.40", viewModel.state.value.volumeText)

        live.tickers.value = mapOf(key to Ticker(key = key, last = 1.0, volumeBase24h = 9_810_000.0, timestamp = 3L))
        advanceUntilIdle()
        assertEquals("9.8M", viewModel.state.value.volumeText)
    }

    @Test
    fun `toolbar changes persist immediately`() = runTest(dispatcher) {
        val viewModel = viewModel()
        collecting(viewModel)
        advanceUntilIdle()

        viewModel.setTimeframe(Timeframe.M15)
        viewModel.setCandleType(CandleType.AREA)
        viewModel.setLogScale(true)
        advanceUntilIdle()

        assertEquals(Timeframe.M15, chartSettings.value.timeframe)
        assertEquals(CandleType.AREA, chartSettings.value.candleType)
        assertTrue(chartSettings.value.logScale)
        assertEquals(Timeframe.M15, viewModel.state.value.settings.timeframe)
    }

    @Test
    fun `toggling an indicator adds it once and then removes it`() = runTest(dispatcher) {
        val viewModel = viewModel()
        collecting(viewModel)
        advanceUntilIdle()
        assertEquals(listOf("VOL"), chartSettings.value.indicators.map { it.name })

        viewModel.toggleIndicator("MACD")
        advanceUntilIdle()
        assertEquals(listOf("VOL", "MACD"), chartSettings.value.indicators.map { it.name })
        assertEquals(IndicatorCatalogue.find("MACD"), chartSettings.value.indicators.last())

        viewModel.toggleIndicator("MACD")
        advanceUntilIdle()
        assertEquals(listOf("VOL"), chartSettings.value.indicators.map { it.name })
    }

    @Test
    fun `an indicator outside the catalogue is ignored`() = runTest(dispatcher) {
        val viewModel = viewModel()
        collecting(viewModel)
        advanceUntilIdle()

        viewModel.toggleIndicator("ATR")
        advanceUntilIdle()
        assertEquals(listOf("VOL"), chartSettings.value.indicators.map { it.name })
    }

    private companion object {
        /** Virtual-time budget for a one-shot event; nothing here ever waits in real time. */
        const val EVENT_TIMEOUT_MS = 1_000L
    }
}
