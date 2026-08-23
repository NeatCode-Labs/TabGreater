package com.neatcode.tabgreater.feature.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicatorCatalogueTest {

    /** Exactly the 11 built-ins the app exposes, with the defaults KLineChart 10.0.2 ships. */
    private val expected = listOf(
        Triple("MA", listOf(5, 10, 30, 60), IndicatorPane.MAIN),
        Triple("EMA", listOf(6, 12, 20), IndicatorPane.MAIN),
        Triple("BOLL", listOf(20, 2), IndicatorPane.MAIN),
        Triple("SAR", listOf(2, 2, 20), IndicatorPane.MAIN),
        Triple("VOL", listOf(5, 10, 20), IndicatorPane.SUB),
        Triple("MACD", listOf(12, 26, 9), IndicatorPane.SUB),
        Triple("RSI", listOf(6, 12, 24), IndicatorPane.SUB),
        Triple("KDJ", listOf(9, 3, 3), IndicatorPane.SUB),
        Triple("CCI", listOf(20), IndicatorPane.SUB),
        Triple("DMI", listOf(14, 6), IndicatorPane.SUB),
        Triple("OBV", listOf(30), IndicatorPane.SUB),
    )

    @Test
    fun `the catalogue has exactly eleven entries in sheet order`() {
        assertEquals(11, IndicatorCatalogue.entries.size)
        assertEquals(expected.map { it.first }, IndicatorCatalogue.entries.map { it.name })
    }

    @Test
    fun `every entry carries its klinecharts default params and pane`() {
        expected.forEach { (name, params, pane) ->
            val spec = IndicatorCatalogue.find(name)!!
            assertEquals(name, params, spec.calcParams)
            assertEquals(name, pane, spec.pane)
        }
    }

    @Test
    fun `indicators KLineChart 10_0_2 does not have are not offered`() {
        // ATR does not exist in 10.0.2 and SMA is a weighted average, not a simple one.
        listOf("ATR", "SMA", "HEIKIN_ASHI", "BBI", "AVP").forEach { assertNull(it, IndicatorCatalogue.find(it)) }
    }

    @Test
    fun `panes map to the strings chart_js switches on`() {
        assertEquals("main", IndicatorPane.MAIN.jsValue)
        assertEquals("sub", IndicatorPane.SUB.jsValue)
    }

    @Test
    fun `volume is the only indicator on by default`() {
        assertEquals(listOf("VOL"), IndicatorCatalogue.defaults.map { it.name })
        assertEquals(IndicatorCatalogue.find("VOL"), IndicatorCatalogue.defaults.single())
    }

    @Test
    fun `sanitize keeps catalogue order and drops what is not in it`() {
        val stored = listOf(
            IndicatorSpec("OBV"),
            IndicatorSpec("ATR", listOf(14)),
            IndicatorSpec("MA"),
        )
        val sanitized = IndicatorCatalogue.sanitize(stored)
        assertEquals(listOf("MA", "OBV"), sanitized.map { it.name })
        assertTrue(sanitized.all { it == IndicatorCatalogue.find(it.name) })
    }
}
