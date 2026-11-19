package com.neatcode.tabgreater.ui.watchlist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neatcode.tabgreater.core.data.flow.observeEach
import com.neatcode.tabgreater.core.data.repo.MarketRepository
import com.neatcode.tabgreater.core.data.repo.Sparkline
import com.neatcode.tabgreater.core.data.repo.SparklineRepository
import com.neatcode.tabgreater.core.data.repo.WatchlistRepository
import com.neatcode.tabgreater.core.data.settings.AppSettings
import com.neatcode.tabgreater.core.data.settings.WatchlistRefreshRates
import com.neatcode.tabgreater.core.live.MarketState
import com.neatcode.tabgreater.core.live.PriceFreshness
import com.neatcode.tabgreater.core.data.repo.HistoryState
import kotlinx.coroutines.Job
import com.neatcode.tabgreater.core.live.LiveStatus
import com.neatcode.tabgreater.core.live.MarketDataRepository
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.InstrumentKind
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.exchange.ExchangeFailureKind
import com.neatcode.tabgreater.core.exchange.exchangeFailureKind
import com.neatcode.tabgreater.core.model.PriceFormat
import com.neatcode.tabgreater.core.model.SortMode
import com.neatcode.tabgreater.core.model.SparkPeriod
import com.neatcode.tabgreater.core.model.Ticker
import com.neatcode.tabgreater.core.model.TileSize
import com.neatcode.tabgreater.core.model.Watchlist
import com.neatcode.tabgreater.core.model.WatchlistItem
import com.neatcode.tabgreater.ui.cardDisplayLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

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
    private val marketCatalogRefreshSignals: Flow<Unit> = periodicMarketCatalogRefreshSignals(),
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val outcomeRolloverRetryMs: Long = OUTCOME_ROLLOVER_RETRY_MS,
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

    private var foreground = false
    private val foregroundState = MutableStateFlow(false)
    private var recoveryGeneration = 0L
    private var recoveryJob: Job? = null
    private val refreshUi = MutableStateFlow(RefreshUi())
    private var lastObservedPrices: Map<MarketKey, MarketState> = emptyMap()

    /** The existing subscription performs catch-up; this only bounds its initial presentation. */
    fun onForeground() {
        if (foreground) return
        foreground = true
        foregroundState.value = true
        beginRecovery()
    }

    fun onBackground() {
        foreground = false
        foregroundState.value = false
        endRecovery(recoveryGeneration)
    }

    private fun beginRecovery(): Long {
        val generation = ++recoveryGeneration
        recoveryJob?.cancel()
        refreshUi.update { it.copy(recovering = foreground) }
        if (foreground) recoveryJob = viewModelScope.launch {
            delay(RECOVERY_GRACE_MS)
            if (generation == recoveryGeneration) refreshUi.update { it.copy(recovering = false) }
        }
        return generation
    }

    private fun endRecovery(generation: Long) {
        if (generation != recoveryGeneration) return
        recoveryJob?.cancel()
        refreshUi.update { it.copy(recovering = false) }
    }

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

    private val keysFlow: StateFlow<Set<MarketKey>> = itemsFlow
        .map { items -> items.mapTo(LinkedHashSet()) { it.key } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

    // Each data source degrades to "no data" on failure (full disk, corrupt DB, ...) instead of
    // cancelling the shared pipeline and freezing the screen.
    private val marketsFlow: Flow<Map<MarketKey, CardMarket>> = combine(keysFlow, foregroundState) { keys, isForeground ->
        keys to isForeground
    }.flatMapLatest { (keys, isForeground) ->
        if (keys.isEmpty()) {
            flowOf(emptyMap())
        } else {
            flow {
                // Always render saved labels immediately. The network refresh and second read are
                // sequential, so the first paint never waits on a source catalogue request.
                var latest = loadMarketMetadata(keys)
                emit(latest)
                if (isForeground) {
                    refreshMarketCatalog(keys)
                    latest = loadMarketMetadata(keys, latest)
                    emit(latest)
                    marketCatalogRefreshSignals.collect {
                        refreshMarketCatalog(keys)
                        latest = loadMarketMetadata(keys, latest)
                        emit(latest)
                    }
                }
            }
        }
    }.map { markets ->
        // Names and settlement conditions only change when exchange metadata changes.
        markets.mapValues { (_, market) -> CardMarket(market, market.cardDisplayLabel()) }
    }.catch { e -> Log.w(TAG, "market lookup failed", e); emit(emptyMap()) }

    private suspend fun refreshMarketCatalog(keys: Set<MarketKey>) {
        keys.asSequence().map { it.exchange }.distinct().forEach { exchange ->
            try {
                marketRepository.refreshMarkets(exchange)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "market catalogue refresh failed for ${exchange.id}", e)
            }
        }
    }

    private suspend fun loadMarketMetadata(
        keys: Set<MarketKey>,
        fallback: Map<MarketKey, Market> = emptyMap(),
    ): Map<MarketKey, Market> = try {
        marketRepository.getMarkets(keys)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "market lookup failed", e)
        fallback
    }

    // The sockets keep running at full speed; the throttle only caps how often the grid is rebuilt
    // and recomposed, which is the "Watchlist refresh rate" setting. It sits inside flatMapLatest
    // so the very first quote is still painted the moment it lands, and `throttleTiles` does the
    // same for the quote that first replaces the persisted snapshot — the stale prices the grid
    // shows while the sockets come back after an unlock.
    private val tickersFlow: Flow<Map<MarketKey, MarketState>> = keysFlow
        .onEach { if (foreground) beginRecovery() }
        .flatMapLatest { keys ->
            if (keys.isEmpty()) {
                flowOf(emptyMap())
            } else {
                marketDataRepository.observeMarketState(keys)
                    .onEach { prices ->
                        lastObservedPrices = prices
                        // A confirmation can have the same price and must clear a failed retry
                        // without waiting for the tile's normal redraw interval.
                        refreshUi.update { refresh ->
                            if (refresh.failedAtConfirmation.isEmpty()) refresh else refresh.copy(
                                failedAtConfirmation = refresh.failedAtConfirmation.filter { (key, failedAt) ->
                                    val state = prices[key]
                                    state == null || !hasConfirmationAfterFailure(key, state, failedAt)
                                },
                            )
                        }
                    }
                    .throttleTiles(
                        changed = { a, b -> a.ticker?.let { x -> b.ticker?.let { redrawsTile(x, it) } } ?: (a.ticker != b.ticker) },
                        alwaysChanged = { a, b -> a.freshness != b.freshness || a.failure != b.failure },
                    ) { refreshMs.value }
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
     * (that is exactly what `observeEach` is here to avoid), so `throttleTiles` reads the map
     * itself: the key set opens the gate while the map is still being filled, and each market's
     * first candle that replaces the cached one opens it once more.
     */
    private val sparklinesFlow: Flow<Map<MarketKey, Sparkline>> = periodFlow
        .flatMapLatest { period ->
            keysFlow.observeEach { key ->
                sparklineRepository.observeSparkline(key, period)
                    .catch { e -> Log.w(TAG, "sparkline failed for ${key.value}", e) }
            }.throttleTiles(::redrawsTile, alwaysChanged = { a, b -> a.history != b.history }) { refreshMs.value }
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
        extrasFlow,
        refreshUi,
    ) { lists, watchlist, inputs, extras, refresh ->
        val period = watchlist?.period ?: SparkPeriod.HOURS_24
        // pendingOrder is read directly instead of being combined in: every change to it also
        // re-emits orderedItemsFlow, so this block always sees the value the tiles were built
        // from — combining it separately would let the sort flip one frame before the order does.
        val sort = if (pendingOrder.value != null) SortMode.CUSTOM else watchlist?.sort ?: SortMode.CUSTOM
        val now = nowEpochMs()
        val tiles = buildTiles(inputs, period, sort, now)
        val issues = priceIssues(inputs, refresh, now)
        val requestedKeys = inputs.items.mapTo(HashSet()) { it.key }
        val refreshing = refresh.activeKeys.any { it in requestedKeys }
        WatchlistUiState(
            watchlists = lists,
            selectedId = watchlist?.id,
            period = period,
            tileSize = watchlist?.tileSize ?: TileSize.SMALL,
            sort = sort,
            tiles = tiles,
            liveStatus = if (inputs.tickers.values.any { it.freshness == PriceFreshness.CURRENT }) LiveStatus.LIVE else LiveStatus.CONNECTING,
            priceRefreshStatus = when {
                requestedKeys.isEmpty() -> PriceRefreshStatus.EMPTY
                issues.any { it.kind == PriceIssueKind.ERROR } -> PriceRefreshStatus.ERROR
                refreshing && refresh.recovering -> PriceRefreshStatus.REFRESHING
                issues.isEmpty() -> PriceRefreshStatus.CURRENT
                refresh.recovering -> PriceRefreshStatus.REFRESHING
                else -> PriceRefreshStatus.STALE
            },
            priceIssues = issues,
            refreshInProgress = refreshing,
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
        viewModelScope.launch {
            combine(
                watchlistRepository.observeWatchlists(),
                watchlistRepository.observeAllKeys(),
                foregroundState,
            ) { lists, keys, isForeground -> Triple(lists, keys, isForeground) }
                .distinctUntilChanged()
                .collectLatest { (lists, keys, isForeground) ->
                    if (isForeground) monitorDailyOutcomeRollovers(lists, keys)
                }
        }
    }

    /**
     * Watches every saved list while the Watchlists screen is active. An expired daily
     * priceBinary keeps its existing contract identity until a complete refreshed catalogue has
     * confirmed one unique next contract with the same Yes/No side.
     */
    private suspend fun monitorDailyOutcomeRollovers(lists: List<Watchlist>, keys: Set<MarketKey>) {
        if (keys.none { it.exchange == ExchangeId.HYPERLIQUID && it.base.startsWith("OUTCOME_") }) return
        var refreshCache = true
        while (true) {
            try {
                if (refreshCache) {
                    marketRepository.refreshMarkets(ExchangeId.HYPERLIQUID).onFailure {
                        Log.w(TAG, "daily outcome catalogue refresh failed", it)
                    }
                    refreshCache = false
                }

                val itemsByList = lists.associate { list ->
                    list.id to watchlistRepository.observeItems(list.id).first()
                }
                val savedKeys = itemsByList.values.flatten().mapTo(LinkedHashSet()) { it.key }
                val metadata = marketRepository.getMarkets(savedKeys)
                val now = nowEpochMs()
                val grouped = itemsByList.flatMap { (listId, items) ->
                    items.mapNotNull { item ->
                        val market = metadata[item.key] ?: return@mapNotNull null
                        val seriesId = market.rolloverSeriesId ?: return@mapNotNull null
                        val side = market.outcomeSide?.trim()?.lowercase(Locale.ROOT)
                            ?.takeIf { it == "yes" || it == "no" } ?: return@mapNotNull null
                        if (market.instrumentKind != InstrumentKind.OUTCOME || market.expiresAt == null) return@mapNotNull null
                        DailyOutcomeItem(listId, item, market, seriesId, side)
                    }
                }

                val due = grouped.groupBy { it.listId to it.groupKey() }.values.mapNotNull { group ->
                    val alreadyHasCurrent = group.any {
                        it.market.active && it.market.expiresAt?.let { expiry -> expiry > now } == true
                    }
                    if (alreadyHasCurrent) null
                    else group.filter { it.market.expiresAt?.let { expiry -> expiry <= now } == true }
                        .maxByOrNull { it.market.expiresAt ?: Long.MIN_VALUE }
                }

                if (due.isNotEmpty()) {
                    // Repaint at expiry even if the venue is offline and no price tick arrives.
                    val expiredAt = due.maxOf { checkNotNull(it.market.expiresAt) }
                    refreshUi.update { it.copy(lastDailyExpiry = expiredAt) }
                    val catalogRefresh = marketRepository.refreshMarkets(ExchangeId.HYPERLIQUID, force = true)
                    catalogRefresh.onFailure {
                        Log.w(TAG, "daily outcome rollover refresh failed", it)
                    }
                    if (catalogRefresh.isFailure) {
                        delay(outcomeRolloverRetryMs.coerceAtLeast(1L))
                        continue
                    }
                    val refreshed = marketRepository.getMarkets(savedKeys)
                    val refreshedAt = nowEpochMs()
                    var rolled = false
                    var eligibilityWaitMs: Long? = null
                    for (entry in due) {
                        val current = refreshed[entry.item.key] ?: entry.market
                        val next = marketRepository.nextDailyOutcomeMarket(current, refreshedAt) ?: continue
                        val untilNextExpiry = (next.expiresAt ?: continue) - refreshedAt
                        if (untilNextExpiry > DAILY_ROLLOVER_LEAD_WINDOW_MS) {
                            val untilEligible = untilNextExpiry - DAILY_ROLLOVER_LEAD_WINDOW_MS
                            eligibilityWaitMs = minOf(eligibilityWaitMs ?: Long.MAX_VALUE, untilEligible)
                            continue
                        }
                        if (untilNextExpiry <= 0L) continue
                        rolled = watchlistRepository.rolloverItem(entry.item.id, entry.item.key, next.key) || rolled
                    }
                    if (rolled) continue
                    delay((eligibilityWaitMs ?: outcomeRolloverRetryMs).coerceAtLeast(1L))
                    continue
                }

                val nextDeadline = grouped.asSequence()
                    .filter { it.market.active }
                    .mapNotNull { it.market.expiresAt }
                    .filter { it > now }
                    .minOrNull()
                if (nextDeadline == null) return
                delay((nextDeadline - now).coerceAtLeast(1L))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "daily outcome rollover monitor failed", failure)
                delay(outcomeRolloverRetryMs.coerceAtLeast(1L))
            }
        }
    }

    private fun DailyOutcomeItem.groupKey(): String = "$seriesId|$side|${market.quote}"

    /** Switches tab and remembers the choice across app restarts. */
    fun selectWatchlist(id: Long) {
        val previous = uiState.value.selectedId
        if (previous != id) {
            cancelPriceRefresh()
            refreshHistoryJob?.cancel()
            if (foreground) beginRecovery()
        }
        requestedId.value = id
        if (previous != id) {
            selectedIds.value = emptySet()
            pendingOrder.value = null
        }
        viewModelScope.launch { settingsStore.setSelectedWatchlistId(id) }
    }

    fun setPeriod(period: SparkPeriod) {
        cancelPriceRefresh()
        refreshHistoryJob?.cancel()
        withSelected { watchlistRepository.setPeriod(it, period) }
    }

    fun setTileSize(size: TileSize) = withSelected { watchlistRepository.setTileSize(it, size) }

    fun setSort(sort: SortMode) = withSelected { watchlistRepository.setSort(it, sort) }

    /** One REST round for the visible markets (app foreground, pull-to-refresh). */
    private var refreshJob: Job? = null
    private var refreshHistoryJob: Job? = null
    private var refreshRequestGeneration = 0L
    private var activeRefreshRecoveryGeneration: Long? = null

    /** Invalidates a cancelled request before a replacement request can start. */
    private fun cancelPriceRefresh() {
        val job = refreshJob ?: return
        if (!job.isActive) return

        refreshRequestGeneration++
        refreshJob = null
        job.cancel()
        refreshUi.update { it.copy(activeKeys = emptySet()) }
        activeRefreshRecoveryGeneration?.let(::endRecovery)
        activeRefreshRecoveryGeneration = null
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        val requestId = ++refreshRequestGeneration
        refreshJob = viewModelScope.launch {
            val state = uiState.value
            val keys = state.tiles.mapTo(LinkedHashSet()) { it.key }
            if (keys.isEmpty()) return@launch
            refreshHistoryJob?.cancel()
            val generation = beginRecovery()
            activeRefreshRecoveryGeneration = generation
            refreshUi.update { it.copy(activeKeys = keys, failedAtConfirmation = it.failedAtConfirmation - keys) }
            try {
                keys.map { it.exchange }.distinct().forEach { com.neatcode.tabgreater.core.exchange.ExchangeRequests.retry(it) }
                val result = marketDataRepository.refreshResult(keys)
                if (requestId == refreshRequestGeneration) {
                    recordRefreshFailures(result.missing.associateWith { null } +
                        result.failures.mapValues { (_, failure) -> failure.exchangeFailureKind() })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestId == refreshRequestGeneration) {
                    Log.w(TAG, "price refresh failed", e)
                    recordRefreshFailures(keys.associateWith { e.exchangeFailureKind() })
                }
            } finally {
                if (requestId == refreshRequestGeneration) {
                    refreshUi.update { it.copy(activeKeys = emptySet()) }
                    if (activeRefreshRecoveryGeneration == generation) activeRefreshRecoveryGeneration = null
                    endRecovery(generation)
                }
            }
            if (requestId != refreshRequestGeneration) return@launch
            // Candle work must not hold the price action busy after its request has completed.
            refreshHistoryJob = viewModelScope.launch {
                try { sparklineRepository.refresh(keys, state.period) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { Log.w(TAG, "history refresh failed", e) }
            }
        }
    }

    private fun recordRefreshFailures(failures: Map<MarketKey, ExchangeFailureKind?>) {
        val confirmations = failures.mapValues { (key, failure) ->
            val state = marketDataRepository.currentState(key) ?: lastObservedPrices[key]
            FailedRefreshMarker(
                confirmedAtElapsedMs = state?.confirmedAtElapsedMs,
                tradeTimestamp = state?.ticker?.timestamp,
                tradePrice = state?.ticker?.last,
                failure = failure,
            )
        }
        refreshUi.update { it.copy(failedAtConfirmation = it.failedAtConfirmation + confirmations) }
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

    private fun buildTiles(inputs: TileInputs, period: SparkPeriod, sort: SortMode, now: Long): List<TileUiState> {
        val rows = inputs.items.map { item ->
            val marketState = inputs.tickers[item.key]
            val ticker = marketState?.ticker
            // The newest bar is still forming and its close only moves when the exchange pushes a
            // kline — Kraken sends none until the pair's next trade, MEXC polls once a minute — so
            // the line follows the live price instead of sitting frozen beside a moving number.
            val spark = inputs.sparks[item.key]?.withLast(ticker?.last)
            val cardMarket = inputs.markets[item.key]
            val market = cardMarket?.market
            val ended = market?.dailyContractEnded(now) == true
            val precision = market?.pricePrecision ?: DEFAULT_PRECISION
            val pair = cardMarket?.label ?: item.key.pair
            val isEventOutcome = market?.instrumentKind == InstrumentKind.OUTCOME
            val marketContext = PriceFormat.cardUnit(market?.unit, pair)
            val numbers = tileNumbers(period, ticker, spark)
            TileRow(
                item = item,
                price = numbers.price,
                changePct = numbers.changePct,
                tile = TileUiState(
                    itemId = item.id,
                    key = item.key,
                    exchangeLabel = item.key.exchange.displayName.uppercase(),
                    pair = pair,
                    marketContext = marketContext,
                    isEventOutcome = isEventOutcome,
                    twoLineLabel = isEventOutcome || '\n' in pair || pair.length > MAX_SINGLE_LINE_LABEL_CHARS,
                    priceText = numbers.price?.let { PriceFormat.formatPriceCompact(it, precision) },
                    changeText = numbers.changePct?.let { PriceFormat.formatChangePct(it) },
                    absChangeText = absChangeText(numbers.absChange, numbers.changePct, precision),
                    highText = numbers.high?.let { PriceFormat.formatPriceCompact(it, precision) },
                    lowText = numbers.low?.let { PriceFormat.formatPriceCompact(it, precision) },
                    volumeText = numbers.volume?.let { PriceFormat.formatVolume(it) },
                    isUp = (numbers.changePct ?: 0.0) >= 0.0,
                    spark = spark?.points?.takeIf { it.size >= 2 },
                    accent = item.accentColor,
                    statusLabel = when {
                        ended -> "Ended"
                        market?.active == false -> "Inactive"
                        numbers.price == null -> "No data"
                        marketState?.freshness == PriceFreshness.OLD -> "Old"
                        marketState?.freshness != PriceFreshness.CURRENT -> "Cached"
                        spark != null && spark.history != HistoryState.VERIFIED -> "History"
                        else -> null
                    },
                    statusDescription = if (ended) {
                        "This daily contract has ended. Any displayed price belongs to that contract."
                    } else listOfNotNull(
                        if (market?.active == false) "Market is inactive" else null,
                        when (marketState?.freshness) {
                            PriceFreshness.OLD -> if (item.key.exchange == ExchangeId.HYPERLIQUID)
                                "Showing an earlier trade price" else "Showing an earlier price update"
                            PriceFreshness.CURRENT -> null
                            else -> "Showing saved data until a price is confirmed"
                        },
                        if (spark != null && spark.history != HistoryState.VERIFIED) "History is not yet verified" else null,
                    ).joinToString(". "),
                ),
            )
        }
        return rows.sortedWith(comparator(sort)).map { it.tile }
    }

    private companion object {
        /** Keeps the sockets alive across a configuration change instead of tearing them down. */
        const val STOP_TIMEOUT_MS = 5_000L
        const val RECOVERY_GRACE_MS = 5_000L
        private const val TAG = "WatchlistVM"

        private const val MAX_SINGLE_LINE_LABEL_CHARS = 12
    }
}

private const val MARKET_CATALOG_REFRESH_MS = 10L * 60 * 1_000
private const val DEFAULT_PRECISION = 2
private const val OUTCOME_ROLLOVER_RETRY_MS = 5L * 60 * 1_000
private const val DAILY_ROLLOVER_LEAD_WINDOW_MS = 24L * 60 * 60 * 1_000

/** The parts of the state that do not come out of the tile pipeline. */
private class Extras(
    val selected: Set<Long>,
    val counts: Map<Long, Int>,
    val shrinkZeros: Boolean,
)

private data class CardMarket(val market: Market, val label: String)

private data class DailyOutcomeItem(
    val listId: Long,
    val item: WatchlistItem,
    val market: Market,
    val seriesId: String,
    val side: String,
)

/** The four independent streams a tile is assembled from. */
private data class TileInputs(
    val items: List<WatchlistItem>,
    val markets: Map<MarketKey, CardMarket>,
    val tickers: Map<MarketKey, MarketState>,
    val sparks: Map<MarketKey, Sparkline>,
)


private data class RefreshUi(
    val recovering: Boolean = false,
    val lastDailyExpiry: Long? = null,
    val activeKeys: Set<MarketKey> = emptySet(),
    // Keep an explicit failed retry visible until that price is confirmed again or retried.
    val failedAtConfirmation: Map<MarketKey, FailedRefreshMarker> = emptyMap(),
)

private data class FailedRefreshMarker(
    val confirmedAtElapsedMs: Long?,
    val tradeTimestamp: Long?,
    val tradePrice: Double?,
    val failure: ExchangeFailureKind?,
)

private fun periodicMarketCatalogRefreshSignals(): Flow<Unit> = flow {
    while (true) {
        delay(MARKET_CATALOG_REFRESH_MS)
        emit(Unit)
    }
}

/** A successful price response resolves the request error even when it reports an earlier trade. */
private fun hasConfirmationAfterFailure(
    key: MarketKey,
    state: MarketState,
    marker: FailedRefreshMarker,
): Boolean {
    if (state.confirmedAtElapsedMs != null && state.confirmedAtElapsedMs != marker.confirmedAtElapsedMs) return true
    return if (key.exchange == ExchangeId.HYPERLIQUID) {
        val latest = state.ticker ?: return false
        if (latest.timestamp <= 0L) return false
        marker.tradeTimestamp == null || latest.timestamp > marker.tradeTimestamp ||
            (marker.tradePrice != null && latest.last != marker.tradePrice)
    } else {
        false
    }
}

private fun Market.dailyContractEnded(now: Long): Boolean = rolloverSeriesId != null && expiresAt?.let { it <= now } == true

private fun priceIssues(inputs: TileInputs, refresh: RefreshUi, now: Long): List<PriceRefreshIssue> =
    inputs.items.distinctBy { it.key }.mapNotNull { item ->
        // A source-confirmed inactive listing can retain its final quote, but it should not keep
        // the active-price health indicator in a warning state.
        val cardMarket = inputs.markets[item.key]
        if (cardMarket?.market?.let { !it.active || it.dailyContractEnded(now) } == true) return@mapNotNull null
        val state = inputs.tickers[item.key]
        val marker = refresh.failedAtConfirmation[item.key]
        val failedRetry = marker != null &&
            (state == null || !hasConfirmationAfterFailure(item.key, state, marker))
        val kind = when {
            state?.failure != null || failedRetry -> PriceIssueKind.ERROR
            state?.freshness == PriceFreshness.OLD -> PriceIssueKind.OLD
            state?.freshness != PriceFreshness.CURRENT -> PriceIssueKind.UNCONFIRMED
            else -> return@mapNotNull null
        }
        val market = cardMarket?.market
        val trade = state?.ticker?.takeIf { item.key.exchange == ExchangeId.HYPERLIQUID && it.timestamp > 0L }
        PriceRefreshIssue(
            key = item.key,
            kind = kind,
            marketLabel = cardMarket?.label?.replace('\n', ' ') ?: item.key.pair,
            lastTradeAtEpochMs = trade?.timestamp,
            lastTradePrice = trade?.let {
                val price = PriceFormat.formatPriceCompact(it.last, market?.pricePrecision ?: DEFAULT_PRECISION)
                val unit = PriceFormat.compactUnit(market?.unit) ?: item.key.quote
                "$price $unit"
            },
            lastPriceReceivedAtEpochMs = state?.confirmedAtEpochMs?.takeIf { it > 0 },
            failure = state?.failure ?: marker?.takeIf { failedRetry }?.failure,
            hasDisplayedPrice = state?.ticker != null || inputs.sparks[item.key]?.lastClose != null,
        )
    }
