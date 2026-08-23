package com.neatcode.tabgreater.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.createBitmap

/**
 * The exchange badge the widget draws instead of the exchange's name: the same two-letter monogram
 * in a hairline box the watchlist tiles and the chart header use (`ExchangeGlyph` in `:app`), only
 * rasterised — Glance is RemoteViews and RemoteViews has neither a `Canvas` nor a border modifier.
 *
 * It replaces `BINANCE`, which cost ~39 dp of a header line; the square costs
 * [REF_BADGE_DP]. Everything that buys goes to the pair.
 *
 * Not the exchanges' logos: those are their trademarks and a public app has no licence to them.
 */
internal object ExchangeBadgeRenderer {

    /** Fractions of the badge's side, shared with the configuration sheet's preview. */
    const val CORNER_FRACTION = 0.2f
    const val FONT_FRACTION = 0.56f

    /** The tiles' hairline, in dp. */
    const val BORDER_DP = 0.75f

    /** A badge is tiny; anything past this is a bug in the caller, not a design. */
    private const val MAX_SIDE_PX = 96

    /**
     * @param argb the tint of both the frame and the letters — the widget's tertiary text colour,
     *   so the badge reads as part of the label rather than as a button.
     * @return `null` when the side rounds down to nothing.
     */
    fun render(monogram: String, sidePx: Int, argb: Int, strokePx: Float): Bitmap? {
        val side = sidePx.coerceAtMost(MAX_SIDE_PX)
        if (side < 4 || monogram.isEmpty()) return null

        // Transparent: the widget background behind it may be any colour, or translucent.
        val bitmap = createBitmap(side, side)
        val canvas = Canvas(bitmap)

        val stroke = strokePx.coerceIn(1f, side / 8f)
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = argb
            strokeWidth = stroke
        }
        val inset = stroke / 2f
        val radius = side * CORNER_FRACTION
        canvas.drawRoundRect(
            RectF(inset, inset, side - inset, side - inset),
            radius,
            radius,
            framePaint,
        )

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = argb
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            textSize = side * FONT_FRACTION
        }
        // Fit the widest monogram inside the frame whatever the system font is.
        val room = side - 2 * stroke - side * 0.12f
        val width = textPaint.measureText(monogram)
        if (width > room) textPaint.textSize *= room / width
        val metrics = textPaint.fontMetrics
        val baseline = side / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(monogram, side / 2f, baseline, textPaint)
        return bitmap
    }
}

/**
 * Rendered badges, keyed by what they look like rather than by widget: five exchanges over a
 * handful of sizes, shared by every widget on the home screen. Glance re-runs the whole
 * composition on every price tick, so without this the badge would be re-rasterised twice a second.
 */
internal object ExchangeBadgeCache {

    private data class Key(val monogram: String, val sidePx: Int, val argb: Int, val strokePx: Float)

    /** Five exchanges x a few sizes; the cap only guards against an unbounded resize dance. */
    private const val MAX_ENTRIES = 24

    private val entries = LinkedHashMap<Key, Bitmap>()

    @Synchronized
    fun bitmap(monogram: String, sidePx: Int, argb: Int, strokePx: Float): Bitmap? {
        val key = Key(monogram, sidePx, argb, strokePx)
        entries[key]?.let { return it }
        val bitmap = ExchangeBadgeRenderer.render(monogram, sidePx, argb, strokePx) ?: return null
        if (entries.size >= MAX_ENTRIES) {
            entries.keys.firstOrNull()?.let { entries.remove(it) }
        }
        entries[key] = bitmap
        return bitmap
    }
}
