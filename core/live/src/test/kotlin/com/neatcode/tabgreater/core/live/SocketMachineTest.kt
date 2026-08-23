package com.neatcode.tabgreater.core.live

import com.neatcode.tabgreater.core.model.MarketKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `LiveTickerService.runSocketMachine` in the small: the exact
 * `map(socketKeys).distinctUntilChanged()` pipeline that decides when the exchange sockets are
 * cancelled and re-opened.
 *
 * Every emission of that pipeline cancels the running `observeTickers` collection, which drops the
 * subscription refcount to zero and makes the next collector pay for a fresh TLS handshake (plus
 * KuCoin's `bullet-public` round trip). Before the fix, the whole session ran through one
 * `collectLatest`, so plugging in a charger re-handshaked every exchange for an identical key set.
 */
class SocketMachineTest {

    private val keys = setOf(MarketKey("kraken:BTC/EUR"), MarketKey("binance:ETH/USDT"))
    private val settings = LiveSettingsValues(widgetRefresh = WidgetRefresh.LIVE)

    private fun conditions(
        screenInteractive: Boolean = true,
        charging: Boolean = false,
        unmetered: Boolean = true,
        transport: Transport = Transport.WIFI,
        powerSave: Boolean = false,
        widgetRefresh: WidgetRefresh = settings.widgetRefresh,
    ) = LiveConditions(
        hasWidgets = true,
        screenInteractive = screenInteractive,
        transport = transport,
        charging = charging,
        dataSaver = false,
        powerSave = powerSave,
        unmetered = unmetered,
        widgetRefresh = widgetRefresh,
        wifiOnly = settings.wifiOnly,
    )

    /** The service's pipeline, verbatim. */
    private suspend fun socketRestarts(vararg conditions: LiveConditions): List<Set<MarketKey>> =
        flowOf(*conditions)
            .map { TickerModeCalculator.socketKeys(keys, it, settings) }
            .distinctUntilChanged()
            .toList()

    @Test
    fun `charger changes never restart the sockets`() = runTest {
        val restarts = socketRestarts(
            conditions(),
            conditions(charging = true),
            conditions(),
        )
        assertEquals(listOf(keys), restarts)
    }

    @Test
    fun `a screen off and on cycle releases the sockets once and re-opens them once`() = runTest {
        val restarts = socketRestarts(
            conditions(),
            conditions(screenInteractive = false),
            // Doze / charger noise while the screen is off must not churn the (already empty) set.
            conditions(screenInteractive = false, charging = true),
            conditions(),
        )
        assertEquals(listOf(keys, emptySet(), keys), restarts)
    }

    @Test
    fun `leaving a socket mode releases them and coming back re-opens them once`() = runTest {
        val restarts = socketRestarts(
            conditions(),
            // Wi-Fi drops to a metered cellular link: NEAR with wifiOnly on holds no socket.
            conditions(transport = Transport.CELLULAR, unmetered = false),
            conditions(transport = Transport.CELLULAR, unmetered = false, charging = true),
            conditions(),
        )
        assertEquals(listOf(keys, emptySet(), keys), restarts)
    }

    @Test
    fun `battery saver closes the sockets`() = runTest {
        val restarts = socketRestarts(conditions(), conditions(powerSave = true))
        assertEquals(listOf(keys, emptySet<MarketKey>()), restarts)
    }

    @Test
    fun `switching to a timed cadence closes the sockets`() = runTest {
        val restarts = socketRestarts(conditions(), conditions(widgetRefresh = WidgetRefresh.MIN_5))
        assertEquals(listOf(keys, emptySet<MarketKey>()), restarts)
    }
}
