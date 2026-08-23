package com.neatcode.tabgreater.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.Tight

/**
 * The small badge next to the exchange name on a tile (10 dp in the Small layout): a two-letter
 * monogram in a hairline box, tinted like the surrounding text so it reads as part of the label.
 *
 * Deliberately not the exchanges' logos — those are their trademarks, and a public app has no
 * licence to them. The monogram is ours, scales with [size] and needs no drawable per exchange.
 */
@Composable
fun ExchangeGlyph(
    exchange: ExchangeId,
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = TG.TextTertiary,
) {
    Box(
        modifier = modifier
            .size(size)
            .border(BORDER, tint, RoundedCornerShape(size * CORNER_FRACTION)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = exchange.monogram,
            style = TextStyle(
                fontSize = (size.value * FONT_FRACTION).sp,
                lineHeight = (size.value * FONT_FRACTION).sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
                color = tint,
                platformStyle = Tight,
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}

private val BORDER = 0.75.dp
private const val CORNER_FRACTION = 0.2f
private const val FONT_FRACTION = 0.56f

@Preview(widthDp = 200, heightDp = 40, backgroundColor = 0xFF202121, showBackground = true)
@Composable
private fun ExchangeGlyphPreview() {
    Row(
        modifier = Modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExchangeId.entries.forEach { ExchangeGlyph(it, size = 16.dp) }
    }
}
