package com.neatcode.tabgreater.core.live

import com.neatcode.tabgreater.core.model.MarketKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The full truth table of [TickerModeCalculator.mode] plus the cadence each mode selects.
 *
 * The combinatorial test walks every refresh × screen × transport × metering × charging × saver ×
 * wifiOnly combination and asserts against an independently written expectation, so a rule change
 * has to be made twice before it can slip through.
 */
class TickerModeCalculatorTest {

    private fun conditions(
        hasWidgets: Boolean = true,
        screenInteractive: Boolean = true,
        transport: Transport = Transport.WIFI,
        charging: Boolean = false,
        dataSaver: Boolean = false,
        powerSave: Boolean = false,
        // A plain Wi-Fi or Ethernet link reports NET_CAPABILITY_NOT_METERED; a hotspot does not,
        // which is exactly the case `unmetered` exists to separate from the transport.
        unmetered: Boolean = transport == Transport.WIFI || transport == Transport.ETHERNET,
        widgetRefresh: WidgetRefresh = WidgetRefresh.LIVE,
        wifiOnly: Boolean = DEFAULT_WIFI_ONLY,
    ) = LiveConditions(
        hasWidgets = hasWidgets,
        screenInteractive = screenInteractive,
        transport = transport,
        charging = charging,
        dataSaver = dataSaver,
        powerSave = powerSave,
        unmetered = unmetered,
        widgetRefresh = widgetRefresh,
        wifiOnly = wifiOnly,
    )

    @Test
    fun `no widgets stops the service`() {
        assertNull(TickerModeCalculator.mode(conditions(hasWidgets = false)))
        assertNull(TickerModeCalculator.mode(conditions(hasWidgets = false, widgetRefresh = WidgetRefresh.MIN_5)))
    }

    @Test
    fun `the default settings put a placed widget in TICK`() {
        assertEquals(
            TickerMode.TICK,
            TickerModeCalculator.mode(conditions(widgetRefresh = DEFAULT_WIDGET_REFRESH)),
        )
    }

    @Test
    fun `a timed cadence is TICK whatever the screen, link and savers are doing`() {
        for (refresh in WidgetRefresh.entries - WidgetRefresh.LIVE) {
            for (screen in BOOLS) {
                assertEquals(
                    "$refresh screen=$screen",
                    TickerMode.TICK,
                    TickerModeCalculator.mode(
                        conditions(
                            widgetRefresh = refresh,
                            screenInteractive = screen,
                            transport = Transport.CELLULAR,
                            dataSaver = true,
                            powerSave = true,
                        ),
                    ),
                )
            }
        }
    }

    @Test
    fun `live with the screen on and an unmetered link is LIVE`() {
        assertEquals(TickerMode.LIVE, TickerModeCalculator.mode(conditions()))
        assertEquals(TickerMode.LIVE, TickerModeCalculator.mode(conditions(transport = Transport.ETHERNET)))
    }

    @Test
    fun `live on a metered link is NEAR while the user asked for wifi only`() {
        assertEquals(
            TickerMode.NEAR,
            TickerModeCalculator.mode(conditions(transport = Transport.CELLULAR, wifiOnly = true)),
        )
        // A hotspot or a Wi-Fi the user marked "Metered" is exactly the same case.
        assertEquals(
            TickerMode.NEAR,
            TickerModeCalculator.mode(conditions(transport = Transport.WIFI, unmetered = false)),
        )
    }

    @Test
    fun `turning wifi only off buys sockets on mobile data`() {
        assertEquals(
            TickerMode.LIVE,
            TickerModeCalculator.mode(conditions(transport = Transport.CELLULAR, wifiOnly = false)),
        )
    }

    @Test
    fun `charging does not pay for the data plan`() {
        assertEquals(
            TickerMode.NEAR,
            TickerModeCalculator.mode(
                conditions(transport = Transport.CELLULAR, charging = true, wifiOnly = true),
            ),
        )
    }

    @Test
    fun `live with the screen off is SLEEP on any link`() {
        assertEquals(TickerMode.SLEEP, TickerModeCalculator.mode(conditions(screenInteractive = false)))
        assertEquals(
            TickerMode.SLEEP,
            TickerModeCalculator.mode(
                conditions(screenInteractive = false, transport = Transport.CELLULAR, wifiOnly = false),
            ),
        )
        // Charging changes nothing: nobody is looking at the widget behind a locked screen.
        assertEquals(
            TickerMode.SLEEP,
            TickerModeCalculator.mode(conditions(screenInteractive = false, charging = true)),
        )
    }

    @Test
    fun `no network is SLEEP whatever else is true`() {
        assertEquals(
            TickerMode.SLEEP,
            TickerModeCalculator.mode(conditions(transport = Transport.NONE, charging = true)),
        )
    }

    @Test
    fun `data saver only bites on a metered link`() {
        assertEquals(
            TickerMode.SLEEP,
            TickerModeCalculator.mode(conditions(transport = Transport.CELLULAR, dataSaver = true)),
        )
        assertEquals(
            TickerMode.SLEEP,
            TickerModeCalculator.mode(conditions(transport = Transport.WIFI, unmetered = false, dataSaver = true)),
        )
        assertEquals(
            TickerMode.LIVE,
            TickerModeCalculator.mode(conditions(transport = Transport.WIFI, dataSaver = true)),
        )
    }

    @Test
    fun `battery saver sleeps unless charging`() {
        assertEquals(TickerMode.SLEEP, TickerModeCalculator.mode(conditions(powerSave = true)))
        assertEquals(
            TickerMode.LIVE,
            TickerModeCalculator.mode(conditions(powerSave = true, charging = true)),
        )
    }

    /** Independent re-statement of the rules, walked over every input combination. */
    private fun expected(c: LiveConditions): TickerMode? = when {
        !c.hasWidgets -> null
        c.widgetRefresh != WidgetRefresh.LIVE -> TickerMode.TICK
        c.transport == Transport.NONE -> TickerMode.SLEEP
        c.dataSaver && !c.unmetered -> TickerMode.SLEEP
        c.powerSave && !c.charging -> TickerMode.SLEEP
        !c.screenInteractive -> TickerMode.SLEEP
        c.unmetered || !c.wifiOnly -> TickerMode.LIVE
        else -> TickerMode.NEAR
    }

    @Test
    fun `truth table`() {
        var cases = 0
        for (hasWidgets in BOOLS) {
            for (refresh in WidgetRefresh.entries) {
                for (screen in BOOLS) {
                    for (transport in Transport.entries) {
                        for (unmetered in BOOLS) {
                            for (charging in BOOLS) {
                                for (dataSaver in BOOLS) {
                                    for (powerSave in BOOLS) {
                                        for (wifiOnly in BOOLS) {
                                            val c = conditions(
                                                hasWidgets = hasWidgets,
                                                widgetRefresh = refresh,
                                                screenInteractive = screen,
                                                transport = transport,
                                                charging = charging,
                                                dataSaver = dataSaver,
                                                powerSave = powerSave,
                                                unmetered = unmetered,
                                                wifiOnly = wifiOnly,
                                            )
                                            cases++
                                            assertEquals(
                                                c.toString(),
                                                expected(c),
                                                TickerModeCalculator.mode(c),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        assertEquals(2 * 5 * 2 * 4 * 2 * 2 * 2 * 2 * 2, cases)
    }

    // ------------------------------------------------------------------ cadences

    @Test
    fun `LIVE holds sockets and renders every two seconds`() {
        val cadence = TickerModeCalculator.cadence(TickerMode.LIVE, LiveSettingsValues(WidgetRefresh.LIVE))
        assertEquals(true, cadence.useSockets)
        assertEquals(2_000L, cadence.widgetRefreshMs)
        assertEquals(SPARKLINE_REFRESH_MS, cadence.sparklineRefreshMs)
        assertEquals(0L, cadence.restRoundMs)
        assertEquals(false, cadence.sleepTick)
        assertEquals(0L, cadence.tickIntervalMs)
    }

    @Test
    fun `NEAR polls REST instead of holding a socket`() {
        val cadence = TickerModeCalculator.cadence(TickerMode.NEAR, LiveSettingsValues(WidgetRefresh.LIVE))
        assertEquals(false, cadence.useSockets)
        assertEquals(NEAR_REST_ROUND_MS, cadence.restRoundMs)
        assertEquals(NEAR_WIDGET_REFRESH_MS, cadence.widgetRefreshMs)
        assertEquals(false, cadence.sleepTick)
    }

    @Test
    fun `SLEEP runs no loop at all and ticks every five minutes`() {
        val cadence = TickerModeCalculator.cadence(TickerMode.SLEEP, LiveSettingsValues(WidgetRefresh.LIVE))
        assertEquals(false, cadence.useSockets)
        assertEquals(0L, cadence.widgetRefreshMs)
        assertEquals(0L, cadence.restRoundMs)
        assertEquals(0L, cadence.sparklineRefreshMs)
        assertEquals(true, cadence.sleepTick)
        assertEquals(LIVE_SCREEN_OFF_TICK_MS, cadence.tickIntervalMs)
    }

    @Test
    fun `TICK ticks at exactly the cadence the user picked`() {
        for (refresh in WidgetRefresh.entries - WidgetRefresh.LIVE) {
            val cadence = TickerModeCalculator.cadence(TickerMode.TICK, LiveSettingsValues(refresh))
            assertEquals(refresh.name, false, cadence.useSockets)
            assertEquals(refresh.name, 0L, cadence.widgetRefreshMs)
            assertEquals(refresh.name, true, cadence.sleepTick)
            assertEquals(refresh.name, refresh.intervalMs, cadence.tickIntervalMs)
            assertEquals(
                refresh.name,
                refresh.intervalMs,
                TickerModeCalculator.tickIntervalMs(TickerMode.TICK, LiveSettingsValues(refresh)),
            )
        }
    }

    @Test
    fun `only the tick modes ask for an alarm`() {
        val live = LiveSettingsValues(WidgetRefresh.LIVE)
        assertEquals(0L, TickerModeCalculator.tickIntervalMs(TickerMode.LIVE, live))
        assertEquals(0L, TickerModeCalculator.tickIntervalMs(TickerMode.NEAR, live))
        assertEquals(LIVE_SCREEN_OFF_TICK_MS, TickerModeCalculator.tickIntervalMs(TickerMode.SLEEP, live))
    }

    // ------------------------------------------------------------------ socket keys

    @Test
    fun `socket keys are the widget keys in LIVE and nothing anywhere else`() {
        val live = LiveSettingsValues(WidgetRefresh.LIVE)
        assertEquals(KEYS, TickerModeCalculator.socketKeys(KEYS, conditions(), live))
        assertEquals(
            emptySet<MarketKey>(),
            TickerModeCalculator.socketKeys(KEYS, conditions(screenInteractive = false), live),
        )
        assertEquals(
            emptySet<MarketKey>(),
            TickerModeCalculator.socketKeys(KEYS, conditions(transport = Transport.CELLULAR), live),
        )
    }

    @Test
    fun `a timed cadence never opens a socket`() {
        val timed = LiveSettingsValues(WidgetRefresh.MIN_5)
        assertEquals(
            emptySet<MarketKey>(),
            TickerModeCalculator.socketKeys(KEYS, conditions(widgetRefresh = WidgetRefresh.MIN_5), timed),
        )
    }

    @Test
    fun `charging does not change the socket set`() {
        val live = LiveSettingsValues(WidgetRefresh.LIVE)
        assertEquals(
            TickerModeCalculator.socketKeys(KEYS, conditions(), live),
            TickerModeCalculator.socketKeys(KEYS, conditions(charging = true), live),
        )
    }

    @Test
    fun `usesTickAlarm says exactly what the cadence says`() {
        // `rearmsHeartbeat` reads it without settings in hand, so the two must not drift apart.
        for (refresh in WidgetRefresh.entries) {
            val settings = LiveSettingsValues(refresh)
            for (mode in TickerMode.entries) {
                assertEquals(
                    "$mode / ${refresh.id}",
                    TickerModeCalculator.cadence(mode, settings).sleepTick,
                    TickerModeCalculator.usesTickAlarm(mode),
                )
            }
        }
    }

    @Test
    fun `no sockets when the service should not run at all`() {
        assertEquals(
            emptySet<MarketKey>(),
            TickerModeCalculator.socketKeys(KEYS, conditions(hasWidgets = false), LiveSettingsValues()),
        )
    }

    private companion object {
        val BOOLS = listOf(true, false)
        val KEYS = setOf(MarketKey("kraken:BTC/EUR"), MarketKey("binance:ETH/USDT"))
    }
}
