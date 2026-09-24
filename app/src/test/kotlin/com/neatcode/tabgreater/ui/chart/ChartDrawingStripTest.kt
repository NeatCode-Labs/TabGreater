package com.neatcode.tabgreater.ui.chart

import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.feature.chart.DrawingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the drawing strip goes: the bottom-left corner of the candle pane's plot, never over the
 * legend at the top of the pane, the price axis to the right or the pills over the canvas' bottom.
 */
class ChartDrawingStripTest {

    // Portrait 360 dp, emulator QA frame: a 303 dp plot, the candle pane ends 452 dp down a 540 dp canvas.
    private val selected = DrawingState(selectedId = "o1", selectedName = "segment", plotLeft = 0, plotBottom = 452, plotWidth = 303)

    @Test
    fun `the strip sits in the plot's bottom-left corner and ends before the price axis`() {
        val slot = stripSlot(selected, canvasHeightDp = 540, bottomReserveDp = 30)
        assertEquals(STRIP_MARGIN_DP, slot.startDp)
        assertEquals(452 - STRIP_MARGIN_DP - STRIP_HEIGHT_DP, slot.topDp)
        assertEquals(303 - 2 * STRIP_MARGIN_DP, slot.maxWidthDp)
        // Right edge of the widest strip stays inside the plot, left of the price labels.
        assertTrue(slot.startDp + slot.maxWidthDp!! <= selected.plotLeft + selected.plotWidth)
    }

    @Test
    fun `the strip stays well below the candle legend`() {
        val slot = stripSlot(selected, canvasHeightDp = 540, bottomReserveDp = 30)
        // The two-line legend (title + O/H/L/C) ends about 40 dp into the pane.
        assertTrue(slot.topDp > 60)
    }

    @Test
    fun `without an indicator pane the strip stays above the pills over the canvas bottom`() {
        // The candle pane reaches the time axis: 20 dp above the canvas' bottom edge.
        val noSubPane = selected.copy(plotBottom = 520)
        val slot = stripSlot(noSubPane, canvasHeightDp = 540, bottomReserveDp = 30)
        assertEquals(540 - 30 - STRIP_HEIGHT_DP, slot.topDp)
    }

    @Test
    fun `in fullscreen the reserve covers the floating toolbar as well`() {
        val landscape = selected.copy(plotWidth = 740, plotBottom = 300)
        val slot = stripSlot(landscape, canvasHeightDp = 320, bottomReserveDp = 70)
        assertEquals(320 - 70 - STRIP_HEIGHT_DP, slot.topDp)
    }

    @Test
    fun `a plot the page has not reported yet falls back to just under the legend`() {
        val slot = stripSlot(DrawingState(drawing = true, tool = "segment"), canvasHeightDp = 540, bottomReserveDp = 30)
        assertEquals(StripSlot(STRIP_MARGIN_DP, STRIP_FALLBACK_TOP_DP, null), slot)
        assertNull(slot.maxWidthDp)
    }

    @Test
    fun `a squeezed pane never pushes the strip above the canvas`() {
        val tiny = selected.copy(plotBottom = 20, plotWidth = 10)
        val slot = stripSlot(tiny, canvasHeightDp = 60, bottomReserveDp = 30)
        assertEquals(0, slot.topDp)
        assertEquals(STRIP_HEIGHT_DP, slot.maxWidthDp)
    }

    @Test
    fun `a candle pane too short for the strip sends it to the lowest pane instead of onto the legend`() {
        // Landscape fullscreen, 336 dp canvas, VOL + MACD + RSI: the candle pane ends 66 dp down and
        // its legend (title + O/H/L/C) at 39 dp. The bottom-left of the plot would be at 30 dp.
        val short = selected.copy(plotWidth = 740, plotBottom = 66, legendBottom = 39)
        val slot = stripSlot(short, canvasHeightDp = 336, bottomReserveDp = 70)
        assertEquals(336 - 70 - STRIP_HEIGHT_DP, slot.topDp)
        assertTrue(slot.topDp >= short.legendBottom + STRIP_LEGEND_GAP_DP)
    }

    @Test
    fun `with no room below the legend anywhere the strip sits right under it`() {
        // No indicator pane and a tiny window: even the canvas' lowest slot is on the legend.
        val tiny = selected.copy(plotBottom = 76, legendBottom = 39)
        val slot = stripSlot(tiny, canvasHeightDp = 100, bottomReserveDp = 30)
        assertEquals(39 + STRIP_LEGEND_GAP_DP, slot.topDp)
    }

    @Test
    fun `main-pane indicators push the legend down and the strip with it`() {
        // MA + BOLL rows under O/H/L/C: the legend ends 90 dp down a 120 dp candle pane.
        val tall = selected.copy(plotBottom = 120, legendBottom = 90)
        val slot = stripSlot(tall, canvasHeightDp = 540, bottomReserveDp = 30)
        assertEquals(540 - 30 - STRIP_HEIGHT_DP, slot.topDp)
    }

    @Test
    fun `a pane with room keeps the strip in its corner whatever the legend`() {
        val slot = stripSlot(selected.copy(legendBottom = 39), canvasHeightDp = 540, bottomReserveDp = 30)
        assertEquals(452 - STRIP_MARGIN_DP - STRIP_HEIGHT_DP, slot.topDp)
    }

    @Test
    fun `strip buttons have a full-size touch target and well-separated pills`() {
        assertTrue(STRIP_TOUCH_DP >= 48)
        assertEquals(STRIP_TOUCH_DP, STRIP_HEIGHT_DP + 2 * STRIP_TOUCH_OVERHANG_DP)
        // Buttons are laid out with no gap: the pills of lock and delete are 2 * overhang apart.
        assertTrue(2 * STRIP_TOUCH_OVERHANG_DP >= 16)
    }

    @Test
    fun `the text dialog is titled after its tool`() {
        assertEquals(R.string.chart_draw_tool_simple_tag, textDialogTitleRes("simpleTag"))
        assertEquals(R.string.chart_draw_tool_simple_annotation, textDialogTitleRes("simpleAnnotation"))
        assertEquals(R.string.chart_draw_text_title, textDialogTitleRes(null))
        assertEquals(R.string.chart_draw_text_title, textDialogTitleRes("spaceship"))
    }
}
