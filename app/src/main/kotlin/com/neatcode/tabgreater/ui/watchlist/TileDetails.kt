package com.neatcode.tabgreater.ui.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.core.model.PriceFormat
import com.neatcode.tabgreater.core.model.TGDimens
import com.neatcode.tabgreater.ui.components.ExchangeGlyph
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType

/** 8 dp corners on every tile size. */
internal val TileShape = RoundedCornerShape(TGDimens.TILE_CORNER_DP.dp)

/**
 * The card every tile size sits in: [aspectRatio] of the grid column, 8 dp corners, a 1 dp `scrim`
 * shadow, the `surface` fill and — in selection mode — a 2 dp accent outline.
 *
 * Gestures are attached by the caller through [modifier], exactly like the Small tile.
 *
 * @param growable `true` lets the card take its height from its content instead of the aspect
 *   ratio, so a tile whose text no longer fits at a large system font scale gets taller rather
 *   than clipping its last row. The content is then responsible for its own minimum height —
 *   see [tileRowHeight].
 */
@Composable
internal fun TileSurface(
    aspectRatio: Float,
    selected: Boolean,
    modifier: Modifier = Modifier,
    growable: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (growable) Modifier else Modifier.aspectRatio(aspectRatio))
            .shadow(
                elevation = TGDimens.TILE_ELEVATION_DP.dp,
                shape = TileShape,
                ambientColor = TG.Scrim,
                spotColor = TG.Scrim,
            )
            .clip(TileShape)
            .background(TG.Surface)
            .then(if (selected) Modifier.border(TILE_SELECTION_BORDER_DP.dp, TG.Accent, TileShape) else Modifier),
        content = content,
    )
}

/**
 * Height rule for the content of a [TileSurface] with `growable = true`: `width / aspectRatio` —
 * the pixel-measured design height — unless the content genuinely needs more, in which case the
 * tile grows by exactly that much.
 *
 * The content is still measured with a **fixed** height, so `fillMaxHeight` and weighted spacers
 * inside it keep pinning the micro-rows to the bottom edge the way they do at font scale 1.0;
 * only the height they are pinned inside of changes.
 */
internal fun Modifier.tileRowHeight(aspectRatio: Float): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val width = constraints.maxWidth
    val designHeight = (width / aspectRatio).toInt()
    val height = maxOf(designHeight, measurable.minIntrinsicHeight(width))
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { placeable.place(0, 0) }
}

/** The user's accent stripe on the left edge; draws nothing when the ticker has no colour. */
@Composable
internal fun TileAccentStripe(accent: Long?, modifier: Modifier = Modifier) {
    if (accent == null) return
    Box(
        modifier
            .fillMaxHeight()
            .width(TGDimens.TILE_STRIPE_DP.dp)
            .background(Color(accent)),
    )
}

/** Exchange caps + badge above the pair — the top-left block of every tile size. */
@Composable
internal fun TileHeader(
    tile: TileUiState,
    modifier: Modifier = Modifier,
    pairEndPadding: Dp = 0.dp,
) {
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GLYPH_GAP_DP.dp),
        ) {
            Text(
                text = tile.exchangeLabel,
                style = TGType.exchange,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ExchangeGlyph(tile.key.exchange, size = GLYPH_DP.dp)
        }
        Text(
            text = tile.pair,
            style = TGType.pair,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = pairEndPadding),
        )
    }
}

/**
 * Price and, under it, the absolute change with the unsigned percentage in brackets
 * (`+1.10 (0.04%)`) — both right-aligned and tinted with the trend colour.
 */
@Composable
internal fun ColumnScope.TilePriceLines(
    tile: TileUiState,
    trend: Color,
    shrinkZeros: Boolean,
) {
    Text(
        text = shrunkPrice(tile.priceText, shrinkZeros),
        style = TGType.price,
        maxLines = 1,
        textAlign = TextAlign.End,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.align(Alignment.End),
    )
    Text(
        text = tile.absChangeText ?: PriceFormat.NO_VALUE,
        style = TGType.absChange,
        color = trend,
        maxLines = 1,
        textAlign = TextAlign.End,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.align(Alignment.End),
    )
}

/**
 * The right-aligned `High / Low / Volume` micro-rows shared by Compact, Medium and Large.
 * Large passes a wider [spacing]; the values are already formatted by the view model.
 */
@Composable
internal fun TileDetails(
    tile: TileUiState,
    modifier: Modifier = Modifier,
    spacing: Dp = TGDimens.TILE_DETAIL_SPACING_DP.dp,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        TileDetailRow(stringResource(R.string.tile_high), tile.highText)
        TileDetailRow(stringResource(R.string.tile_low), tile.lowText)
        TileDetailRow(stringResource(R.string.tile_volume), tile.volumeText)
    }
}

/** One `High 2,816.45` row: a tertiary 8 sp label and a secondary 9 sp value on one baseline. */
@Composable
private fun TileDetailRow(label: String, value: String?) {
    Row(
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(text = label, style = TGType.tileDetailLabel, maxLines = 1)
        Spacer(Modifier.width(TGDimens.TILE_DETAIL_GAP_DP.dp))
        Text(
            text = value ?: PriceFormat.NO_VALUE,
            style = TGType.tileDetailValue,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val GLYPH_DP = 10
private const val GLYPH_GAP_DP = 4

/** Horizontal text inset of every tile size. */
internal const val TILE_PADDING_DP = 8

/** Top inset of the exchange caps line. */
internal const val TILE_TOP_DP = 4

/** Top inset of the Small / Compact sparkline box, so it ends at y = 41 dp. */
internal const val SPARK_TOP_DP = 2

/** Selection-mode outline. */
internal const val TILE_SELECTION_BORDER_DP = 2
