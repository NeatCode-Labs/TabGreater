package com.neatcode.tabgreater.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The four glyphs this UI needs that `material-icons-core` does not ship
 * (`show_chart`, `grid_view`, `sort`, `playlist_add`), drawn from scratch on the standard
 * 24 dp / 24-unit Material grid so [androidx.compose.material3.Icon] can tint them like any
 * bundled icon. Adding `material-icons-extended` (~40 MB of vectors) is not worth four shapes.
 */
object TGIcons {

    /** Rising zigzag: sparkline period chip and the "Watchlists" bottom-nav item. */
    val ShowChart: ImageVector by lazy {
        strokeVector("tt_show_chart", width = 2f) {
            moveTo(2.5f, 17.5f)
            lineTo(8.5f, 11.5f)
            lineTo(12.5f, 15.5f)
            lineTo(21.5f, 6.5f)
        }
    }

    /** Four rounded blocks, left column taller: the tile-size chip ("Small"). */
    val GridSmall: ImageVector by lazy {
        ImageVector.Builder(
            name = "tt_grid_small",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            block(3f, 3f, 9f, 13f)
            block(3f, 15f, 9f, 21f)
            block(11f, 3f, 21f, 9f)
            block(11f, 11f, 21f, 21f)
        }.build()
    }

    /** Three left-aligned lines of decreasing length: the sort chip ("Custom"). */
    val SortLines: ImageVector by lazy {
        strokeVector("tt_sort", width = 2f) {
            moveTo(3.5f, 6.5f)
            lineTo(20.5f, 6.5f)
            moveTo(3.5f, 12f)
            lineTo(15.5f, 12f)
            moveTo(3.5f, 17.5f)
            lineTo(10.5f, 17.5f)
        }
    }

    /** Three lines plus a `+`: opens the Watchlist Manager from the tab row. */
    val PlaylistAdd: ImageVector by lazy {
        strokeVector("tt_playlist_add", width = 2f) {
            moveTo(3.5f, 6f)
            lineTo(19.5f, 6f)
            moveTo(3.5f, 11.5f)
            lineTo(19.5f, 11.5f)
            moveTo(3.5f, 17f)
            lineTo(12f, 17f)
            moveTo(17f, 14f)
            lineTo(17f, 21f)
            moveTo(13.5f, 17.5f)
            lineTo(20.5f, 17.5f)
        }
    }

    // ---- F3 glyphs ------------------------------------------------------------------------

    /** Four equal rounded blocks (2×2): "Compact" in the Tickers Appearance sheet / chip. */
    val GridCompact: ImageVector by lazy {
        blockVector("tt_grid_compact") {
            block(3f, 3f, 11f, 11f)
            block(13f, 3f, 21f, 11f)
            block(3f, 13f, 11f, 21f)
            block(13f, 13f, 21f, 21f)
        }
    }

    /** Two wide bars: "Medium". */
    val GridMedium: ImageVector by lazy {
        blockVector("tt_grid_medium") {
            block(3f, 4f, 21f, 11f)
            block(3f, 13f, 21f, 20f)
        }
    }

    /** One filled square: "Large". */
    val GridLarge: ImageVector by lazy {
        blockVector("tt_grid_large") {
            block(3f, 3f, 21f, 21f)
        }
    }

    /** Two short horizontal lines: the drag handle in the Watchlist Manager. */
    val DragHandle: ImageVector by lazy {
        strokeVector("tt_drag_handle", width = 2f) {
            moveTo(5f, 9.5f)
            lineTo(19f, 9.5f)
            moveTo(5f, 14.5f)
            lineTo(19f, 14.5f)
        }
    }

    /** Bar with an upward arrow: "Move to top" tile action. */
    val MoveToTop: ImageVector by lazy {
        strokeVector("tt_move_to_top", width = 2f) {
            moveTo(5f, 4f)
            lineTo(19f, 4f)
            moveTo(12f, 8f)
            lineTo(12f, 20f)
            moveTo(7f, 13f)
            lineTo(12f, 8f)
            lineTo(17f, 13f)
        }
    }

    /** Folder with a right arrow: "Move to watchlist" tile action. */
    val MoveToList: ImageVector by lazy {
        strokeVector("tt_move_to_list", width = 2f) {
            moveTo(3f, 6f)
            lineTo(3f, 18f)
            lineTo(21f, 18f)
            lineTo(21f, 8f)
            lineTo(12f, 8f)
            lineTo(10f, 6f)
            close()
            moveTo(9f, 13f)
            lineTo(15f, 13f)
            moveTo(13f, 10.5f)
            lineTo(15.5f, 13f)
            lineTo(13f, 15.5f)
        }
    }

    /** Painter's palette (outline with three dots): "Colour" tile action. */
    val Palette: ImageVector by lazy {
        ImageVector.Builder(
            name = "tt_palette",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 3f)
                curveTo(7f, 3f, 3f, 7f, 3f, 12f)
                curveTo(3f, 17f, 7f, 21f, 12f, 21f)
                curveTo(13.5f, 21f, 14.5f, 20f, 14.5f, 18.8f)
                curveTo(14.5f, 17.3f, 13.5f, 17f, 13.5f, 16f)
                curveTo(13.5f, 15f, 14.3f, 14.5f, 15.5f, 14.5f)
                lineTo(17.5f, 14.5f)
                curveTo(19.5f, 14.5f, 21f, 13f, 21f, 11f)
                curveTo(21f, 6.5f, 17f, 3f, 12f, 3f)
                close()
            }
            dot(7.5f, 11.5f)
            dot(10.5f, 7.5f)
            dot(15.5f, 8f)
        }.build()
    }

    /** Two overlapping sheets: "Copy" in the Watchlist Manager menu. */
    val ContentCopy: ImageVector by lazy {
        strokeVector("tt_content_copy", width = 2f) {
            moveTo(9f, 7f)
            lineTo(19f, 7f)
            lineTo(19f, 21f)
            lineTo(9f, 21f)
            close()
            moveTo(6f, 16f)
            lineTo(4f, 16f)
            lineTo(4f, 3f)
            lineTo(15f, 3f)
            lineTo(15f, 5f)
        }
    }

    /** Tray with an upward arrow: Settings → Export. */
    val Upload: ImageVector by lazy {
        strokeVector("tt_upload", width = 2f) {
            moveTo(4f, 16f)
            lineTo(4f, 20f)
            lineTo(20f, 20f)
            lineTo(20f, 16f)
            moveTo(12f, 15f)
            lineTo(12f, 4f)
            moveTo(7.5f, 8.5f)
            lineTo(12f, 4f)
            lineTo(16.5f, 8.5f)
        }
    }

    /** Tray with a downward arrow: Settings → Import. */
    val Download: ImageVector by lazy {
        strokeVector("tt_download", width = 2f) {
            moveTo(4f, 16f)
            lineTo(4f, 20f)
            lineTo(20f, 20f)
            lineTo(20f, 16f)
            moveTo(12f, 4f)
            lineTo(12f, 15f)
            moveTo(7.5f, 10.5f)
            lineTo(12f, 15f)
            lineTo(16.5f, 10.5f)
        }
    }

    // ---- F4 glyphs ------------------------------------------------------------------------

    /** Solid triangle pointing up: the direction caret next to the chart's last price. */
    val CaretUp: ImageVector by lazy {
        filledVector("tt_caret_up") {
            moveTo(12f, 7f)
            lineTo(19f, 17f)
            lineTo(5f, 17f)
            close()
        }
    }

    /** Solid triangle pointing down. */
    val CaretDown: ImageVector by lazy {
        filledVector("tt_caret_down") {
            moveTo(12f, 17f)
            lineTo(5f, 7f)
            lineTo(19f, 7f)
            close()
        }
    }

    /**
     * Five-pointed star, filled: the chart header's ★ when the market is in the watchlist.
     * Material's `Icons.Outlined.Star` is filled too, so the empty state needs its own glyph.
     */
    val Star: ImageVector by lazy { filledVector("tt_star") { starPath() } }

    /** The same star as an outline: the ☆ empty state. */
    val StarOutline: ImageVector by lazy {
        strokeVector("tt_star_outline", width = 1.8f) { starPath() }
    }

    /** Two candles with wicks: the chart-type button in the chart toolbar. */
    val CandleType: ImageVector by lazy {
        strokeVector("tt_candle_type", width = 2f) {
            moveTo(8f, 3f)
            lineTo(8f, 6f)
            moveTo(8f, 18f)
            lineTo(8f, 21f)
            moveTo(5.5f, 6f)
            lineTo(10.5f, 6f)
            lineTo(10.5f, 18f)
            lineTo(5.5f, 18f)
            close()
            moveTo(16f, 6f)
            lineTo(16f, 9f)
            moveTo(16f, 16f)
            lineTo(16f, 19f)
            moveTo(13.5f, 9f)
            lineTo(18.5f, 9f)
            lineTo(18.5f, 16f)
            lineTo(13.5f, 16f)
            close()
        }
    }

    /** A wave over a baseline: the indicators button in the chart toolbar. */
    val Indicators: ImageVector by lazy {
        strokeVector("tt_indicators", width = 2f) {
            moveTo(3f, 20f)
            lineTo(21f, 20f)
            moveTo(3.5f, 15f)
            curveTo(7f, 4f, 10f, 18f, 13.5f, 9f)
            curveTo(16f, 3f, 18.5f, 12f, 20.5f, 8f)
        }
    }

    /** Four outward corner brackets: enter fullscreen. */
    val Fullscreen: ImageVector by lazy {
        strokeVector("tt_fullscreen", width = 2f) {
            moveTo(9f, 4f)
            lineTo(4f, 4f)
            lineTo(4f, 9f)
            moveTo(15f, 4f)
            lineTo(20f, 4f)
            lineTo(20f, 9f)
            moveTo(20f, 15f)
            lineTo(20f, 20f)
            lineTo(15f, 20f)
            moveTo(9f, 20f)
            lineTo(4f, 20f)
            lineTo(4f, 15f)
        }
    }

    /** Four inward corner brackets: leave fullscreen. */
    val FullscreenExit: ImageVector by lazy {
        strokeVector("tt_fullscreen_exit", width = 2f) {
            moveTo(4f, 9f)
            lineTo(9f, 9f)
            lineTo(9f, 4f)
            moveTo(20f, 9f)
            lineTo(15f, 9f)
            lineTo(15f, 4f)
            moveTo(15f, 20f)
            lineTo(15f, 15f)
            lineTo(20f, 15f)
            moveTo(9f, 20f)
            lineTo(9f, 15f)
            lineTo(4f, 15f)
        }
    }

    /** Glyph for the tile-size chip and the Tickers Appearance sheet. */
    fun forTileSize(size: com.neatcode.tabgreater.core.model.TileSize): ImageVector = when (size) {
        com.neatcode.tabgreater.core.model.TileSize.SMALL -> GridSmall
        com.neatcode.tabgreater.core.model.TileSize.COMPACT -> GridCompact
        com.neatcode.tabgreater.core.model.TileSize.MEDIUM -> GridMedium
        com.neatcode.tabgreater.core.model.TileSize.LARGE -> GridLarge
    }

    /** The classic five-pointed star on the 24×24 grid, shared by [Star] and [StarOutline]. */
    private fun androidx.compose.ui.graphics.vector.PathBuilder.starPath() {
        moveTo(12f, 3.2f)
        lineTo(14.85f, 9.0f)
        lineTo(21.2f, 9.9f)
        lineTo(16.6f, 14.4f)
        lineTo(17.7f, 20.8f)
        lineTo(12f, 17.8f)
        lineTo(6.3f, 20.8f)
        lineTo(7.4f, 14.4f)
        lineTo(2.8f, 9.9f)
        lineTo(9.15f, 9.0f)
        close()
    }

    /** A single filled path on the 24×24 Material grid. */
    private fun filledVector(
        name: String,
        pathBuilder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White), pathBuilder = pathBuilder)
    }.build()

    private fun blockVector(name: String, blocks: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(blocks).build()

    /** A filled 1.25-unit-radius circle centred at (cx, cy). */
    private fun ImageVector.Builder.dot(cx: Float, cy: Float) {
        val r = 1.25f
        path(fill = SolidColor(Color.White)) {
            moveTo(cx - r, cy)
            arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
            arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
            close()
        }
    }

    /** A stroked single-path icon on the 24×24 Material grid. */
    private fun strokeVector(
        name: String,
        width: Float,
        pathBuilder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder,
        )
    }.build()

    /** A filled rounded rectangle from (l, t) to (r, b), corner radius 1.5 units. */
    private fun ImageVector.Builder.block(l: Float, t: Float, r: Float, b: Float) {
        val radius = 1.5f
        path(fill = SolidColor(Color.White)) {
            moveTo(l + radius, t)
            lineTo(r - radius, t)
            quadTo(r, t, r, t + radius)
            lineTo(r, b - radius)
            quadTo(r, b, r - radius, b)
            lineTo(l + radius, b)
            quadTo(l, b, l, b - radius)
            lineTo(l, t + radius)
            quadTo(l, t, l + radius, t)
            close()
        }
    }
}
