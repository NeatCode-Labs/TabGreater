package com.neatcode.tabgreater.widget

/**
 * The point maths of the widget sparkline, identical to the in-app tile sparkline
 * (`ui/components/Sparkline.kt`): min–max scaled over the whole window, straight unsmoothed
 * segments, the polyline inset by half the stroke so the round caps stay inside the box, and a
 * flat window drawn through the middle instead of along the bottom edge.
 *
 * Kept pure (no `android.graphics`) so it is unit tested on the JVM; [SparklineRenderer] only
 * turns these points into a bitmap.
 */
internal object SparklinePath {

    /**
     * @return `x0, y0, x1, y1, …` in pixels, or an empty array for fewer than two values.
     */
    fun points(values: FloatArray, width: Float, height: Float, stroke: Float): FloatArray {
        if (values.size < 2 || width <= 0f || height <= 0f) return FloatArray(0)

        var low = values[0]
        var high = values[0]
        for (value in values) {
            if (value < low) low = value
            if (value > high) high = value
        }
        val flat = high - low <= 0f
        val span = if (flat) 1f else high - low

        val usableHeight = (height - stroke).coerceAtLeast(0f)
        val stepX = width / (values.size - 1)

        val out = FloatArray(values.size * 2)
        for (index in values.indices) {
            val unit = if (flat) 0.5f else (values[index] - low) / span
            out[index * 2] = index * stepX
            out[index * 2 + 1] = stroke / 2f + (1f - unit) * usableHeight
        }
        return out
    }
}
