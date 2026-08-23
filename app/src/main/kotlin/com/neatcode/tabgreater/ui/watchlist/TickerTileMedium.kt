package com.neatcode.tabgreater.ui.watchlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
 * The **Medium** tile: 348 × 81 dp at the 360 dp reference frame, one full-width row.
 *
 * Two fixed columns and one elastic one, so every row in the list lines up whatever the screen is
 * wide: exchange + pair in a fixed 109 dp column on the left, the price, the absolute change and
 * the `High / Low / Volume` micro-rows right-aligned in a fixed 114 dp column flush against the
 * tile's end inset, and the 52 dp-high sparkline taking whatever is left in between (109 dp at
 * 360 dp, which is where `SPARK_MEDIUM_W_DP` was measured). The micro-rows are pushed to the
 * bottom edge by a weighted spacer, so a smaller font scale opens the gap under the price instead
 * of lifting them off the baseline.
 *
 * The row's height is the measured 348 / 4.3 dp, or more when a large system font scale needs it
 * ([tileRowHeight]) — otherwise the Volume row would be measured to zero and disappear.
 */
@Composable
internal fun MediumTile(
    tile: TileUiState,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    shrinkZeros: Boolean = true,
) {
    val trend = if (tile.isUp) TG.Up else TG.Down
    TileSurface(TGDimens.TILE_ASPECT_MEDIUM, selected, modifier, growable = true) {
        // The card takes its height from the row below, so the stripe has to be told what to fill.
        Box(Modifier.matchParentSize()) { TileAccentStripe(tile.accent) }

        Row(
            modifier = Modifier
                .tileRowHeight(TGDimens.TILE_ASPECT_MEDIUM)
                .padding(start = TILE_PADDING_DP.dp, end = TILE_PADDING_DP.dp),
        ) {
            TileHeader(
                tile = tile,
                modifier = Modifier
                    .width(TGDimens.TILE_MEDIUM_LEFT_DP.dp)
                    .padding(top = TILE_TOP_DP.dp),
            )

            Sparkline(
                values = tile.spark,
                color = trend,
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
                    .height(TGDimens.SPARK_MEDIUM_H_DP.dp),
            )

            Column(
                modifier = Modifier
                    .width(TGDimens.TILE_MEDIUM_RIGHT_DP.dp)
                    .fillMaxHeight()
                    .padding(
                        top = TGDimens.TILE_PRICE_TOP_DP.dp,
                        bottom = TGDimens.TILE_DETAIL_BOTTOM_DP.dp,
                    ),
                horizontalAlignment = Alignment.End,
            ) {
                TilePriceLines(tile, trend, shrinkZeros)
                Spacer(Modifier.weight(1f))
                TileDetails(tile)
            }
        }
    }
}

@Preview(name = "Medium up", widthDp = 348, backgroundColor = 0xFF141515, showBackground = true)
@Composable
private fun MediumTileUpPreview() {
    TickerTile(tile = previewTile(up = true), size = TileSize.MEDIUM)
}

@Preview(name = "Medium down", widthDp = 348, backgroundColor = 0xFF141515, showBackground = true)
@Composable
private fun MediumTileDownPreview() {
    TickerTile(tile = previewTile(up = false, accent = 0xFFFF9E99), size = TileSize.MEDIUM)
}
