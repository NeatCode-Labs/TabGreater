package com.neatcode.tabgreater.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * How one ticker widget is laid out at the size the launcher actually gave it.
 *
 * **One composition at every size.** The widget used to switch between three trees — a row, a card
 * and a side-by-side wide shape — each with its own font ladder, so the same widget looked like a
 * different product on a Pixel's four-column grid, on One UI's five-column one and again after a
 * resize. It now draws the same three bands everywhere:
 *
 * ```
 * [BN] BTC/USDT                 <- identity: the exchange badge and the pair
 * [ sparkline, full width ]     <- takes every dp the two text lines leave
 * 77,362.58            +0.02%   <- price, with the 24 h change at the far end
 * ```
 *
 * and everything in it — every font size, the badge and every gap — is one reference design
 * multiplied by a single [WidgetPlan.scale]. Two widgets of different sizes are therefore
 * *proportionally* the same picture; growing the slot grows the type until it reaches [SCALE_MAX]
 * and then only the chart keeps growing, which is what a bigger chart widget should do.
 *
 * The scale is the smaller of what the height affords and what the width affords, so text is
 * never clipped and the pair is never ellipsised — the bug the owner photographed on One UI, where
 * `BINANCE BTC/USDT +0.02%` needed a hair more than the slot and `BTC/US…` was the result. Two
 * changes bought that back: the percentage moved from the header to the price line (two items per
 * line, not three), and the exchange's spelled-out name became the [ExchangeBadgeRenderer]
 * monogram the tiles already use — `BINANCE` cost ~39 dp of header, the square costs
 * [REF_BADGE_DP].
 */
internal data class WidgetPlan(
    /** The reference design multiplied into every size below; 1.0 is the design as drawn. */
    val scale: Float,
    /** Side of the square exchange badge. */
    val badgeDp: Float,
    val pairSp: Float,
    val priceSp: Float,
    val changeSp: Float,
    val metaSp: Float,
    /** Gap between the badge and the pair. */
    val headerGapDp: Float,
    /** Minimum gap between the price and the 24 h change; the change is pushed to the far end. */
    val priceGapDp: Float,
    /** Air above and below the sparkline band. */
    val bandGapDp: Float,
    /** `false` when even the smallest scale cannot fit the badge beside the pair. */
    val showBadge: Boolean,
    /** The "Updated hh:mm:ss" line and the "↻" target, which only tall widgets have room for. */
    val showMeta: Boolean,
    /** What the sparkline gets; `0` when the widget is too short to carry one. */
    val bandHeightDp: Float,
) {
    val hasBand: Boolean get() = bandHeightDp >= MIN_SPARK_DP
}

// ---- The reference design (scale = 1) ---------------------------------------------------------

/**
 * Side of the exchange badge. A tile draws the same monogram at 10 dp beside a smaller label; on
 * the widget the badge carries the exchange on its own, so it sits a little above the pair's
 * cap height rather than below it.
 */
internal const val REF_BADGE_DP = 12f

internal const val REF_PAIR_SP = 13f
internal const val REF_PRICE_SP = 18f
/**
 * The 24 h change reads as the second headline, not as a footnote: at 11 sp it was barely legible
 * beside an 18 sp price on a real home screen, so it takes the pair's size.
 */
internal const val REF_CHANGE_SP = 13f
internal const val REF_META_SP = 8f

/** Gap between the badge and the pair on the header line. */
internal const val REF_HEADER_GAP_DP = 5f

/** Gap between the price and the 24 h percentage. */
internal const val REF_PRICE_GAP_DP = 6f

/** Air above and below the sparkline band. */
internal const val REF_BAND_GAP_DP = 3f

/**
 * How far the type may be scaled.
 *
 * The floor only ever binds in a slot narrower than anything a real launcher grid offers, where a
 * small but complete widget beats a clipped one. The ceiling stops a 4 × 2 from turning into a
 * poster: past it every extra dp goes to the chart.
 */
internal const val SCALE_MIN = 0.55f
internal const val SCALE_MAX = 1.35f

/**
 * The share of the inner height the two (or three) text lines and their gaps may take when there
 * is a sparkline to draw. The remaining ~40 % is the chart, which is the proportion the owner
 * signed off on for a 2 × 1 — and because it is a *fraction*, every other size keeps it.
 */
internal const val TEXT_MAX_FRACTION = 0.60f

/** Without a chart the text simply centres in the widget, so it may use nearly all of it. */
private const val TEXT_ONLY_FRACTION = 0.95f

/** Below this the badge is dropped rather than shrunk into a smudge. */
private const val BADGE_DROP_SCALE = 0.85f

/** From this inner height up there is room for the "Updated" line and the "↻" target. */
internal const val META_MIN_INNER_HEIGHT_DP = 120f

/** Nothing shorter than this is worth rasterising as a sparkline. */
internal const val MIN_SPARK_DP = 14f

/** Horizontal room the floating "↻" target needs at the end of the header line. */
internal const val REFRESH_RESERVE_DP = 18f

/** Rough line box of a Glance `Text`: Roboto's ascent + descent plus the font padding it keeps. */
internal const val LINE_FACTOR = 1.35f

/**
 * The user's font scale is honoured — a widget is not exempt from accessibility — so it is folded
 * into the fit instead: a larger scale simply makes [widgetPlan] pick a smaller design scale. The
 * badge is a bitmap and is not affected by it, only the text is. The clamp guards the arithmetic
 * against a hostile value.
 */
private const val FONT_SCALE_MIN = 0.8f
private const val FONT_SCALE_MAX = 1.6f

// ---- Padding ----------------------------------------------------------------------------------

/**
 * Breathing room between the widget background and its content. It grows with the widget so a
 * 4 × 2 is not framed like a 2 × 1: the old flat 4 dp put a five-digit price a hair from the
 * rounded corner on One UI.
 */
internal fun verticalPaddingDp(slotHeightDp: Float): Float = (slotHeightDp * 0.075f).coerceIn(6f, 12f)

/** The sides get a little more than the top and bottom — text reads as tighter against a corner. */
internal fun horizontalPaddingDp(slotHeightDp: Float): Float = verticalPaddingDp(slotHeightDp) + 2f

/**
 * The slot Glance reports, or a 2 × 1-ish stand-in when the host has not measured the widget yet
 * (`SizeMode.Exact` hands out `DpSize.Zero` until the first `OPTION_APPWIDGET_SIZES` arrives on
 * some hosts). A freshly dropped widget is a 2 × 1, so that is the right guess.
 */
internal val FALLBACK_SLOT: DpSize = DpSize(160.dp, 92.dp)

internal fun slotOf(size: DpSize): DpSize =
    if (size.width.value < 1f || size.height.value < 1f) FALLBACK_SLOT else size

// ---- Hosts that draw smaller than they measure ---------------------------------------------------

/**
 * One UI's key for the fraction of the reported slot it actually draws the widget in.
 *
 * Measured on a One UI 8.5 S23 Ultra (5 x 6 grid, 384 dp screen, density 2.8125): a 2 x 1 slot is
 * reported as `150.0 x 91.7 dp` and drawn as `124.8 x 76.4 dp` — 0.833 of it in both dimensions,
 * which is exactly the `hsResizeRatio=0.8333333` the launcher puts in the widget's options bundle.
 * The same bundle reports a five-column widget as 406 dp wide on a 384 dp screen, so the inflation
 * is not a rounding artefact: One UI measures in cells and keeps a fifth of each for its own frame.
 *
 * Nothing else is affected — no other launcher writes this key, and without it the factor is 1.
 * Left uncorrected it is the widget's flexible parts that pay: the sparkline, the only weighted
 * child, loses every dp of the difference, and the gap that pushes the 24 h change to the right
 * edge collapses.
 */
internal const val OPTION_HOST_DRAW_RATIO = "hsResizeRatio"

/** A host may keep a frame for itself, but a widget drawn at half its slot is a bad reading. */
private const val HOST_DRAW_RATIO_MIN = 0.6f

/**
 * The fraction of the reported slot the host really draws in, from [OPTION_HOST_DRAW_RATIO].
 * Anything missing, out of range or not a number means "the host draws what it measures".
 */
internal fun hostDrawRatio(raw: Any?): Float {
    val value = (raw as? Number)?.toFloat() ?: return 1f
    if (!value.isFinite() || value > 1f || value < HOST_DRAW_RATIO_MIN) return 1f
    return value
}

/** The reported slot scaled down to the box the host will actually hand the widget. */
internal fun drawnSlotOf(size: DpSize, hostRatio: Float): DpSize {
    val slot = slotOf(size)
    if (hostRatio >= 1f) return slot
    return DpSize(slot.width * hostRatio, slot.height * hostRatio)
}

// ---- Text budgeting -----------------------------------------------------------------------------

/**
 * Advance of one character of the widget's default sans-serif face, in em.
 *
 * Glance is RemoteViews and RemoteViews cannot measure text, so this estimate is the only thing
 * standing between a long pair and the `BTC/US…` the owner photographed. The classes below are
 * Roboto Regular's own advances, rounded **up** for the letters (0.70 em is its widest common
 * capital rather than its average) so the estimate errs towards a slightly smaller widget instead
 * of towards a clipped one, and its own exact advances for digits and separators — charging a
 * comma the flat letter rate over-estimated `76,457.59` by 40 % and cost the price two sizes.
 */
internal const val EM_PER_CHAR = 0.62f

private const val EM_DIGIT = 0.56f
private const val EM_NARROW = 0.26f
private const val EM_SIGN = 0.42f
private const val EM_PERCENT = 0.90f
private const val EM_UPPER = 0.70f
private const val EM_LOWER = 0.56f
private const val EM_SLASH = 0.46f

/** `0.0₄123`: [WidgetModelFactory.shrink] writes the zero count as Unicode subscript digits. */
private const val EM_SUBSCRIPT = 0.36f

private fun emOf(c: Char): Float = when {
    c in '0'..'9' -> EM_DIGIT
    c == '.' || c == ',' || c == ':' || c == ' ' -> EM_NARROW
    c == '+' || c == '-' || c == '−' -> EM_SIGN
    c == '%' -> EM_PERCENT
    c == '/' -> EM_SLASH
    c in '₀'..'₉' -> EM_SUBSCRIPT
    c in 'A'..'Z' -> EM_UPPER
    c in 'a'..'z' -> EM_LOWER
    else -> EM_PER_CHAR
}

/** dp == sp at font scale 1.0; [fontScale] is what a larger system font costs in real dp. */
internal fun textWidthDp(text: String, fontSp: Float, fontScale: Float = 1f): Float {
    var em = 0f
    for (c in text) em += emOf(c)
    return em * fontSp * fontScale
}

/**
 * Width of `[BN] PAIR` at the reference scale. The badge is a bitmap, so unlike the pair it does
 * not grow with the system font scale.
 */
internal fun headerRefWidthDp(hasBadge: Boolean, pair: String, fontScale: Float): Float =
    (if (hasBadge) REF_BADGE_DP + REF_HEADER_GAP_DP else 0f) +
        textWidthDp(pair, REF_PAIR_SP, fontScale)

/** Width of `price … ±%` at the reference scale. */
internal fun priceRefWidthDp(price: String, change: String, fontScale: Float): Float =
    textWidthDp(price, REF_PRICE_SP, fontScale) + REF_PRICE_GAP_DP +
        textWidthDp(change, REF_CHANGE_SP, fontScale)

// ---- The plan -----------------------------------------------------------------------------------

/**
 * The one layout decision the widget makes: how far to scale the reference design so that the
 * header, the price line and — when the user asked for one — a sparkline band all fit the slot.
 *
 * @param hasBadge the widget's key still names a known exchange, so there is a monogram to draw.
 * @param hasSparkline the user's switch plus a candle cache with something in it. When it is off
 *   the text simply centres and may use the whole widget.
 */
internal fun widgetPlan(
    innerWidthDp: Float,
    innerHeightDp: Float,
    pair: String,
    price: String,
    change: String,
    hasBadge: Boolean,
    hasSparkline: Boolean,
    fontScale: Float = 1f,
): WidgetPlan {
    val fs = fontScale.coerceIn(FONT_SCALE_MIN, FONT_SCALE_MAX)
    val showMeta = innerHeightDp >= META_MIN_INNER_HEIGHT_DP
    val width = innerWidthDp.coerceAtLeast(1f)
    val height = innerHeightDp.coerceAtLeast(1f)

    // A widget too short to carry both text lines and a usable band drops the band instead of
    // squeezing the price into nothing; the text then centres in what there is.
    var band = hasSparkline
    var plan = solve(width, height, pair, price, change, hasBadge, band, showMeta, fs)
    if (band && plan.bandHeightDp < MIN_SPARK_DP) {
        band = false
        plan = solve(width, height, pair, price, change, hasBadge, false, showMeta, fs)
    }
    return plan
}

private fun solve(
    width: Float,
    height: Float,
    pair: String,
    price: String,
    change: String,
    hasBadge: Boolean,
    band: Boolean,
    showMeta: Boolean,
    fs: Float,
): WidgetPlan {
    // Height: the text lines and their gaps together may take a fixed share of the widget, so the
    // chart keeps the same share of every size.
    val lineBoxRef = (REF_PAIR_SP + REF_PRICE_SP + if (showMeta) REF_META_SP else 0f) * LINE_FACTOR * fs
    val gapsRef = if (band) 2 * REF_BAND_GAP_DP else 0f
    val heightBudget = height * (if (band) TEXT_MAX_FRACTION else TEXT_ONLY_FRACTION)
    val scaleH = heightBudget / (lineBoxRef + gapsRef)

    // Width: whichever of the two lines is the wider one decides.
    val scalePrice = width / priceRefWidthDp(price, change, fs).coerceAtLeast(0.01f)
    val scaleHeaderWith = width / headerRefWidthDp(true, pair, fs).coerceAtLeast(0.01f)
    val scaleHeaderWithout = width / headerRefWidthDp(false, pair, fs).coerceAtLeast(0.01f)

    var showBadge = hasBadge
    var scale = minOf(scaleH, scalePrice, if (showBadge) scaleHeaderWith else scaleHeaderWithout)
    if (showBadge && scale < BADGE_DROP_SCALE) {
        val without = minOf(scaleH, scalePrice, scaleHeaderWithout)
        if (without > scale) {
            showBadge = false
            scale = without
        }
    }
    scale = scale.coerceIn(SCALE_MIN, SCALE_MAX)

    val pairSp = REF_PAIR_SP * scale
    val priceSp = REF_PRICE_SP * scale
    val metaSp = REF_META_SP * scale
    val bandGap = REF_BAND_GAP_DP * scale
    val textHeight = (pairSp + priceSp + if (showMeta) metaSp else 0f) * LINE_FACTOR * fs
    val bandHeight = if (band) (height - textHeight - 2 * bandGap).coerceAtLeast(0f) else 0f

    return WidgetPlan(
        scale = scale,
        badgeDp = REF_BADGE_DP * scale,
        pairSp = pairSp,
        priceSp = priceSp,
        changeSp = REF_CHANGE_SP * scale,
        metaSp = metaSp,
        headerGapDp = REF_HEADER_GAP_DP * scale,
        priceGapDp = REF_PRICE_GAP_DP * scale,
        bandGapDp = bandGap,
        showBadge = showBadge,
        showMeta = showMeta,
        bandHeightDp = bandHeight,
    )
}

/**
 * Sparkline stroke in **pixels**, not dp: the bitmap is rasterised 1 : 1 at the slot's pixel size,
 * so a fixed dp stroke turned into a fat 4 px line on a 2.6 density phone. Two pixels on a dense
 * screen and one and a half elsewhere is what the tile sparkline reads like at arm's length.
 */
internal fun sparkStrokePx(density: Float): Float = if (density >= 2.5f) 2f else 1.5f

/** The badge's hairline, in pixels; never thinner than one so it does not vanish on a mdpi host. */
internal fun badgeStrokePx(density: Float): Float =
    (ExchangeBadgeRenderer.BORDER_DP * density).coerceAtLeast(1f)
