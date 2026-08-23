package com.neatcode.tabgreater.core.model

/**
 * The app's dark colour palette, the single source of truth for every surface, text and accent
 * colour. Stored as ARGB longs so both Compose (`Color(TGColors.BACKGROUND)`) and the Glance
 * widget / bitmaps can share them.
 */
object TGColors {
    /** Screen, status bar, app bar, tab row, chip row. */
    const val BACKGROUND = 0xFF141515L
    /** Ticker tile surface. */
    const val SURFACE = 0xFF202121L
    /** Bottom navigation bar. */
    const val NAV_SURFACE = 0xFF232526L
    /** Tile shadow halo and FAB content colour. */
    const val SCRIM = 0xFF0F1010L
    /** Chip background. */
    const val CHIP_FILL = 0xFF18191AL
    /** Chip border and tab-row divider. */
    const val OUTLINE = 0xFF374444L
    const val TEXT_PRIMARY = 0xFFFFFFFFL
    const val TEXT_SECONDARY = 0xFF9E9E9EL
    /** Exchange caps label + exchange glyph tint. */
    const val TEXT_TERTIARY = 0xFF737271L
    /** Brand accent — the green of the app logo (art/launcher-logo.jpg): tab indicator, FAB, switches. */
    const val ACCENT = 0xFF85D32DL
    /** Active bottom-nav pill: the accent at 26 % over [NAV_SURFACE]. */
    const val NAV_PILL = 0xFF3C5228L
    /** Positive % and sparkline. */
    const val UP = 0xFF60A86BL
    /** Negative % and sparkline. */
    const val DOWN = 0xFFF34142L

    // Chart screen
    const val CANDLE_UP = 0xFF6FA26FL
    const val CANDLE_DOWN = 0xFFD9655EL
    const val VOLUME_UP = 0xFF283D29L
    const val VOLUME_DOWN = 0xFF41211DL
    const val CHART_GRID = 0xFF2C2D2FL
    const val CHART_AXIS_TEXT = 0xFFB0B0B0L
    const val CHART_LEGEND = 0xFFA8ABB2L
    const val CHART_LAST_PRICE_TAG = 0xFF73A973L

    /**
     * Ticker stripe colours offered by the long-press "Colour" action: eight hues that stay
     * legible as a 3.5 dp stripe on [SURFACE], warm to cool, brand accent last.
     */
    val ACCENT_PALETTE: List<Long> = listOf(
        0xFFF95B3AL, // vermilion
        0xFFFF9E99L, // salmon
        0xFFFAA426L, // orange
        0xFFFCCD0BL, // yellow
        0xFF51C872L, // green
        0xFF5BB8F0L, // sky
        0xFFA58CF0L, // lavender
        0xFF85D32DL, // lime (brand accent)
    )
}

/** Geometry tokens (dp), reference frame 360 dp. */
object TGDimens {
    const val TILE_ASPECT_SMALL = 2.02f
    const val TILE_ASPECT_COMPACT = 1.43f
    const val TILE_ASPECT_MEDIUM = 4.3f
    const val TILE_ASPECT_LARGE = 1.87f
    const val TILE_CORNER_DP = 8

    /**
     * The home-screen widget is rounded far harder than a watchlist tile: a launcher frames its
     * widgets with a generous system radius (Android's own `system_app_widget_background_radius`
     * is 16 dp and One UI's is larger still), so an 8 dp card inside that frame reads as a sharp
     * rectangle sitting in a rounded hole.
     */
    const val WIDGET_CORNER_DP = 20
    const val TILE_ELEVATION_DP = 1
    const val GRID_MARGIN_DP = 6
    const val GRID_GAP_DP = 8
    const val SPARK_W_DP = 61
    const val SPARK_H_DP = 39
    /** The line reads as 1.8-2.3 dp once anti-aliased; 1.5 dp + AA is what produces that on screen (F1). */
    const val SPARK_STROKE_DP = 1.5f
    const val SPARK_FILL_ALPHA = 0.22f
    const val APP_BAR_DP = 48
    const val TAB_ROW_DP = 48
    const val CHIP_ROW_DP = 36
    const val CHIP_H_DP = 20
    const val FAB_W_DP = 112
    const val FAB_H_DP = 40
    const val NAV_BAR_DP = 56

    // ---- F3: Compact / Medium / Large tiles ------------------------------------------------
    // All of the following are design values in the 360 dp reference frame.

    /** Accent stripe on the left edge, identical in every tile size. */
    const val TILE_STRIPE_DP = 3.5f

    /** Gap between a micro-row label and its value. */
    const val TILE_DETAIL_GAP_DP = 3

    /** Vertical gap between the micro-rows on Compact and Medium (measured pitch ≈ 12 dp). */
    const val TILE_DETAIL_SPACING_DP = 1

    /** Large is airier: measured pitch ≈ 14.5 dp. */
    const val TILE_DETAIL_SPACING_LARGE_DP = 3.5f

    /** Bottom inset under the last micro-row (Compact / Medium / Large). */
    const val TILE_DETAIL_BOTTOM_DP = 2

    /**
     * Compact: top of the price line in the design. The tile does not lay the price
     * out from this offset — price, absolute change and micro-rows share one bottom-anchored
     * column so that they can never overlap — but at font scale 1.0 that column happens to put
     * the price line exactly here.
     */
    const val TILE_COMPACT_PRICE_TOP_DP = 43

    /** Compact: gap between the absolute-change line and the first micro-row. */
    const val TILE_COMPACT_PRICE_GAP_DP = 3

    /** Medium / Large: top inset of the right-hand price column (price ink starts at ≈ 10.6 dp). */
    const val TILE_PRICE_TOP_DP = 5

    /** Medium: fixed left column (exchange caps + pair), so the sparkline starts at x = 117 dp. */
    const val TILE_MEDIUM_LEFT_DP = 109

    /** Medium: fixed right column (price, absolute change, micro-rows). */
    const val TILE_MEDIUM_RIGHT_DP = 114

    /**
     * Medium: the centre sparkline is 52 dp high and vertically centred; the 108.8 dp
     * width holds at the **360 dp reference** only — the column takes whatever the two fixed columns
     * leave, so the price block stays flush with the tile's end inset on a wider screen.
     */
    const val SPARK_MEDIUM_W_DP = 109
    const val SPARK_MEDIUM_H_DP = 52

    /** Large: the sparkline band spans y 48 → 135 dp and bleeds off the right edge. */
    const val SPARK_LARGE_TOP_DP = 48
    const val SPARK_LARGE_H_DP = 87

    /** Large: the band starts just clear of the accent stripe instead of at x = 0. */
    const val SPARK_LARGE_START_DP = TILE_STRIPE_DP + 2f

    /** Large draws a heavier line and a much more visible area gradient than the smaller tiles. */
    const val SPARK_LARGE_STROKE_DP = 2f
    const val SPARK_LARGE_FILL_ALPHA = 0.35f
}
