package com.neatcode.tabgreater.ui.watchlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neatcode.tabgreater.core.model.TGDimens
import com.neatcode.tabgreater.core.model.TileSize
import com.neatcode.tabgreater.ui.components.Sparkline
import com.neatcode.tabgreater.ui.theme.TG

/**
 * The **Large** tile: 348 × 186 dp, one full-width card.
 *
 * Exchange + pair top-left, price and absolute change top-right, a sparkline band between
 * y 48 dp and y 135 dp with a much stronger area gradient than the small tiles, and the
 * `High / Low / Volume` micro-rows in the bottom-right corner (the bottom-left corner stays
 * empty — the app has no account balances to show there).
 *
 * The band starts just clear of the accent stripe and bleeds off the right edge.
 *
 * Header, band and micro-rows are stacked rather than pinned to fixed offsets, so a large system
 * font scale pushes the band down and makes the card taller ([tileRowHeight]) instead of letting
 * the absolute-change line be drawn over the top of the band.
 */
@Composable
internal fun LargeTile(
    tile: TileUiState,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    shrinkZeros: Boolean = true,
) {
    val trend = if (tile.isUp) TG.Up else TG.Down
    TileSurface(TGDimens.TILE_ASPECT_LARGE, selected, modifier, growable = true) {
        // The card takes its height from the column below, so the stripe is told what to fill.
        Box(Modifier.matchParentSize()) { TileAccentStripe(tile.accent) }

        Column(Modifier.tileRowHeight(TGDimens.TILE_ASPECT_LARGE)) {
            // The header block is at least as tall as the band's measured 48 dp start, and taller
            // when a large font scale needs it — the band follows instead of being drawn over.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TGDimens.SPARK_LARGE_TOP_DP.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = TILE_PADDING_DP.dp, end = TILE_PADDING_DP.dp),
                ) {
                    TileHeader(
                        tile = tile,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = TILE_TOP_DP.dp),
                    )
                    Column(
                        modifier = Modifier.padding(top = TGDimens.TILE_PRICE_TOP_DP.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        TilePriceLines(tile, trend, shrinkZeros)
                    }
                }
            }

            Sparkline(
                values = tile.spark,
                color = trend,
                modifier = Modifier
                    .padding(start = TGDimens.SPARK_LARGE_START_DP.dp)
                    .fillMaxWidth()
                    .height(TGDimens.SPARK_LARGE_H_DP.dp),
                fillAlpha = TGDimens.SPARK_LARGE_FILL_ALPHA,
                strokeWidth = TGDimens.SPARK_LARGE_STROKE_DP.dp,
            )

            Spacer(Modifier.weight(1f))

            TileDetails(
                tile = tile,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = TILE_PADDING_DP.dp, bottom = TGDimens.TILE_DETAIL_BOTTOM_DP.dp),
                spacing = TGDimens.TILE_DETAIL_SPACING_LARGE_DP.dp,
            )
        }
    }
}

@Preview(name = "Large up", widthDp = 348, backgroundColor = 0xFF141515, showBackground = true)
@Composable
private fun LargeTileUpPreview() {
    TickerTile(tile = previewTile(up = true), size = TileSize.LARGE)
}

@Preview(name = "Large down", widthDp = 348, backgroundColor = 0xFF141515, showBackground = true)
@Composable
private fun LargeTileDownPreview() {
    TickerTile(tile = previewTile(up = false, accent = 0xFF53A8B0), size = TileSize.LARGE)
}
