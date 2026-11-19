package com.neatcode.tabgreater.ui.watchlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.TileSize

/** One stable Small grid item whose measured height matches its neighbour in the same row. */
@Composable
internal fun SmallTileCell(
    tile: TileUiState,
    peer: TileUiState,
    selectedIds: Set<Long>,
    isSelecting: Boolean,
    shrinkZeros: Boolean,
    reorder: ReorderState,
    modifier: Modifier = Modifier,
    peerWidthAdjustmentPx: Int = 0,
    onOpenChart: (MarketKey) -> Unit,
    onToggleSelection: (Long) -> Unit,
) {
    val itemModifier = Modifier
        .reorderable(reorder, tile.itemId, enabled = !isSelecting)
        .clickable {
            when {
                // The release that ends a long press is not a tap.
                reorder.consumeLongPress() -> Unit
                isSelecting -> onToggleSelection(tile.itemId)
                else -> onOpenChart(tile.key)
            }
        }

    Layout(
        modifier = modifier,
        content = {
            TickerTile(
                tile = tile,
                size = TileSize.SMALL,
                modifier = itemModifier,
                selected = tile.itemId in selectedIds,
                shrinkZeros = shrinkZeros,
            )
            // This copy is measured only; it is neither placed nor exposed to accessibility,
            // and has no click or drag modifiers.
            Box(
                propagateMinConstraints = true,
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            ) {
                TickerTile(
                    tile = peer,
                    size = TileSize.SMALL,
                    selected = false,
                    shrinkZeros = shrinkZeros,
                )
            }
        },
    ) { measurables, constraints ->
        val peerWidth = (constraints.maxWidth + peerWidthAdjustmentPx).coerceAtLeast(0)
        val peerCopy = measurables[1]
        val peerPlaceable = peerCopy.measure(constraints.copy(minWidth = peerWidth, maxWidth = peerWidth))
        val actual = measurables[0].measure(
            constraints.copy(minHeight = maxOf(constraints.minHeight, peerPlaceable.height)),
        )
        layout(actual.width, actual.height) {
            actual.place(0, 0)
        }
    }
}
