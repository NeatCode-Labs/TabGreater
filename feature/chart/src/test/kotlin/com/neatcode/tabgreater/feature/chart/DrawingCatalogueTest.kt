package com.neatcode.tabgreater.feature.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The drawing tools the sheet offers and the `tg.*` calls a command becomes. */
class DrawingCatalogueTest {

    @Test
    fun `the catalogue is exactly the 33 overlay templates`() {
        val builtIn = setOf(
            "segment", "rayLine", "straightLine", "horizontalStraightLine", "horizontalRayLine",
            "horizontalSegment", "verticalStraightLine", "verticalRayLine", "verticalSegment", "priceLine",
            "priceChannelLine", "parallelStraightLine", "fibonacciLine", "brush", "simpleAnnotation", "simpleTag",
        )
        val ported = setOf(
            "arrow", "circle", "rect", "triangle", "parallelogram", "fibonacciCircle", "fibonacciSegment",
            "fibonacciSpiral", "fibonacciSpeedResistanceFan", "fibonacciExtension", "gannBox", "threeWaves",
            "fiveWaves", "eightWaves", "anyWaves", "abcd", "xabcd",
        )
        assertEquals(16, builtIn.size)
        assertEquals(17, ported.size)
        assertEquals(builtIn + ported, DrawingCatalogue.names)
        assertEquals(33, DrawingCatalogue.tools.size)
    }

    @Test
    fun `the sheet offers the 30 tools of the toolbar`() {
        val offered = DrawingCatalogue.offered.map { it.name }
        assertEquals(30, offered.size)
        assertEquals(
            DrawingCatalogue.names - setOf("horizontalSegment", "verticalRayLine", "verticalSegment"),
            offered.toSet(),
        )
        assertEquals(
            listOf(
                "segment", "rayLine", "straightLine", "arrow", "horizontalStraightLine", "horizontalRayLine",
                "verticalStraightLine", "priceLine",
            ),
            DrawingCatalogue.inGroup(DrawingGroup.LINES).map { it.name },
        )
    }

    @Test
    fun `a restorable but unoffered built-in survives sanitising`() {
        val drawing = Drawing(name = "verticalSegment", points = listOf(DrawingPoint(1, 2.0), DrawingPoint(3, 4.0)))
        assertEquals(listOf(drawing), DrawingsCodec.sanitize(listOf(drawing)))
    }

    @Test
    fun `names are unique`() {
        val names = DrawingCatalogue.tools.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `every group has tools and tools appear in group order`() {
        DrawingGroup.entries.forEach { assertTrue("$it is empty", DrawingCatalogue.inGroup(it).isNotEmpty()) }
        assertEquals(DrawingCatalogue.offered, DrawingGroup.entries.flatMap { DrawingCatalogue.inGroup(it) })
    }

    @Test
    fun `only the two annotations require text`() {
        assertEquals(
            setOf("simpleAnnotation", "simpleTag"),
            DrawingCatalogue.tools.filter { it.requiresText }.map { it.name }.toSet(),
        )
        assertEquals(DrawingGroup.ANNOTATIONS, DrawingCatalogue.find("simpleTag")?.group)
        assertFalse(DrawingCatalogue.find("segment")!!.requiresText)
        assertNull(DrawingCatalogue.find("spaceship"))
        assertNull(DrawingCatalogue.find(null))
    }

    @Test
    fun `magnet modes carry the tg setMagnet values and default to weak`() {
        assertEquals(listOf("none", "weak_magnet", "strong_magnet"), MagnetMode.entries.map { it.jsValue })
        assertEquals(MagnetMode.STRONG, MagnetMode.fromNameOrDefault("STRONG"))
        assertEquals(MagnetMode.WEAK, MagnetMode.fromNameOrDefault("bogus"))
        assertEquals(MagnetMode.WEAK, MagnetMode.fromNameOrDefault(null))
    }

    @Test
    fun `commands become guarded tg calls with JSON-escaped arguments`() {
        assertEquals("window.tg&&tg.startDrawing(\"fibonacciLine\")", DrawingAction.StartTool("fibonacciLine").toJs())
        assertEquals("window.tg&&tg.cancelDrawing()", DrawingAction.Cancel.toJs())
        assertEquals("window.tg&&tg.removeSelectedDrawing()", DrawingAction.RemoveSelected.toJs())
        assertEquals("window.tg&&tg.toggleSelectedLock()", DrawingAction.ToggleSelectedLock.toJs())
        assertEquals("window.tg&&tg.deselectDrawing()", DrawingAction.Deselect.toJs())
        assertEquals("window.tg&&(tg.cancelDrawing(),tg.deselectDrawing())", DrawingAction.ClearFocus.toJs())
        assertEquals("window.tg&&tg.clearDrawings()", DrawingAction.ClearAll.toJs())
        assertEquals("window.tg&&tg.removeDrawing(\"o_3\")", DrawingAction.RemoveDrawing("o_3").toJs())
        assertEquals(
            "window.tg&&tg.setDrawingText(\"o_3\",\"it's \\\"up\\\"\\n\")",
            DrawingAction.SetText("o_3", "it's \"up\"\n").toJs(),
        )
    }
}
