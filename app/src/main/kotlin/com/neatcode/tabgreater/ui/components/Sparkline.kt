package com.neatcode.tabgreater.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neatcode.tabgreater.core.model.TGDimens

/**
 * The tile sparkline: a straight, unsmoothed polyline with round caps, min-max
 * scaled to the box, plus an area fill whose gradient is anchored to the **box** (colour at
 * 0.28 alpha on the top edge fading to 0 on the bottom edge), not to the line.
 *
 * Draws nothing for fewer than two points, so a tile whose history has not loaded yet simply
 * shows an empty box instead of a flat line.
 *
 * The defaults are the Small/Compact/Medium tile look; the Large tile passes a heavier
 * [strokeWidth] and a stronger [fillAlpha] so its full-width band reads as a real chart.
 *
 * @param fillAlpha alpha of the area gradient on the **top** edge of the box (it always fades to 0).
 * @param strokeWidth width of the polyline; it also insets the line vertically so the round caps
 *   stay inside the box.
 */
@Composable
fun Sparkline(
    values: FloatArray?,
    color: Color,
    modifier: Modifier = Modifier,
    fillAlpha: Float = TGDimens.SPARK_FILL_ALPHA,
    strokeWidth: Dp = TGDimens.SPARK_STROKE_DP.dp,
) {
    Canvas(modifier) {
        if (values == null || values.size < 2) return@Canvas

        var low = values[0]
        var high = values[0]
        for (value in values) {
            if (value < low) low = value
            if (value > high) high = value
        }
        val flat = high - low <= 0f
        val span = if (flat) 1f else high - low

        val stroke = strokeWidth.toPx()
        val usableHeight = (size.height - stroke).coerceAtLeast(0f)
        val stepX = size.width / (values.size - 1)

        val line = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            // A flat window (all closes equal) is drawn through the middle, not along the bottom edge.
            val unit = if (flat) 0.5f else (value - low) / span
            val y = stroke / 2f + (1f - unit) * usableHeight
            if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }

        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                0f to color.copy(alpha = fillAlpha),
                1f to color.copy(alpha = 0f),
            ),
        )
        drawPath(
            path = line,
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
