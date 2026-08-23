package com.neatcode.tabgreater.ui.watchlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.TGDimens
import com.neatcode.tabgreater.ui.components.AddTickerFab
import com.neatcode.tabgreater.ui.components.TGAppBar
import com.neatcode.tabgreater.ui.manager.WatchlistManagerSheet
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType
import org.koin.androidx.compose.koinViewModel

/**
 * The Watchlists screen: app bar, watchlist tabs, filter chips and the tile grid with the
 * "+ Ticker" pill floating over it.
 *
 * Three gestures share the grid: a tap opens the chart, a long press ticks the tile for a bulk
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
    val gridState = rememberLazyGridState()
    val reorder = rememberReorderState(
        gridState = gridState,
        onMove = viewModel::moveTile,
        onDragEnd = viewModel::commitOrder,
        onLongPress = viewModel::toggleSelection,
    )
    val backgroundTaps = remember { MutableInteractionSource() }

    BackHandler(enabled = state.isSelecting) { viewModel.clearSelection() }

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
            TGAppBar()
        }
        WatchlistTabs(
            watchlists = state.watchlists,
            selectedId = state.selectedId,
            onSelect = viewModel::selectWatchlist,
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
            if (state.tiles.isEmpty()) {
                Text(
                    text = stringResource(R.string.watchlist_empty),
                    style = TGType.chip,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(state.tileSize.columns),
                    modifier = Modifier.fillMaxSize(),
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = TGDimens.GRID_MARGIN_DP.dp,
                        end = TGDimens.GRID_MARGIN_DP.dp,
                        // The chip row's own 8 dp bottom pad is the gap to the first tile row.
                        top = 0.dp,
                        // Tiles scroll under the FAB, but the last row can still scroll clear of it.
                        bottom = (TGDimens.GRID_GAP_DP + TGDimens.FAB_H_DP + 16).dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(TGDimens.GRID_GAP_DP.dp),
                    verticalArrangement = Arrangement.spacedBy(TGDimens.GRID_GAP_DP.dp),
                ) {
                    items(state.tiles, key = { it.itemId }) { tile ->
                        val dragging = tile.itemId == reorder.draggingItemId
                        TickerTile(
                            tile = tile,
                            size = state.tileSize,
                            modifier = Modifier
                                .fillMaxWidth()
                                // The tile under the finger must not also animate to its new slot.
                                .then(if (dragging) Modifier else Modifier.animateItem())
                                .reorderable(reorder, tile.itemId, enabled = !state.isSelecting)
                                .clickable {
                                    when {
                                        // The release that ends a long press is not a tap.
                                        reorder.consumeLongPress() -> Unit
                                        state.isSelecting -> viewModel.toggleSelection(tile.itemId)
                                        else -> onOpenChart(tile.key)
                                    }
                                },
                            selected = tile.itemId in state.selectedIds,
                            shrinkZeros = state.shrinkZeros,
                        )
                    }
                }
            }

            AddTickerFab(
                onClick = { state.selectedId?.let(onOpenSearch) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
            )
        }
    }

    if (showManager) {
        WatchlistManagerSheet(
            onDismiss = { showManager = false },
            onSelectWatchlist = viewModel::selectWatchlist,
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
