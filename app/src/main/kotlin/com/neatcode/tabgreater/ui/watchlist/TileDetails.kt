package com.neatcode.tabgreater.ui.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.core.model.PriceFormat
import com.neatcode.tabgreater.core.model.TGDimens
import com.neatcode.tabgreater.ui.components.ExchangeGlyph
import com.neatcode.tabgreater.ui.components.Sparkline
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
    propagateMinConstraints: Boolean = false,
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
        propagateMinConstraints = propagateMinConstraints,
        content = content,
    )
}

/** Give content its design minimum while allowing text to grow during actual measurement. */
internal fun Modifier.tileRowHeight(aspectRatio: Float): Modifier = layout { measurable, constraints ->
    val minimum = if (constraints.hasBoundedWidth) {
        (constraints.maxWidth / aspectRatio).toInt().coerceIn(constraints.minHeight, constraints.maxHeight)
    } else {
        constraints.minHeight
    }
    val placeable = measurable.measure(constraints.copy(minHeight = minimum))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
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

/** Short titles sit next to the chart. A title that needs the whole row starts below it. */
@Composable
internal fun HalfWidthTileHeader(tile: TileUiState, contextStyle: TextStyle) {
    Column(Modifier.fillMaxWidth().padding(horizontal = TILE_PADDING_DP.dp)) {
        Layout(
            modifier = Modifier.fillMaxWidth().padding(top = TILE_TOP_DP.dp),
            content = {
                TileExchange(tile)
                TileTitle(tile)
                if ((tile.spark?.size ?: 0) >= 2) {
                    Sparkline(tile.spark, if (tile.isUp) TG.Up else TG.Down)
                }
            },
        ) { children, constraints ->
            val width = constraints.maxWidth
            val chart = children.getOrNull(2)
            val chartWidth = if (chart == null) 0 else minOf(TGDimens.SPARK_W_DP.dp.roundToPx(), width / 2)
            val gap = if (chart == null) 0 else GLYPH_GAP_DP.dp.roundToPx()
            val besideChart = (width - chartWidth - gap).coerceAtLeast(0)
            val source = children[0].measure(Constraints(maxWidth = besideChart))
            val fullRow = chart != null && children[1].maxIntrinsicWidth(Constraints.Infinity) > besideChart
            val title = children[1].measure(Constraints(maxWidth = if (fullRow) width else besideChart))
            val chartHeight = when {
                fullRow -> TGDimens.SPARK_SHORT_H_DP.dp.roundToPx()
                '\n' in tile.pair -> minOf(TGDimens.SPARK_H_DP.dp.roundToPx(), source.height + title.height)
                else -> TGDimens.SPARK_H_DP.dp.roundToPx()
            }
            val spark = chart?.measure(Constraints.fixed(chartWidth, chartHeight))
            val titleY = if (fullRow) maxOf(source.height, spark?.height ?: 0) else source.height
            layout(width, maxOf(titleY + title.height, spark?.height ?: 0)) {
                source.placeRelative(0, 0)
                title.placeRelative(0, titleY)
                spark?.placeRelative(width - chartWidth, 0)
            }
        }
        TileCaption(tile, contextStyle)
    }
}

@Composable
internal fun TileHeader(tile: TileUiState, modifier: Modifier = Modifier) {
    Column(modifier) {
        TileExchange(tile)
        TileTitle(tile)
        TileCaption(tile, TGType.exchange)
    }
}

@Composable
private fun TileExchange(tile: TileUiState) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(GLYPH_GAP_DP.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GLYPH_GAP_DP.dp),
        ) {
            Text(tile.exchangeLabel, style = TGType.exchange)
            ExchangeGlyph(tile.key.exchange, size = GLYPH_DP.dp)
        }
        tile.statusLabel?.let { Text(it, style = TGType.exchange) }
    }
}

@Composable
private fun TileTitle(tile: TileUiState) {
    val title = tile.pair.substringBefore('\n')
    val slash = title.lastIndexOf('/')
    if (!tile.isEventOutcome && slash > 0 && title.none(Char::isWhitespace)) {
        // Keep the quote together when it needs a second line. Both pieces share a spoken label.
        FlowRow(modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(title) }) {
            Text(title.take(slash), style = TGType.pair)
            Text(title.substring(slash), style = TGType.pair)
        }
    } else {
        Text(title, style = TGType.pair)
    }
}

@Composable
private fun TileCaption(tile: TileUiState, contextStyle: TextStyle) {
    val subtitle = tile.pair.substringAfter('\n', "")
    val context = tile.marketContext?.takeIf(String::isNotBlank)
    if (tile.isEventOutcome && subtitle.isNotEmpty() && context != null) {
        Text(
            text = buildAnnotatedString {
                append(subtitle)
                withStyle(TGType.exchange.toSpanStyle()) { append(" \u00b7 $context") }
            },
            style = TGType.subLabel,
        )
    } else {
        if (subtitle.isNotEmpty()) Text(subtitle, style = TGType.subLabel)
        if (context != null) Text(context, style = contextStyle)
    }
}

@Composable
internal fun TilePriceLines(tile: TileUiState, trend: Color, shrinkZeros: Boolean) {
    TileMainPrice(tile, shrinkZeros)
    TileChange(tile, trend, shrinkZeros, showAmount = true)
}

/** Keep the whole percentage, dropping the optional absolute move if it cannot fit at reading size. */
@Composable
internal fun TileChange(tile: TileUiState, trend: Color, shrinkZeros: Boolean, showAmount: Boolean) {
    val style = TGType.change.copy(color = if (tile.changeText == null) TG.TextTertiary else trend, textAlign = TextAlign.End)
    val percent = tile.changeText ?: PriceFormat.NO_VALUE
    val combined = tile.absChangeText.takeIf { showAmount }
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            if (combined != null) {
                val amount = combined.substringBefore(" (")
                val sign = if (amount.startsWith('+')) "+" else ""
                val formatted = shrunkPrice(amount.removePrefix("+"), shrinkZeros)
                BasicText(
                    text = AnnotatedString(sign) + formatted + AnnotatedString(combined.removePrefix(amount)),
                    style = style,
                    modifier = Modifier.exactNumber(combined),
                )
            }
            BasicText(percent, style = style)
        },
    ) { candidates, constraints ->
        val candidate = candidates.first()
        val selected = if (candidate.maxIntrinsicWidth(Constraints.Infinity) <= constraints.maxWidth) {
            candidate
        } else {
            candidates.last()
        }
        val line = selected.measure(constraints.copy(minWidth = constraints.maxWidth))
        layout(line.width, line.height) { line.placeRelative(0, 0) }
    }
}

/** Reserve the right edge for the numeric value at every tile size. */
@Composable
internal fun TileMainPrice(tile: TileUiState, shrinkZeros: Boolean) {
    BasicText(
        text = shrunkPrice(tile.priceText, shrinkZeros),
        style = TGType.price.copy(textAlign = TextAlign.End),
        autoSize = TextAutoSize.StepBased(10.sp, TGType.price.fontSize, 1.sp),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.fillMaxWidth().exactNumber(tile.priceText),
    )
}

/** Speak all digits, including zeros that are drawn as a subscript. */
private fun Modifier.exactNumber(value: String?): Modifier = clearAndSetSemantics {
    text = AnnotatedString(value ?: PriceFormat.NO_VALUE)
}

/**
 * The right-aligned `High / Low / Volume` micro-rows shared by Compact, Medium and Large.
 * Large passes a wider [spacing]; the values are already formatted by the view model.
 */
@Composable
internal fun TileDetails(
    tile: TileUiState,
    shrinkZeros: Boolean,
    modifier: Modifier = Modifier,
    spacing: Dp = TGDimens.TILE_DETAIL_SPACING_DP.dp,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        TileDetailRow(stringResource(R.string.tile_high), tile.highText, shrinkZeros)
        TileDetailRow(stringResource(R.string.tile_low), tile.lowText, shrinkZeros)
        TileDetailRow(stringResource(R.string.tile_volume), tile.volumeText, shrinkZeros = false)
    }
}

/** One `High 2,816.45` row: a tertiary 8 sp label and a secondary 9 sp value on one baseline. */
@Composable
private fun TileDetailRow(label: String, value: String?, shrinkZeros: Boolean) {
    Row(
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(text = label, style = TGType.tileDetailLabel, maxLines = 1)
        Spacer(Modifier.width(TGDimens.TILE_DETAIL_GAP_DP.dp))
        Text(
            text = shrunkPrice(value, shrinkZeros),
            style = TGType.tileDetailValue,
            textAlign = TextAlign.End,
            modifier = Modifier.exactNumber(value),
        )
    }
}

private const val GLYPH_DP = 10
private const val GLYPH_GAP_DP = 4

/** Horizontal text inset of every tile size. */
internal const val TILE_PADDING_DP = 8

/** Top inset of the exchange caps line. */
internal const val TILE_TOP_DP = 4

/** Selection-mode outline. */
internal const val TILE_SELECTION_BORDER_DP = 2
