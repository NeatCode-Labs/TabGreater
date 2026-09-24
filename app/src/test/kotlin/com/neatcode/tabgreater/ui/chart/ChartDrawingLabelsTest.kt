package com.neatcode.tabgreater.ui.chart

import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.feature.chart.DrawingCatalogue
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every tool the "Draw" sheet lists has its own label, and nothing else does. */
class ChartDrawingLabelsTest {

    @Test
    fun `every catalogue tool has exactly one label`() {
        assertEquals(DrawingCatalogue.names, DrawingToolLabels.keys)
    }

    @Test
    fun `labels are distinct`() {
        assertEquals(DrawingToolLabels.size, DrawingToolLabels.values.toSet().size)
    }

    @Test
    fun `an unknown or missing template falls back to the generic label`() {
        assertEquals(R.string.chart_draw_tool_unknown, drawingToolLabelRes("spaceship"))
        assertEquals(R.string.chart_draw_tool_unknown, drawingToolLabelRes(null))
        assertEquals(R.string.chart_draw_tool_segment, drawingToolLabelRes("segment"))
    }
}
