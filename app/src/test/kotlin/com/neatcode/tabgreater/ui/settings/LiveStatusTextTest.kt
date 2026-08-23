package com.neatcode.tabgreater.ui.settings

import com.neatcode.tabgreater.core.live.LiveDiagnosticsState
import com.neatcode.tabgreater.core.live.LiveSettingsValues
import com.neatcode.tabgreater.core.live.LiveStatus
import com.neatcode.tabgreater.core.live.TickerMode
import com.neatcode.tabgreater.core.live.Transport
import com.neatcode.tabgreater.core.live.WidgetRefresh
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/** Every branch of the Status row, which is the first thing to read when a widget goes stale. */
class LiveStatusTextTest {

    @Test
    fun `no widget is the whole answer`() {
        val text = liveStatusText(
            live = LiveSettingsValues(),
            diagnostics = LiveDiagnosticsState(serviceRunning = false, widgetCount = 0),
            zone = UTC,
        )

        assertEquals("No widgets", text)
    }

    @Test
    fun `the default timed cadence is named the way the settings row names it`() {
        val text = liveStatusText(
            live = LiveSettingsValues(widgetRefresh = WidgetRefresh.MIN_5),
            diagnostics = LiveDiagnosticsState(
                serviceRunning = true,
                mode = TickerMode.TICK,
                transport = Transport.WIFI,
                widgetCount = 1,
            ),
            zone = UTC,
        )

        // No transport and no socket line: in a timed mode neither changes what the service does.
        assertEquals("Service running · Every 5 min · 1 widget", text)
    }

    @Test
    fun `every timed cadence has its own label`() {
        val labels = listOf(
            WidgetRefresh.MIN_1 to "Every 1 min",
            WidgetRefresh.MIN_2 to "Every 2 min",
            WidgetRefresh.MIN_5 to "Every 5 min",
            WidgetRefresh.MIN_15 to "Every 15 min",
        )
        for ((refresh, label) in labels) {
            val text = liveStatusText(
                live = LiveSettingsValues(widgetRefresh = refresh),
                diagnostics = LiveDiagnosticsState(
                    serviceRunning = true,
                    mode = TickerMode.TICK,
                    widgetCount = 2,
                ),
                zone = UTC,
            )
            assertEquals("Service running · $label · 2 widgets", text)
        }
    }

    @Test
    fun `a running live service reports mode, transport, widget count, refresh and sockets`() {
        val text = liveStatusText(
            live = LiveSettingsValues(widgetRefresh = WidgetRefresh.LIVE),
            diagnostics = LiveDiagnosticsState(
                serviceRunning = true,
                mode = TickerMode.LIVE,
                transport = Transport.WIFI,
                streamStatus = LiveStatus.LIVE,
                widgetCount = 2,
                lastWidgetRefreshAt = REFRESHED_AT,
            ),
            zone = UTC,
        )

        assertEquals(
            "Service running · Live · Wi-Fi · 2 widgets\nLast update 22:13:20 · sockets LIVE",
            text,
        )
    }

    @Test
    fun `one widget on cellular is singular, named mobile data and reported as polling`() {
        val text = liveStatusText(
            live = LiveSettingsValues(widgetRefresh = WidgetRefresh.LIVE),
            diagnostics = LiveDiagnosticsState(
                serviceRunning = true,
                mode = TickerMode.NEAR,
                transport = Transport.CELLULAR,
                streamStatus = LiveStatus.OFFLINE,
                widgetCount = 1,
                lastWidgetRefreshAt = REFRESHED_AT,
            ),
            zone = UTC,
        )

        assertEquals(
            "Service running · Live (polling) · Mobile data · 1 widget\n" +
                "Last update 22:13:20 · sockets OFFLINE",
            text,
        )
    }

    @Test
    fun `live with the screen off reads as paused rather than broken`() {
        val text = liveStatusText(
            live = LiveSettingsValues(widgetRefresh = WidgetRefresh.LIVE),
            diagnostics = LiveDiagnosticsState(
                serviceRunning = true,
                mode = TickerMode.SLEEP,
                transport = Transport.WIFI,
                widgetCount = 1,
            ),
            zone = UTC,
        )

        assertEquals("Service running · Live (paused) · Wi-Fi · 1 widget\nsockets OFFLINE", text)
    }

    @Test
    fun `a service that has not evaluated its mode yet simply omits it`() {
        val text = liveStatusText(
            live = LiveSettingsValues(widgetRefresh = WidgetRefresh.LIVE),
            diagnostics = LiveDiagnosticsState(
                serviceRunning = true,
                mode = null,
                transport = Transport.ETHERNET,
                widgetCount = 3,
            ),
            zone = UTC,
        )

        assertEquals("Service running · Ethernet · 3 widgets\nsockets OFFLINE", text)
    }

    @Test
    fun `no network is named as such`() {
        val text = liveStatusText(
            live = LiveSettingsValues(widgetRefresh = WidgetRefresh.LIVE),
            diagnostics = LiveDiagnosticsState(
                serviceRunning = true,
                mode = TickerMode.SLEEP,
                transport = Transport.NONE,
                widgetCount = 1,
            ),
            zone = UTC,
        )

        assertEquals("Service running · Live (paused) · No network · 1 widget\nsockets OFFLINE", text)
    }

    @Test
    fun `an idle service with widgets placed still shows the last refresh and the last error`() {
        val text = liveStatusText(
            live = LiveSettingsValues(),
            diagnostics = LiveDiagnosticsState(
                serviceRunning = false,
                widgetCount = 2,
                lastWidgetRefreshAt = REFRESHED_AT,
                lastError = "refresh: IOException timeout",
            ),
            zone = UTC,
        )

        assertEquals(
            "Service idle · 2 widgets\nLast update 22:13:20 · refresh: IOException timeout",
            text,
        )
    }

    @Test
    fun `the timestamp is rendered in the zone it is given`() {
        val text = liveStatusText(
            live = LiveSettingsValues(),
            diagnostics = LiveDiagnosticsState(
                serviceRunning = false,
                widgetCount = 1,
                lastWidgetRefreshAt = REFRESHED_AT,
            ),
            zone = ZoneId.of("Europe/Zagreb"),
        )

        // 22:13:20 UTC is 23:13:20 in Zagreb on a November day (CET, UTC+1).
        assertEquals("Service idle · 1 widget\nLast update 23:13:20", text)
    }

    private companion object {
        val UTC: ZoneId = ZoneId.of("UTC")

        /** 2023-11-14T22:13:20Z. */
        const val REFRESHED_AT = 1_700_000_000_000L
    }
}
