package com.neatcode.tabgreater.ui.navigation

import com.neatcode.tabgreater.core.model.MarketKey
import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchActionTest {

    private val btc = MarketKey("binance:BTC/EUR")
    private val btcLink = "tabgreater://chart/binance%3ABTC%2FEUR"

    /** A stack that is not a lone chart: the grid, the settings, a chart reached from the grid. */
    private val onGrid: (MarketKey?) -> Boolean = { false }

    /** A stack that is exactly the chart of [key], as a widget tap leaves it. */
    private fun onlyChartOf(key: MarketKey): (MarketKey?) -> Boolean = { asked -> asked == null || asked == key }

    @Test
    fun `a widget link opens its chart over anything else`() {
        assertEquals(LaunchAction.OpenChart(btc), launchAction(btcLink, onGrid))
        assertEquals(LaunchAction.OpenChart(btc), launchAction(btcLink, onlyChartOf(MarketKey("kraken:ETH/EUR"))))
    }

    @Test
    fun `tapping the widget of the chart already showing changes nothing`() {
        assertEquals(LaunchAction.Stay, launchAction(btcLink, onlyChartOf(btc)))
    }

    @Test
    fun `the launcher icon over a widget-opened chart goes to the grid`() {
        assertEquals(LaunchAction.OpenGrid, launchAction(null, onlyChartOf(btc)))
    }

    @Test
    fun `the launcher icon over the app itself changes nothing`() {
        assertEquals(LaunchAction.Stay, launchAction(null, onGrid))
    }

    @Test
    fun `a chart link without a usable key is reported, never acted on`() {
        val bad = "tabgreater://chart/nasdaq%3ABTC%2FEUR"
        assertEquals(LaunchAction.BadLink(bad), launchAction(bad, onGrid))
        // Even over a lone chart it is not mistaken for the launcher icon.
        assertEquals(LaunchAction.BadLink(bad), launchAction(bad, onlyChartOf(btc)))
        assertEquals(LaunchAction.BadLink("tabgreater://chart/"), launchAction("tabgreater://chart/", onGrid))
    }

    @Test
    fun `data that is not a chart link counts as an ordinary launch`() {
        val other = "tabgreater://widget/config/7"
        assertEquals(LaunchAction.OpenGrid, launchAction(other, onlyChartOf(btc)))
        assertEquals(LaunchAction.Stay, launchAction(other, onGrid))
        // Another host is not a chart link either, so it is not reported as a bad one.
        assertEquals(LaunchAction.Stay, launchAction("tabgreater://charts/binance%3ABTC%2FEUR", onGrid))
    }
}
