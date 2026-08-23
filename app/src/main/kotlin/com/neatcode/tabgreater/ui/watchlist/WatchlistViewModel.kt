package com.neatcode.tabgreater.ui.watchlist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neatcode.tabgreater.core.data.flow.observeEach
import com.neatcode.tabgreater.core.data.flow.throttleLatest
import com.neatcode.tabgreater.core.data.repo.MarketRepository
import com.neatcode.tabgreater.core.data.repo.Sparkline
import com.neatcode.tabgreater.core.data.repo.SparklineRepository
import com.neatcode.tabgreater.core.data.repo.WatchlistRepository
import com.neatcode.tabgreater.core.data.settings.AppSettings
import com.neatcode.tabgreater.core.data.settings.WatchlistRefreshRates
import com.neatcode.tabgreater.core.live.LiveStatus
import com.neatcode.tabgreater.core.live.MarketDataRepository
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.PriceFormat
import com.neatcode.tabgreater.core.model.SortMode
import com.neatcode.tabgreater.core.model.SparkPeriod
import com.neatcode.tabgreater.core.model.Ticker
import com.neatcode.tabgreater.core.model.TileSize
import com.neatcode.tabgreater.core.model.Watchlist
import com.neatcode.tabgreater.core.model.WatchlistItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the Watchlists screen.
 *
 * The tile pipeline is rebuilt only when it has to be: the item list restarts when the selected
 * watchlist changes, the ticker subscription restarts when the **set of market keys** changes and
 * sparkline subscriptions are started or cancelled per key (never wholesale) as tickers come and
 * go — changing only the sort order or the tile size re-sorts the existing tiles without touching
 * any socket.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchlistViewModel(
    private val watchlistRepository: WatchlistRepository,
    private val marketRepository: MarketRepository,
    private val sparklineRepository: SparklineRepository,
    private val marketDataRepository: MarketDataRepository,
    private val settingsStore: AppSettings,
) : ViewModel() {

    /**
     * How often the grid may redraw, as a hot value the throttle can read without restarting.
     *
     * Eagerly started so `.value` is a real setting rather than the default by the time the first
     * ticker arrives; a change takes effect on the next tick, not by rebuilding the pipeline.
     */
    private val refreshMs: StateFlow<Long> = settingsStore.watchlistRefreshMs
        .catch { e -> Log.w(TAG, "refresh rate unavailable", e); emit(WatchlistRefreshRates.DEFAULT) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, WatchlistRefreshRates.DEFAULT)

    /** The tab the user picked; `null` until restored, and ignored when it no longer exists. */
    private val requestedId = MutableStateFlow<Long?>(null)

    /** Item ids ticked in selection mode. Held here so it survives a configuration change. */
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    /**
     * The order the user is dragging, applied on top of the database until Room reports exactly
     * that order. While it is set the grid is also shown as [SortMode.CUSTOM], so dragging inside
     * a sorted watchlist does not fight the comparator before the drop is persisted.
     */
    private val pendingOrder = MutableStateFlow<List<Long>?>(null)

    private val watchlistsFlow: StateFlow<List<Watchlist>> = watchlistRepository.observeWatchlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val selectedWatchlist: Flow<Watchlist?> =
        combine(watchlistsFlow, requestedId) { lists, id ->
            lists.firstOrNull { it.id == id } ?: lists.firstOrNull()
        }.distinctUntilChanged()

    private val itemsFlow: Flow<List<WatchlistItem>> = selectedWatchlist
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else watchlistRepository.observeItems(id)
        }
        .distinctUntilChanged()

    /** [itemsFlow] with the order the finger is currently dragging applied on top. */
    private val orderedItemsFlow: Flow<List<WatchlistItem>> =
        combine(itemsFlow, pendingOrder) { items, order ->
            when {
                order == null -> items
                // Room caught up with the drop: drop the override and follow the database again.
                items.sortedBy { it.position }.map { it.id } == order -> {
                    pendingOrder.value = null
                    items
                }
                else -> orderItems(items, order)
            }
        }

    private val keysFlow: Flow<Set<MarketKey>> = itemsFlow
        .map { items -> items.mapTo(LinkedHashSet()) { it.key } }
        .distinctUntilChanged()

    // Each data source degrades to "no data" on failure (full disk, corrupt DB, ...) instead of
    // cancelling the shared pipeline and freezing the screen.
    private val marketsFlow: Flow<Map<MarketKey, Market>> = keysFlow
        .map { keys -> if (keys.isEmpty()) emptyMap() else marketRepository.getMarkets(keys) }
        .catch { e -> Log.w(TAG, "market lookup failed", e); emit(emptyMap()) }

    // The sockets keep running at full speed; `throttleLatest` only caps how often the grid is
    // rebuilt and recomposed, which is the "Watchlist refresh rate" setting. It sits inside
    // flatMapLatest so the very first quote is still painted the moment it lands.
    private val tickersFlow: Flow<Map<MarketKey, Ticker>> = keysFlow
        .flatMapLatest { keys ->
            if (keys.isEmpty()) {
                flowOf(emptyMap())
            } else {
                marketDataRepository.observeTickers(keys).throttleLatest { refreshMs.value }
            }
        }
        .onStart { emit(emptyMap()) }
        .catch { e -> Log.w(TAG, "ticker stream failed", e); emit(emptyMap()) }

    private val periodFlow: Flow<SparkPeriod> = selectedWatchlist
        .map { it?.period ?: SparkPeriod.HOURS_24 }
        .distinctUntilChanged()

    /**
     * One candle subscription per market: `observeEach` keeps the flows of unchanged keys alive,
     * so adding or removing a single ticker never re-fetches the other sparklines — only a period
     * change restarts the whole set. A broken market drops out of the map instead of failing it.
     *
     * The throttle cannot sit inside a `flatMapLatest` over the keys the way [tickersFlow]'s does
     * (that is exactly what `observeEach` is here to avoid), so the key set is what opens the gate
     * instead: a map for different markets is not another sample of the same tiles, and making it
     * wait would show a freshly opened watchlist with prices but no mini-charts for a whole window.
     */
    private val sparklinesFlow: Flow<Map<MarketKey, Sparkline>> = periodFlow
        .flatMapLatest { period ->
            keysFlow.observeEach { key ->
                sparklineRepository.observeSparkline(key, period)
                    .catch { e -> Log.w(TAG, "sparkline failed for ${key.value}", e) }
            }.throttleLatest(passThrough = { previous, next -> previous?.keys != next.keys }) {
                refreshMs.value
            }
        }
        .onStart { emit(emptyMap()) }
        .catch { e -> Log.w(TAG, "sparkline stream failed", e); emit(emptyMap()) }

    private val itemCountsFlow: Flow<Map<Long, Int>> =
        flow { emitAll(watchlistRepository.observeItemCounts()) }
            .onStart { emit(emptyMap()) }
            .catch { e -> Log.w(TAG, "item counts unavailable", e); emit(emptyMap()) }

    private val shrinkZerosFlow: Flow<Boolean> = settingsStore.shrinkZeros
        .onStart { emit(true) }
        .catch { e -> Log.w(TAG, "settings unavailable", e); emit(true) }
        .distinctUntilChanged()

    private val extrasFlow: Flow<Extras> =
        combine(selectedIds, itemCountsFlow, shrinkZerosFlow) { selected, counts, shrinkZeros ->
            Extras(selected, counts, shrinkZeros)
        }

    private val tileInputs: Flow<TileInputs> =
        combine(orderedItemsFlow, marketsFlow, tickersFlow, sparklinesFlow) { items, markets, tickers, sparks ->
            TileInputs(items, markets, tickers, sparks)
        }

    val uiState: StateFlow<WatchlistUiState> = combine(
        watchlistsFlow,
        selectedWatchlist,
        tileInputs,
        marketDataRepository.status.onStart { emit(LiveStatus.CONNECTING) },
        extrasFlow,
    ) { lists, watchlist, inputs, status, extras ->
        val period = watchlist?.period ?: SparkPeriod.HOURS_24
        // pendingOrder is read directly instead of being combined in: every change to it also
        // re-emits orderedItemsFlow, so this block always sees the value the tiles were built
        // from — combining it separately would let the sort flip one frame before the order does.
        val sort = if (pendingOrder.value != null) SortMode.CUSTOM else watchlist?.sort ?: SortMode.CUSTOM
        val tiles = buildTiles(inputs, period, sort)
        WatchlistUiState(
            watchlists = lists,
            selectedId = watchlist?.id,
            period = period,
            tileSize = watchlist?.tileSize ?: TileSize.SMALL,
            sort = sort,
            tiles = tiles,
            liveStatus = status,
            shrinkZeros = extras.shrinkZeros,
            selectedIds = pruneSelection(extras.selected, tiles),
            itemCounts = extras.counts,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WatchlistUiState())

    init {
        viewModelScope.launch {
            val fallback = watchlistRepository.ensureDefault()
            requestedId.value = settingsStore.selectedWatchlistId.first() ?: fallback
        }
    }

    /** Switches tab and remembers the choice across app restarts. */
    fun selectWatchlist(id: Long) {
        val previous = uiState.value.selectedId
        requestedId.value = id
        if (previous != id) {
            selectedIds.value = emptySet()
            pendingOrder.value = null
        }
        viewModelScope.launch { settingsStore.setSelectedWatchlistId(id) }
    }

    fun setPeriod(period: SparkPeriod) = withSelected { watchlistRepository.setPeriod(it, period) }

    fun setTileSize(size: TileSize) = withSelected { watchlistRepository.setTileSize(it, size) }

    fun setSort(sort: SortMode) = withSelected { watchlistRepository.setSort(it, sort) }

    /** One REST round for the visible markets (app foreground, pull-to-refresh). */
    fun refresh() {
        viewModelScope.launch {
            val state = uiState.value
            val keys = state.tiles.map { it.key }
            if (keys.isEmpty()) return@launch
            marketDataRepository.refresh(keys)
            sparklineRepository.refresh(keys, state.period)
        }
    }

    // ---- Selection mode --------------------------------------------------------------------

    /** Ticks or unticks one tile; the first tick enters selection mode. */
    fun toggleSelection(itemId: Long) {
        selectedIds.update { if (itemId in it) it - itemId else it + itemId }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    /** Removal happens straight away, without a confirmation dialog. */
    fun deleteSelected() = withSelection { watchlistRepository.removeItems(it) }

    fun moveSelectedToTop() = withSelection { watchlistRepository.moveItemsToTop(it) }

    /** @param argb `null` removes the stripe. */
    fun colourSelected(argb: Long?) = withSelection { watchlistRepository.setAccentColor(it, argb) }

    fun moveSelectedTo(watchlistId: Long) =
        withSelection { watchlistRepository.moveItemsToWatchlist(it, watchlistId) }

    // ---- Drag reorder ----------------------------------------------------------------------

    /**
     * One step of a drag: shows the tile at [fromIndex] in position [toIndex] immediately.
     * Nothing is written to the database until [commitOrder].
     */
    fun moveTile(fromIndex: Int, toIndex: Int) {
        val current = pendingOrder.value ?: uiState.value.tiles.map { it.itemId }
        pendingOrder.value = moveItem(current, fromIndex, toIndex)
    }

    /**
     * Persists the dragged order. A watchlist that was sorted by price, change or name becomes
     * Custom, because the manual order *is* the Custom sort.
     */
    fun commitOrder() {
        val order = pendingOrder.value ?: return
        val id = uiState.value.selectedId ?: return
        val wasSorted = watchlistsFlow.value.firstOrNull { it.id == id }?.sort != SortMode.CUSTOM
        viewModelScope.launch {
            try {
                if (wasSorted) watchlistRepository.setSort(id, SortMode.CUSTOM)
                watchlistRepository.reorderItems(id, order)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "reorder failed", e)
                pendingOrder.value = null
            }
        }
    }

    private fun withSelected(block: suspend (Long) -> Unit) {
        val id = uiState.value.selectedId ?: return
        viewModelScope.launch { block(id) }
    }

    /** Runs a bulk action on the ticked items and leaves selection mode when it is done. */
    private fun withSelection(block: suspend (Set<Long>) -> Unit) {
        val ids = selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                block(ids)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed write must not strand the user in selection mode.
                Log.w(TAG, "selection action failed", e)
            }
            selectedIds.value = emptySet()
        }
    }

    /** Drops ticks whose tile is gone (deleted or moved away) so the action bar cannot lie. */
    private fun pruneSelection(selected: Set<Long>, tiles: List<TileUiState>): Set<Long> {
        if (selected.isEmpty()) return selected
        val alive = tiles.mapTo(HashSet(tiles.size)) { it.itemId }
        if (selected.all { it in alive }) return selected
        return selected.intersect(alive).also { selectedIds.value = it }
    }

    private fun buildTiles(inputs: TileInputs, period: SparkPeriod, sort: SortMode): List<TileUiState> {
        val rows = inputs.items.map { item ->
            val spark = inputs.sparks[item.key]
            val precision = inputs.markets[item.key]?.pricePrecision ?: DEFAULT_PRECISION
            val numbers = tileNumbers(period, inputs.tickers[item.key], spark)
            TileRow(
                item = item,
                price = numbers.price,
                changePct = numbers.changePct,
                tile = TileUiState(
                    itemId = item.id,
                    key = item.key,
                    exchangeLabel = item.key.exchange.displayName.uppercase(),
                    pair = item.key.pair,
                    priceText = numbers.price?.let { PriceFormat.formatPrice(it, precision) },
                    changeText = numbers.changePct?.let { PriceFormat.formatChangePct(it) },
                    absChangeText = absChangeText(numbers.absChange, numbers.changePct, precision),
                    highText = numbers.high?.let { PriceFormat.formatPrice(it, precision) },
                    lowText = numbers.low?.let { PriceFormat.formatPrice(it, precision) },
                    volumeText = numbers.volume?.let { PriceFormat.formatVolume(it) },
                    isUp = (numbers.changePct ?: 0.0) >= 0.0,
                    spark = spark?.points?.takeIf { it.size >= 2 },
                    accent = item.accentColor,
                ),
            )
        }
        return rows.sortedWith(comparator(sort)).map { it.tile }
    }

    private companion object {
        /** Keeps the sockets alive across a configuration change instead of tearing them down. */
        const val STOP_TIMEOUT_MS = 5_000L
        private const val TAG = "WatchlistVM"

        /** Used when the instrument list has not been fetched yet, so tiles still show a price. */
        const val DEFAULT_PRECISION = 2
    }
}

/** The parts of the state that do not come out of the tile pipeline. */
private class Extras(
    val selected: Set<Long>,
    val counts: Map<Long, Int>,
    val shrinkZeros: Boolean,
)

/** The four independent streams a tile is assembled from. */
private data class TileInputs(
    val items: List<WatchlistItem>,
    val markets: Map<MarketKey, Market>,
    val tickers: Map<MarketKey, Ticker>,
    val sparks: Map<MarketKey, Sparkline>,
)

