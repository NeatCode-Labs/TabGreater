package com.neatcode.tabgreater.ui.chart

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neatcode.tabgreater.core.data.repo.MarketRepository
import com.neatcode.tabgreater.core.data.repo.Sparkline
import com.neatcode.tabgreater.core.data.repo.SparklineRepository
import com.neatcode.tabgreater.core.data.repo.WatchlistRepository
import com.neatcode.tabgreater.core.data.flow.throttleLatest
import com.neatcode.tabgreater.core.data.settings.AppSettings
import com.neatcode.tabgreater.core.data.settings.WatchlistRefreshRates
import com.neatcode.tabgreater.core.live.MarketDataRepository
import com.neatcode.tabgreater.core.model.Limits
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.PriceFormat
import com.neatcode.tabgreater.core.model.SparkPeriod
import com.neatcode.tabgreater.core.model.Ticker
import com.neatcode.tabgreater.core.model.Timeframe
import com.neatcode.tabgreater.core.model.WatchlistItem
import com.neatcode.tabgreater.feature.chart.CandleType
import com.neatcode.tabgreater.feature.chart.ChartSettings
import com.neatcode.tabgreater.feature.chart.ChartPreferences
import com.neatcode.tabgreater.feature.chart.IndicatorCatalogue
import com.neatcode.tabgreater.ui.watchlist.changePct
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the chart screen for exactly one market.
 *
 * The header is fed by [MarketDataRepository.observeTickers] — which also keeps the exchange
 * socket alive while the chart is open, so the price pill and the header move together — and the
 * chart's own preferences (timeframe, candle type, log scale, indicators) come from
 * [ChartPreferences], which is global rather than per market.
 *
 * The 24 h change follows the same three-step rule as a tile ([changePct]): the exchange's own
 * percentage, else one derived from its 24 h open, else the 24 h sparkline window — the only
 * source a Kraken header has until the v2 socket delivers its first rolling `change_pct`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChartViewModel(
    private val key: MarketKey,
    private val marketRepository: MarketRepository,
    private val watchlistRepository: WatchlistRepository,
    private val marketDataRepository: MarketDataRepository,
    private val sparklineRepository: SparklineRepository,
    private val chartSettings: ChartPreferences,
    private val appSettings: AppSettings,
) : ViewModel() {

    /**
     * The instrument, looked up once. A market the app has never seen (a widget deep link into a
     * pair that was never searched) triggers one instrument-list refresh before giving up;
     * [MarketLookup.resolved] is what tells "still loading" from "this exchange has no such pair".
     */
    private val marketFlow: Flow<MarketLookup> = flow {
        emit(MarketLookup.PENDING)
        val cached = marketRepository.getMarket(key)
        if (cached != null) {
            emit(MarketLookup(cached, resolved = true))
        } else {
            marketRepository.refreshMarkets(key.exchange)
            emit(MarketLookup(marketRepository.getMarket(key), resolved = true))
        }
    }.catch { e ->
        Log.w(TAG, "market lookup failed for $key", e)
        emit(MarketLookup(null, resolved = true))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MarketLookup.PENDING)

    /** The watchlist's redraw cadence, which the header dashboard shares (Settings → DISPLAY). */
    private val refreshMs: StateFlow<Long> = appSettings.watchlistRefreshMs
        .catch { e -> Log.w(TAG, "refresh rate unavailable", e); emit(WatchlistRefreshRates.DEFAULT) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, WatchlistRefreshRates.DEFAULT)

    // Throttled like the watchlist tiles: the header numbers are read, not watched. The chart's
    // own live candles come from the WebView bridge and are deliberately left untouched.
    private val tickerFlow: Flow<Ticker?> = marketDataRepository.observeTickers(setOf(key))
        .throttleLatest { refreshMs.value }
        .map { it[key] }
        .onStart { emit(null) }
        .catch { e ->
            Log.w(TAG, "ticker stream failed for $key", e)
            emit(null)
        }

    /**
     * The 24 h candle window behind the header's change when the ticker has none. Shares the
     * watchlist's cache (fresh for any market on a list; one REST seed otherwise) and the same
     * throttle as [tickerFlow], so the two never redraw the header at different cadences.
     */
    private val sparkFlow: Flow<Sparkline> = sparklineRepository.observeSparkline(key, SparkPeriod.HOURS_24)
        .throttleLatest { refreshMs.value }
        .onStart { emit(Sparkline.EMPTY) }
        .catch { e ->
            Log.w(TAG, "sparkline stream failed for $key", e)
            emit(Sparkline.EMPTY)
        }

    /** Market, ticker and window together — `combine` takes at most five flows, so the header gets its own. */
    private val headerFlow: Flow<Triple<MarketLookup, Ticker?, Sparkline>> =
        combine(marketFlow, tickerFlow, sparkFlow) { lookup, ticker, spark -> Triple(lookup, ticker, spark) }

    /** Items of the watchlist the tab row currently shows — the list the ★ adds to / removes from. */
    private val currentListFlow: Flow<Pair<Long, List<WatchlistItem>>?> =
        combine(watchlistRepository.observeWatchlists(), appSettings.selectedWatchlistId) { lists, selected ->
            (lists.firstOrNull { it.id == selected } ?: lists.firstOrNull())?.id
        }
            .distinctUntilChanged()
            .flatMapLatest { id ->
                if (id == null) flowOf(null) else watchlistRepository.observeItems(id).map { id to it }
            }
            .catch { e ->
                Log.w(TAG, "watchlist membership unavailable", e)
                emit(null)
            }

    private val eventChannel = Channel<ChartEvent>(Channel.BUFFERED)

    /** One-shot messages for the screen's snackbar; nothing here changes [state]. */
    val events: Flow<ChartEvent> = eventChannel.receiveAsFlow()

    val state: StateFlow<ChartUiState> = combine(
        headerFlow,
        currentListFlow,
        chartSettings.settings,
        appSettings.shrinkZeros,
    ) { (lookup, ticker, spark), list, settings, shrinkZeros ->
        buildState(lookup, ticker, spark, list, settings, shrinkZeros)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        ChartUiState(key = key),
    )

    // ------------------------------------------------------------------ actions

    fun setTimeframe(timeframe: Timeframe) {
        viewModelScope.launch { chartSettings.setTimeframe(timeframe) }
    }

    fun setCandleType(type: CandleType) {
        viewModelScope.launch { chartSettings.setCandleType(type) }
    }

    fun setLogScale(enabled: Boolean) {
        viewModelScope.launch { chartSettings.setLogScale(enabled) }
    }

    /** Adds the indicator when it is off, removes it when it is on; the pane comes from the catalogue. */
    fun toggleIndicator(name: String) {
        val spec = IndicatorCatalogue.find(name) ?: return
        viewModelScope.launch {
            val current = chartSettings.settings.first().indicators
            val next = if (current.any { it.name == name }) current.filterNot { it.name == name } else current + spec
            chartSettings.setIndicators(next)
        }
    }

    /**
     * ★: adds the market to the selected watchlist, or removes it from there.
     *
     * Both ways of doing nothing are reported through [events] instead of leaving the tap silent:
     * there is no watchlist at all, or the selected one already holds
     * [Limits.MAX_ITEMS_PER_WATCHLIST] markets (`addItems` drops the key without a word).
     */
    fun toggleStar() {
        viewModelScope.launch {
            val current = currentListFlow.first()
            if (current == null) {
                eventChannel.send(ChartEvent.NoWatchlist)
                return@launch
            }
            val (listId, items) = current
            val existing = items.firstOrNull { it.key == key }
            when {
                existing != null -> watchlistRepository.removeItems(listOf(existing.id))
                items.size >= Limits.MAX_ITEMS_PER_WATCHLIST ->
                    eventChannel.send(ChartEvent.WatchlistFull(Limits.MAX_ITEMS_PER_WATCHLIST))

                else -> watchlistRepository.addItems(listId, listOf(key))
            }
        }
    }

    // ------------------------------------------------------------------ formatting

    private fun buildState(
        lookup: MarketLookup,
        ticker: Ticker?,
        spark: Sparkline,
        list: Pair<Long, List<WatchlistItem>>?,
        settings: ChartSettings,
        shrinkZeros: Boolean,
    ): ChartUiState {
        val market = lookup.market
        val precision = market?.pricePrecision ?: DEFAULT_PRECISION
        fun price(value: Double?): String =
            if (value == null) PriceFormat.NO_VALUE else PriceFormat.formatPrice(value, precision)

        // No ticker, no change: the window alone would describe a price the header is not showing.
        val change = ticker?.let { changePct(SparkPeriod.HOURS_24, it, spark) }
        val items = list?.second
        return ChartUiState(
            key = key,
            market = market,
            unavailable = lookup.resolved && market == null,
            priceText = price(ticker?.last),
            changeText = PriceFormat.formatChangePct(change),
            isUp = (change ?: 0.0) >= 0.0,
            hasTrend = change != null,
            askText = price(ticker?.ask),
            bidText = price(ticker?.bid),
            highText = price(ticker?.high24h),
            lowText = price(ticker?.low24h),
            volumeText = ticker?.volumeBase24h?.let(::formatBaseVolume) ?: PriceFormat.NO_VALUE,
            starred = items?.any { it.key == key } == true,
            // The ★ can only change something when there is a list and either the market is
            // already in it (the tap un-stars) or the list still has room.
            canStar = items != null &&
                (items.any { it.key == key } || items.size < Limits.MAX_ITEMS_PER_WATCHLIST),
            shrinkZeros = shrinkZeros,
            settings = settings,
        )
    }

    /**
     * The 24 h base volume is printed in full (`32,085.64`); only genuinely large numbers
     * are compacted, so a 225.40 BTC volume no longer reads the same as 225.40 M.
     */
    private fun formatBaseVolume(volume: Double): String =
        if (volume.isFinite() && kotlin.math.abs(volume) < COMPACT_VOLUME_FROM) {
            PriceFormat.formatPrice(volume, 2)
        } else {
            PriceFormat.formatVolume(volume)
        }

    /** Result of the one-shot instrument lookup. [resolved] is `false` only while it is running. */
    private data class MarketLookup(val market: Market?, val resolved: Boolean) {
        companion object {
            val PENDING = MarketLookup(null, resolved = false)
        }
    }

    private companion object {
        const val TAG = "ChartViewModel"
        const val STOP_TIMEOUT_MS = 5_000L
        const val DEFAULT_PRECISION = 2

        /** Below this the 24 h volume is printed in full, above it in compact `1.2M` notation. */
        const val COMPACT_VOLUME_FROM = 1_000_000.0
    }
}
