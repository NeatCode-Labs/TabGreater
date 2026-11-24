package com.neatcode.tabgreater.ui.watchlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.ui.components.AddTickerFab
import com.neatcode.tabgreater.ui.components.TGAppBar
import com.neatcode.tabgreater.ui.manager.WatchlistManagerSheet
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * The Watchlists screen: app bar, watchlist tabs, filter chips and the tile grid with the
 * "+ Ticker" pill floating over it.
 *
 * A horizontal swipe switches watchlists. A tap opens the chart, a long press ticks the tile for a bulk
 * action, and a long press followed by movement drags the tile to a new place (see
 * [rememberReorderState]). While tiles are ticked the app bar becomes a [SelectionActionBar].
 *
 * The bottom navigation bar is chrome owned by the navigation host, so the grid only needs its
 * own 8 dp bottom padding — the tiles stop exactly at the nav bar and scroll under the FAB.
 */
@Composable
fun WatchlistScreen(
    onOpenSearch: (Long) -> Unit,
    onOpenChart: (MarketKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WatchlistViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var openSheet by rememberSaveable { mutableStateOf(WatchlistSheet.NONE) }
    var showManager by rememberSaveable { mutableStateOf(false) }
    var showPriceStatus by rememberSaveable { mutableStateOf(false) }
    val watchlistIds = remember(state.watchlists) { state.watchlists.map { it.id } }
    val selectedPage = state.selectedId?.let(watchlistIds::indexOf)?.coerceAtLeast(0) ?: 0
    val pagerState = rememberPagerState(initialPage = selectedPage) { watchlistIds.size }
    val scope = rememberCoroutineScope()
    val motionScaleFactor = scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    var pagerAligned by remember(pagerState) { mutableStateOf(false) }
    var reorderingWatchlistId by remember { mutableStateOf<Long?>(null) }
    val currentWatchlistIds by rememberUpdatedState(watchlistIds)
    val committedWatchlistId by rememberUpdatedState(state.selectedId)
    val visualSelectedId = watchlistIds.getOrNull(pagerState.currentPage) ?: state.selectedId
    val navigateToWatchlist: (Long) -> Unit = { id ->
        val page = watchlistIds.indexOf(id)
        if (page >= 0) scope.launch { pagerState.animateScrollToPage(page) }
    }
    val backgroundTaps = remember { MutableInteractionSource() }

    // Keep the pager keyed to the persisted selection after restore or a watchlist-list mutation.
    LaunchedEffect(watchlistIds, state.selectedId) {
        pagerAligned = false
        val selected = state.selectedId?.let(watchlistIds::indexOf) ?: -1
        if (selected >= 0) {
            if (pagerState.currentPage != selected || pagerState.currentPageOffsetFraction != 0f) {
                pagerState.scrollToPage(selected)
            }
            pagerAligned = true
        }
    }
    // A drag only previews pages. Persist the active watchlist once Pager reports a settled page.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage to pagerAligned }
            .distinctUntilChanged()
            .collect { (page, aligned) ->
                if (!aligned) return@collect
                val id = currentWatchlistIds.getOrNull(page) ?: return@collect
                if (id != committedWatchlistId) viewModel.selectWatchlist(id)
            }
    }

    BackHandler(enabled = state.isSelecting) { viewModel.clearSelection() }
    LifecycleStartEffect(viewModel) {
        viewModel.onForeground()
        onStopOrDispose { viewModel.onBackground() }
    }

    Column(modifier.fillMaxSize().background(TG.Background)) {
        if (state.isSelecting) {
            SelectionActionBar(
                count = state.selectedIds.size,
                onClose = viewModel::clearSelection,
                onMoveToTop = viewModel::moveSelectedToTop,
                onColour = { openSheet = WatchlistSheet.COLOUR },
                onMoveToList = { openSheet = WatchlistSheet.MOVE },
                onDelete = viewModel::deleteSelected,
            )
        } else {
            TGAppBar {
                PriceStatusButton(state.priceRefreshStatus, onClick = { showPriceStatus = true })
            }
        }
        WatchlistTabs(
            watchlists = state.watchlists,
            selectedId = visualSelectedId,
            onSelect = navigateToWatchlist,
            onManage = { showManager = true },
        )
        FilterChipsRow(
            period = state.period,
            tileSize = state.tileSize,
            sort = state.sort,
            onPeriodClick = { openSheet = WatchlistSheet.PERIOD },
            onTileSizeClick = { openSheet = WatchlistSheet.SIZE },
            onSortClick = { openSheet = WatchlistSheet.SORT },
        )

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // Tapping the grid background is the third way out of selection mode.
                .then(
                    if (state.isSelecting) {
                        Modifier.clickable(
                            interactionSource = backgroundTaps,
                            indication = null,
                            onClick = viewModel::clearSelection,
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (state.watchlists.isEmpty()) {
                Text(
                    text = stringResource(R.string.watchlist_empty),
                    style = TGType.chip,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val flingBehavior = rememberWatchlistPagerFlingBehavior(
                        state = pagerState,
                        pageWidth = maxWidth,
                        motionScaleFactor = motionScaleFactor,
                    )
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                        userScrollEnabled = state.watchlists.size > 1 &&
                            !state.isSelecting && reorderingWatchlistId == null,
                        key = { page -> watchlistIds[page] },
                        flingBehavior = flingBehavior,
                    ) { page ->
                        val watchlist = state.watchlists[page]
                        WatchlistPagerPage(
                            watchlist = watchlist,
                            screenState = state,
                            viewModel = viewModel,
                            onOpenChart = onOpenChart,
                            onReorderActive = { id, active ->
                                if (active) reorderingWatchlistId = id
                                else if (reorderingWatchlistId == id) reorderingWatchlistId = null
                            },
                        )
                    }
                }
            }

            AddTickerFab(
                onClick = {
                    (watchlistIds.getOrNull(pagerState.settledPage) ?: state.selectedId)?.let(onOpenSearch)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
            )
        }
    }

    if (showPriceStatus) {
        PriceStatusSheet(
            status = state.priceRefreshStatus,
            issues = state.priceIssues,
            refreshInProgress = state.refreshInProgress,
            onRetry = viewModel::refresh,
            onDismiss = { showPriceStatus = false },
        )
    }

    if (showManager) {
        WatchlistManagerSheet(
            onDismiss = { showManager = false },
            onSelectWatchlist = navigateToWatchlist,
        )
    }

    WatchlistSheetHost(
        open = openSheet,
        state = state,
        viewModel = viewModel,
        onClose = { openSheet = WatchlistSheet.NONE },
    )
}

/** The two sheets that act on the ticked tiles and mean nothing without them. */
private val WatchlistSheet.actsOnSelection: Boolean
    get() = this == WatchlistSheet.COLOUR || this == WatchlistSheet.MOVE

/**
 * Hosts whichever chip or tile-action sheet is open; each one applies its change and closes.
 *
 * `openSheet` is saved across process death but the selection it acts on is not, so a restored
 * COLOUR / MOVE sheet would sit on top of a screen with nothing ticked and every option would be
 * a silent no-op. Those two are closed again as soon as they are seen without a selection.
 */
@Composable
private fun WatchlistSheetHost(
    open: WatchlistSheet,
    state: WatchlistUiState,
    viewModel: WatchlistViewModel,
    onClose: () -> Unit,
) {
    val orphaned = open.actsOnSelection && state.selectedIds.isEmpty()
    LaunchedEffect(orphaned) { if (orphaned) onClose() }
    if (orphaned) return

    when (open) {
        WatchlistSheet.NONE -> Unit

        WatchlistSheet.PERIOD -> PeriodSheet(
            current = state.period,
            onPick = { viewModel.setPeriod(it); onClose() },
            onDismiss = onClose,
        )

        WatchlistSheet.SIZE -> TileSizeSheet(
            current = state.tileSize,
            onPick = { viewModel.setTileSize(it); onClose() },
            onDismiss = onClose,
        )

        WatchlistSheet.SORT -> SortSheet(
            current = state.sort,
            onPick = { viewModel.setSort(it); onClose() },
            onDismiss = onClose,
        )

        WatchlistSheet.COLOUR -> {
            // A tick is only shown when every ticked tile already carries the same colour.
            val accents = state.tiles
                .filter { it.itemId in state.selectedIds }
                .map { it.accent }
                .distinct()
            TileColourSheet(
                checkedColour = accents.singleOrNull(),
                noneChecked = accents.size == 1 && accents[0] == null,
                onPick = { viewModel.colourSelected(it); onClose() },
                onDismiss = onClose,
            )
        }

        WatchlistSheet.MOVE -> MoveToWatchlistSheet(
            targets = state.watchlists.filter { it.id != state.selectedId },
            itemCounts = state.itemCounts,
            onPick = { viewModel.moveSelectedTo(it); onClose() },
            onDismiss = onClose,
        )
    }
}
