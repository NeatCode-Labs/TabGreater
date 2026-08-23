package com.neatcode.tabgreater.widget

import com.neatcode.tabgreater.core.model.MarketKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Findings 13 / 21: `widget_configs` is the app's own record and it outlives the widgets whenever
 * `ACTION_APPWIDGET_DELETED` is not delivered (force-stopped package) or auto-backup restores ids
 * no host owns. An entry nobody can see must not keep `LiveTickerService` and its exchange sockets
 * alive.
 */
class WidgetReconcileTest {

    private val btc = WidgetConfig(MarketKey("kraken:BTC/EUR"))
    private val eth = WidgetConfig(MarketKey("binance:ETH/USDT"))

    @Test
    fun `configs whose widget is gone are dropped`() {
        val stored = mapOf(11 to btc, 22 to eth)

        val live = reconcileConfigs(stored, bound = setOf(11))

        assertEquals(mapOf(11 to btc), live)
    }

    @Test
    fun `an empty host enumeration means no widgets at all`() {
        val stored = mapOf(11 to btc, 22 to eth)

        assertTrue(reconcileConfigs(stored, bound = emptySet()).isEmpty())
    }

    @Test
    fun `a failed enumeration keeps every config instead of reporting zero widgets`() {
        val stored = mapOf(11 to btc, 22 to eth)

        assertSame(stored, reconcileConfigs(stored, bound = null))
    }

    @Test
    fun `ids the host knows but we have no config for are simply ignored`() {
        val stored = mapOf(11 to btc)

        assertEquals(stored, reconcileConfigs(stored, bound = setOf(11, 22, 33)))
    }

    @Test
    fun `the surviving keys are what the live service subscribes to`() {
        val stored = mapOf(11 to btc, 22 to eth, 33 to btc)

        val keys = reconcileConfigs(stored, bound = setOf(22, 33)).values.mapTo(LinkedHashSet()) { it.key }

        assertEquals(setOf(MarketKey("binance:ETH/USDT"), MarketKey("kraken:BTC/EUR")), keys)
    }
}
