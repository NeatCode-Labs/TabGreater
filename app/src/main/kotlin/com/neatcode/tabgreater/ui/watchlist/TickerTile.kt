package com.neatcode.tabgreater.ui.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.PriceFormat
import com.neatcode.tabgreater.core.model.TGDimens
import com.neatcode.tabgreater.core.model.TileSize
import com.neatcode.tabgreater.ui.components.ExchangeGlyph
import com.neatcode.tabgreater.ui.components.Sparkline
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType

/**
 * One ticker tile in the watchlist grid, drawn in the layout the watchlist's "Tickers Appearance"
 * setting asks for: [TileSize.SMALL] and [TileSize.COMPACT] are half-width,
 * [TileSize.MEDIUM] and [TileSize.LARGE] are full-width rows.
 *
 * Gestures are **not** handled here: the caller attaches click / long-press / drag modifiers
 * through [modifier], so the same tile works in the grid, in selection mode and while dragging.
 *
 * @param selected draws the selection-mode highlight (2 dp accent outline).
 * @param shrinkZeros renders the leading-zero compression (`0.0₃71501`).
 */
@Composable
fun TickerTile(
    tile: TileUiState,
    size: TileSize,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    shrinkZeros: Boolean = true,
) {
    val accessibleModifier = modifier.semantics { stateDescription = tile.statusDescription }
    when (size) {
        TileSize.SMALL -> SmallTile(tile, accessibleModifier, selected, shrinkZeros)
        TileSize.COMPACT -> CompactTile(tile, accessibleModifier, selected, shrinkZeros)
        TileSize.MEDIUM -> MediumTile(tile, accessibleModifier, selected, shrinkZeros)
        TileSize.LARGE -> LargeTile(tile, accessibleModifier, selected, shrinkZeros)
    }
}

/**
 * The Small ticker tile: 2.02 : 1, 8 dp corners, 1 dp `scrim` shadow,
 * exchange caps + badge and pair top-left, a 61 × 39 dp sparkline top-right, price and signed
 * percentage right-aligned at the bottom.
 *
 * The price uses the leading-zero compression, so `0.000071501` renders as `0.0₃71501`
 * (unless [shrinkZeros] is off). At larger system fonts the tile grows to keep every row visible.
 */
@Composable
private fun SmallTile(
    tile: TileUiState,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    shrinkZeros: Boolean = true,
) {
    val trend = if (tile.isUp) TG.Up else TG.Down
    val largeText = LocalDensity.current.fontScale > 1.3f
    TileSurface(
        modifier = modifier,
        aspectRatio = TGDimens.TILE_ASPECT_SMALL,
        selected = selected,
        growable = true,
    ) {
        Box(Modifier.matchParentSize()) { TileAccentStripe(tile.accent) }

        if (!largeText) Sparkline(
            values = tile.spark,
            color = trend,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 2.dp, end = 8.dp)
                .size(width = TGDimens.SPARK_W_DP.dp, height = TGDimens.SPARK_H_DP.dp),
        )

        Column(
            Modifier
                .fillMaxWidth()
                .tileRowHeight(TGDimens.TILE_ASPECT_SMALL)
                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = listOfNotNull(tile.exchangeLabel, tile.statusLabel).joinToString(" · "),
                    style = TGType.exchange,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                ExchangeGlyph(tile.key.exchange, size = 10.dp)
            }
            Text(
                text = tile.pair,
                style = TGType.pair,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Stop before the sparkline box (61 dp + 8 dp end inset) so long pairs ellipsise.
                modifier = Modifier.padding(end = if (largeText) 0.dp else (TGDimens.SPARK_W_DP + 4).dp),
            )

            if (largeText) Sparkline(
                values = tile.spark,
                color = trend,
                modifier = Modifier.fillMaxWidth().height(TGDimens.SPARK_H_DP.dp),
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = shrunkPrice(tile.priceText, shrinkZeros),
                style = TGType.price,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.align(Alignment.End),
            )
            Text(
                text = tile.changeText ?: PriceFormat.NO_VALUE,
                style = TGType.change,
                color = trend,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

/**
 * Renders "shrink zeros": `0.0` followed by a subscript count of the remaining zeros
 * and the significant digits. Prices that need no compression come back as plain text.
 */
@Composable
internal fun shrunkPrice(priceText: String?, shrinkZeros: Boolean = true): AnnotatedString {
    val text = priceText ?: PriceFormat.NO_VALUE
    return remember(text, shrinkZeros) {
        val shrunk = if (shrinkZeros) PriceFormat.shrinkZeros(text) else PriceFormat.shrinkZeros(text, minZeros = 0)
        buildAnnotatedString {
            append(shrunk.prefix)
            val zeros = shrunk.zeroCount
            if (zeros != null) {
                // Subscript sits just below the baseline and inside the tight line box
                // (BaselineShift.Subscript = -0.5 pushes it out of the 24 sp line).
                withStyle(SpanStyle(baselineShift = BaselineShift(SUBSCRIPT_SHIFT), fontSize = 0.62.em)) {
                    append(zeros.toString())
                }
                append(shrunk.rest)
            }
        }
    }
}

internal val PreviewSpark = FloatArray(48) { index ->
    val t = index / 47f
    (t * t * 0.8f + 0.2f * kotlin.math.sin(index * 0.7f) + 0.5f)
}

/**
 * Preview-only fixture: one rising and one falling market with realistic numbers, so every tile
 * size preview shows the same data and can be compared side by side.
 */
internal fun previewTile(up: Boolean = true, accent: Long? = null): TileUiState = if (up) {
    TileUiState(
        itemId = 1,
        key = MarketKey("binance:ETH/USDT"),
        exchangeLabel = "BINANCE",
        pair = "ETH/USDT",
        priceText = PriceFormat.formatPrice(2785.05, 2),
        changeText = PriceFormat.formatChangePct(0.04),
        absChangeText = "+1.10 (0.04%)",
        highText = PriceFormat.formatPrice(2816.45, 2),
        lowText = PriceFormat.formatPrice(2732.9, 2),
        volumeText = PriceFormat.formatVolume(1_000_000.0),
        isUp = true,
        spark = PreviewSpark,
        accent = accent,
    )
} else {
    TileUiState(
        itemId = 2,
        key = MarketKey("kraken:BTC/EUR"),
        exchangeLabel = "KRAKEN",
        pair = "BTC/EUR",
        priceText = PriceFormat.formatPrice(38498.5, 1),
        changeText = PriceFormat.formatChangePct(-0.41),
        absChangeText = "-159.0 (0.41%)",
        highText = PriceFormat.formatPrice(39323.5, 1),
        lowText = PriceFormat.formatPrice(38186.0, 1),
        volumeText = PriceFormat.formatVolume(713_000_000.0),
        isUp = false,
        spark = FloatArray(48) { index -> 1f - PreviewSpark[index] },
        accent = accent,
    )
}

@Preview(name = "Small up", widthDp = 170, backgroundColor = 0xFF141515, showBackground = true)
@Composable
private fun SmallTileUpPreview() {
    TickerTile(tile = previewTile(up = true), size = TileSize.SMALL)
}

@Preview(name = "Small down", widthDp = 170, backgroundColor = 0xFF141515, showBackground = true)
@Composable
private fun SmallTileDownPreview() {
    TickerTile(
        tile = previewTile(up = false, accent = 0xFFFFBF66).copy(
            pair = "EOS/BTC",
            priceText = PriceFormat.formatPrice(0.000071501, 9),
        ),
        size = TileSize.SMALL,
    )
}

private const val SUBSCRIPT_SHIFT = -0.18f
