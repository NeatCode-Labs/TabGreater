package com.neatcode.tabgreater.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import com.neatcode.tabgreater.core.model.TGDimens

/**
 * Draws the tile sparkline into a small bitmap, because Glance is RemoteViews and RemoteViews has
 * no `Canvas`.
 *
 * Size discipline matters more here than anywhere else in the app: Glance re-parcels the whole
 * `RemoteViews` tree — bitmaps included — on **every** update, and the platform rejects an update
 * whose bitmaps exceed 1.5 × display pixels × 4 bytes. Hence the [MAX_WIDTH_PX] × [MAX_HEIGHT_PX]
 * cap and `RGB_565` (half the bytes of `ARGB_8888`) whenever the widget background is opaque and
 * the alpha channel is not needed.
 *
 * The cap was raised with F5-3: the sparkline is now rendered at the launcher's real slot size
 * instead of being upscaled ~2× from a breakpoint. With `SizeMode.Exact` there is only ever one
 * tree, so exactly one bitmap is parcelled per update; the cap is generous enough for a 4 × 2 slot
 * on a 2.6-density phone (~420 × 260 px of `RGB_565`, ~215 KB) and [SparklineCache] keeps the same
 * pixels across the 2-second refresh cadence.
 */
internal object SparklineRenderer {

    const val MAX_WIDTH_PX = 640
    const val MAX_HEIGHT_PX = 320

    /**
     * @param backgroundArgb the widget background the sparkline sits on. Fully opaque backgrounds
     *   get an `RGB_565` bitmap with that colour baked in; a translucent background needs a
     *   translucent bitmap, so it falls back to `ARGB_8888`.
     * @return `null` when there is nothing to draw (fewer than two closes, or a zero-sized slot).
     */
    fun render(
        values: FloatArray,
        widthPx: Int,
        heightPx: Int,
        lineArgb: Int,
        backgroundArgb: Int,
        strokePx: Float,
        fillAlpha: Float = TGDimens.SPARK_FILL_ALPHA,
    ): Bitmap? {
        val width = widthPx.coerceIn(1, MAX_WIDTH_PX)
        val height = heightPx.coerceIn(1, MAX_HEIGHT_PX)
        if (values.size < 2 || widthPx < 2 || heightPx < 2) return null

        val points = SparklinePath.points(values, width.toFloat(), height.toFloat(), strokePx)
        if (points.isEmpty()) return null

        val opaque = Color.alpha(backgroundArgb) == 255
        val config = if (opaque) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        // ARGB_8888 bitmaps start fully transparent, which is exactly what a translucent
        // widget background needs; an opaque one gets that colour painted in first.
        val bitmap = createBitmap(width, height, config)
        val canvas = Canvas(bitmap)
        if (opaque) canvas.drawColor(backgroundArgb)

        val line = Path()
        line.moveTo(points[0], points[1])
        for (i in 2 until points.size step 2) line.lineTo(points[i], points[i + 1])

        val area = Path(line).apply {
            lineTo(width.toFloat(), height.toFloat())
            lineTo(0f, height.toFloat())
            close()
        }

        val red = Color.red(lineArgb)
        val green = Color.green(lineArgb)
        val blue = Color.blue(lineArgb)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            // Gradient anchored to the box, not to the line: colour at fillAlpha on the top edge,
            // fading to fully transparent on the bottom edge.
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                Color.argb((fillAlpha * 255f + 0.5f).toInt(), red, green, blue),
                Color.argb(0, red, green, blue),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(area, fillPaint)

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = lineArgb
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(line, linePaint)
        return bitmap
    }
}

/**
 * One rendered sparkline per widget, kept in memory.
 *
 * Glance re-runs the whole composition on every state write — up to once every two seconds while
 * `LiveTickerService` streams — and every run is a *fresh* composition, so `remember` never sees
 * the previous bitmap. Keying on the widget id (a widget only ever draws one sparkline) plus the
 * pixel size and the colours means a price tick reuses the raster, while a resize, a colour change
 * or a new candle window re-renders it.
 */
internal object SparklineCache {

    private data class Key(
        val widthPx: Int,
        val heightPx: Int,
        val lineArgb: Int,
        val backgroundArgb: Int,
        val strokePx: Float,
        val spark: Int,
    )

    private class Entry(val key: Key, val bitmap: Bitmap)

    private val entries = HashMap<Int, Entry>()

    @Synchronized
    fun bitmap(
        widgetId: Int,
        values: FloatArray,
        widthPx: Int,
        heightPx: Int,
        lineArgb: Int,
        backgroundArgb: Int,
        strokePx: Float,
    ): Bitmap? {
        val key = Key(widthPx, heightPx, lineArgb, backgroundArgb, strokePx, values.contentHashCode())
        entries[widgetId]?.let { if (it.key == key) return it.bitmap }
        val bitmap = SparklineRenderer.render(
            values = values,
            widthPx = widthPx,
            heightPx = heightPx,
            lineArgb = lineArgb,
            backgroundArgb = backgroundArgb,
            strokePx = strokePx,
        ) ?: return null
        entries[widgetId] = Entry(key, bitmap)
        return bitmap
    }

    /** Called when the host drops a widget, so a deleted instance does not pin its raster. */
    @Synchronized
    fun forget(widgetId: Int) {
        entries.remove(widgetId)
    }
}
