package com.neatcode.tabgreater.core.live

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * In-memory [LiveSettings] — the fake the service and the mode tests use, and the reference for
 * the defaults.
 */
class FakeLiveSettings(initial: LiveSettingsValues = LiveSettingsValues()) : LiveSettings {

    private val state = MutableStateFlow(initial)

    override val values = state
    override val widgetRefresh = state.map { it.widgetRefresh }
    override val wifiOnly = state.map { it.wifiOnly }

    override suspend fun setWidgetRefresh(refresh: WidgetRefresh) {
        state.value = state.value.copy(widgetRefresh = refresh)
    }

    override suspend fun setWifiOnly(enabled: Boolean) {
        state.value = state.value.copy(wifiOnly = enabled)
    }
}

class LiveSettingsTest {

    @Test
    fun `defaults are the cheap timed mode on wifi-only`() = runTest {
        val settings = FakeLiveSettings()
        assertEquals(WidgetRefresh.MIN_5, settings.widgetRefresh.first())
        assertEquals(300_000L, settings.widgetRefresh.first().intervalMs)
        assertTrue(settings.wifiOnly.first())
    }

    @Test
    fun `every option carries the cadence its label promises`() {
        assertEquals(2_000L, WidgetRefresh.LIVE.intervalMs)
        assertEquals(60_000L, WidgetRefresh.MIN_1.intervalMs)
        assertEquals(120_000L, WidgetRefresh.MIN_2.intervalMs)
        assertEquals(300_000L, WidgetRefresh.MIN_5.intervalMs)
        assertEquals(900_000L, WidgetRefresh.MIN_15.intervalMs)
    }

    @Test
    fun `ids round-trip and anything unknown falls back to the default`() {
        for (option in WidgetRefresh.entries) {
            assertEquals(option, WidgetRefresh.fromId(option.id))
        }
        assertEquals(DEFAULT_WIDGET_REFRESH, WidgetRefresh.fromId(null))
        assertEquals(DEFAULT_WIDGET_REFRESH, WidgetRefresh.fromId("30s"))
        // The keys of the four settings this one replaced are simply not read any more.
        assertEquals(DEFAULT_WIDGET_REFRESH, WidgetRefresh.fromId("live_interval_ms"))
    }

    @Test
    fun `picking a timed cadence puts the service in TICK`() = runTest {
        val settings = FakeLiveSettings()
        val conditions = LiveConditions(
            hasWidgets = true,
            screenInteractive = true,
            transport = Transport.WIFI,
            charging = false,
            dataSaver = false,
            powerSave = false,
            unmetered = true,
            widgetRefresh = settings.widgetRefresh.first(),
        )
        assertEquals(TickerMode.TICK, TickerModeCalculator.mode(conditions))

        settings.setWidgetRefresh(WidgetRefresh.LIVE)
        assertEquals(
            TickerMode.LIVE,
            TickerModeCalculator.mode(conditions.copy(widgetRefresh = settings.widgetRefresh.first())),
        )
    }

    @Test
    fun `the wifi-only switch is stored as given`() = runTest {
        val settings = FakeLiveSettings()
        settings.setWifiOnly(false)
        assertFalse(settings.wifiOnly.first())
        assertFalse(settings.values.first().wifiOnly)
    }
}
