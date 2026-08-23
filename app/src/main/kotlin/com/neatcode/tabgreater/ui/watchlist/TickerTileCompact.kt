package com.neatcode.tabgreater.ui.watchlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
 * The **Compact** tile: 170 × 119 dp, two columns.
 *
 * It is the Small tile plus two blocks — the absolute change `+1.10 (0.04%)` under the price and
 * the right-aligned `High / Low / Volume` micro-rows anchored to the bottom edge. The bottom-left
 * corner stays empty: the app has no accounts, so there are no balances to print there.
 *
 * Everything is stacked in one column instead of being pinned to three corners, so no two blocks
 * can ever be drawn on top of each other: the header + sparkline band on top, then the slack, then
 * price, absolute change and micro-rows against the bottom edge. At 170 × 119 dp the
 * slack puts the price line exactly at its design offset of 43 dp; a large system font scale eats the
 * slack first and then makes the tile taller ([tileRowHeight]) rather than overlapping anything.
 */
@Composable
internal fun CompactTile(
    tile: TileUiState,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    shrinkZeros: Boolean = true,
) {
    val trend = if (tile.isUp) TG.Up else TG.Down
    TileSurface(TGDimens.TILE_ASPECT_COMPACT, selected, modifier, growable = true) {
        // The card takes its height from the column below, so the stripe is told what to fill.
        Box(Modifier.matchParentSize()) { TileAccentStripe(tile.accent) }

        Column(Modifier.tileRowHeight(TGDimens.TILE_ASPECT_COMPACT)) {
            Box(Modifier.fillMaxWidth()) {
                Sparkline(
                    values = tile.spark,
                    color = trend,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = SPARK_TOP_DP.dp, end = TILE_PADDING_DP.dp)
                        .size(width = TGDimens.SPARK_W_DP.dp, height = TGDimens.SPARK_H_DP.dp),
                )

                TileHeader(
                    tile = tile,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = TILE_PADDING_DP.dp, top = TILE_TOP_DP.dp),
                    // Stop before the sparkline box (61 dp + 8 dp end inset) so long pairs ellipsise.
                    pairEndPadding = (TGDimens.SPARK_W_DP + 4).dp,
                )
            }

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = TILE_PADDING_DP.dp,
                        end = TILE_PADDING_DP.dp,
                        bottom = TGDimens.TILE_DETAIL_BOTTOM_DP.dp,
                    ),
                horizontalAlignment = Alignment.End,
            ) {
                TilePriceLines(tile, trend, shrinkZeros)
                Spacer(Modifier.height(TGDimens.TILE_COMPACT_PRICE_GAP_DP.dp))
                TileDetails(tile)
            }
        }
    }
}

@Preview(name = "Compact up", widthDp = 170, backgroundColor = 0xFF141515, showBackground = true)
@Composable
private fun CompactTileUpPreview() {
    TickerTile(tile = previewTile(up = true), size = TileSize.COMPACT)
}

@Preview(name = "Compact down", widthDp = 170, backgroundColor = 0xFF141515, showBackground = true)
@Composable
private fun CompactTileDownPreview() {
    TickerTile(tile = previewTile(up = false, accent = 0xFFFFBF66), size = TileSize.COMPACT)
}
