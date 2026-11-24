package com.neatcode.tabgreater.ui.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.TGDimens
import com.neatcode.tabgreater.core.model.TileSize
import com.neatcode.tabgreater.core.model.Watchlist
import com.neatcode.tabgreater.ui.theme.TGType

@Composable
internal fun WatchlistPagerPage(
    watchlist: Watchlist,
    screenState: WatchlistUiState,
    viewModel: WatchlistViewModel,
    onOpenChart: (MarketKey) -> Unit,
    onReorderActive: (watchlistId: Long, active: Boolean) -> Unit,
) {
    val previewFlow = remember(viewModel, watchlist.id) { viewModel.previewPageState(watchlist.id) }
    val preview by previewFlow.collectAsStateWithLifecycle(initialValue = null)
    val isSelectedPage = watchlist.id == screenState.selectedId
    val activeDataReady = isSelectedPage &&
        screenState.tilesWatchlistId == watchlist.id && screenState.tilesReady
    val page = if (activeDataReady) {
        WatchlistPageUiState(
            watchlistId = watchlist.id,
            period = screenState.period,
            tileSize = screenState.tileSize,
            sort = screenState.sort,
            tiles = screenState.tiles,
        )
    } else {
        preview
    }

    val gridState = rememberLazyGridState()
    val reorder = rememberReorderState(
        gridState = gridState,
        onMove = viewModel::moveTile,
        onDragEnd = viewModel::commitOrder,
        onLongPress = viewModel::toggleSelection,
    )
    LaunchedEffect(isSelectedPage, reorder.draggingItemId) {
        onReorderActive(watchlist.id, isSelectedPage && reorder.draggingItemId != null)
    }

    Box(Modifier.fillMaxSize()) {
        when {
            page == null -> Unit
            page.tiles.isEmpty() -> Text(
                text = stringResource(R.string.watchlist_empty),
                style = TGType.chip,
                modifier = Modifier.align(Alignment.Center),
            )
            else -> WatchlistTileGrid(
                page = page,
                screenState = screenState,
                isSelectedPage = isSelectedPage,
                activeDataReady = activeDataReady,
                gridState = gridState,
                reorder = reorder,
                onOpenChart = onOpenChart,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun WatchlistTileGrid(
    page: WatchlistPageUiState,
    screenState: WatchlistUiState,
    isSelectedPage: Boolean,
    activeDataReady: Boolean,
    gridState: LazyGridState,
    reorder: ReorderState,
    onOpenChart: (MarketKey) -> Unit,
    viewModel: WatchlistViewModel,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val gridWidthPx = with(density) { maxWidth.roundToPx() }
        val gridMarginPx = with(density) { TGDimens.GRID_MARGIN_DP.dp.roundToPx() }
        val gridGapPx = with(density) { TGDimens.GRID_GAP_DP.dp.roundToPx() }
        val gridColumnsWidthPx = (gridWidthPx - 2 * gridMarginPx - gridGapPx).coerceAtLeast(0)
        val firstSmallColumnIsOnePixelWider =
            page.tileSize == TileSize.SMALL && gridColumnsWidthPx % 2 != 0
        val selecting = isSelectedPage && screenState.isSelecting
        val canReorder = activeDataReady && !selecting

        LazyVerticalGrid(
            columns = GridCells.Fixed(page.tileSize.columns),
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            contentPadding = PaddingValues(
                start = TGDimens.GRID_MARGIN_DP.dp,
                end = TGDimens.GRID_MARGIN_DP.dp,
                top = 0.dp,
                bottom = (TGDimens.GRID_GAP_DP + TGDimens.FAB_H_DP + 16).dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(TGDimens.GRID_GAP_DP.dp),
            verticalArrangement = Arrangement.spacedBy(TGDimens.GRID_GAP_DP.dp),
        ) {
            itemsIndexed(page.tiles, key = { _, tile -> tile.itemId }) { index, tile ->
                val dragging = tile.itemId == reorder.draggingItemId
                val itemModifier = Modifier
                    .fillMaxWidth()
                    .then(if (dragging) Modifier else Modifier.animateItem())
                val peer = if (page.tileSize == TileSize.SMALL) {
                    page.tiles.getOrNull(if (index % 2 == 0) index + 1 else index - 1)
                } else {
                    null
                }
                val selectedIds = if (activeDataReady) screenState.selectedIds else emptySet()
                if (peer != null) {
                    SmallTileCell(
                        tile = tile,
                        peer = peer,
                        selectedIds = selectedIds,
                        isSelecting = selecting,
                        shrinkZeros = screenState.shrinkZeros,
                        reorder = reorder,
                        reorderEnabled = canReorder,
                        peerWidthAdjustmentPx = if (firstSmallColumnIsOnePixelWider) {
                            if (index % 2 == 0) -1 else 1
                        } else {
                            0
                        },
                        modifier = itemModifier,
                        onOpenChart = onOpenChart,
                        onToggleSelection = viewModel::toggleSelection,
                    )
                } else {
                    TickerTile(
                        tile = tile,
                        size = page.tileSize,
                        modifier = itemModifier
                            .reorderable(reorder, tile.itemId, enabled = canReorder)
                            .clickable {
                                when {
                                    reorder.consumeLongPress() -> Unit
                                    selecting -> viewModel.toggleSelection(tile.itemId)
                                    else -> onOpenChart(tile.key)
                                }
                            },
                        selected = tile.itemId in selectedIds,
                        shrinkZeros = screenState.shrinkZeros,
                    )
                }
            }
        }
    }
}
