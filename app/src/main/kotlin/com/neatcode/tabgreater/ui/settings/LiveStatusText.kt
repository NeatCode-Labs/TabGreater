package com.neatcode.tabgreater.ui.settings

import com.neatcode.tabgreater.core.live.LiveDiagnosticsState
import com.neatcode.tabgreater.core.live.LiveSettingsValues
import com.neatcode.tabgreater.core.live.TickerMode
import com.neatcode.tabgreater.core.live.Transport
import com.neatcode.tabgreater.core.live.WidgetRefresh
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The "why is my widget not updating?" line of the WIDGETS section, as a pure function of the two
 * snapshots the view model already holds.
 *
 * Unlike every other label on the screen this text is **not** in `strings.xml`: it is assembled
 * from a dozen fragments that only make sense together, it is the one piece of settings copy that
 * has to be unit-tested branch by branch, and the app ships English only. Keeping the
 * fragments next to the code that joins them is what makes both possible.
 *
 * Returns one or two lines: what the service is doing, then — when there is anything to report —
 * the last widget refresh, the socket state and the last error.
 *
 * @param zone the device time zone; injected so the tests do not depend on where they run.
 */
internal fun liveStatusText(
    live: LiveSettingsValues,
    diagnostics: LiveDiagnosticsState,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val liveMode = live.widgetRefresh == WidgetRefresh.LIVE
    val head = when {
        // Without a widget there is nothing for the service to do, and that is the whole answer.
        diagnostics.widgetCount == 0 -> NO_WIDGETS
        diagnostics.serviceRunning -> listOfNotNull(
            SERVICE_RUNNING,
            // `mode` is null for the moment between "service started" and its first evaluation.
            diagnostics.mode?.let { modeLabel(it, live.widgetRefresh) },
            // The link only changes what the service does in Live; in a timed mode a request is a
            // request, so naming the transport there would only add noise.
            transportLabel(diagnostics.transport).takeIf { liveMode },
            widgetsLabel(diagnostics.widgetCount),
        ).joinToString(SEPARATOR)

        else -> listOf(SERVICE_IDLE, widgetsLabel(diagnostics.widgetCount)).joinToString(SEPARATOR)
    }

    // The 15-minute worker refreshes widgets in every mode, so the timestamp is worth showing
    // always; the socket line only means something while the service actually holds sockets.
    val detail = listOfNotNull(
        diagnostics.lastWidgetRefreshAt.takeIf { it > 0L }?.let { "$LAST_UPDATE ${clock(it, zone)}" },
        if (liveMode && diagnostics.serviceRunning) "$SOCKETS ${diagnostics.streamStatus.name}" else null,
        diagnostics.lastError,
    ).joinToString(SEPARATOR)

    return if (detail.isEmpty()) head else "$head\n$detail"
}

/**
 * What the service is doing, in the words of the setting that caused it.
 *
 * In a timed mode the mode *is* the setting, so it is named as the user picked it. In Live the
 * three states are worth telling apart: they are exactly the three answers to "why is my widget
 * not moving right now?".
 */
private fun modeLabel(mode: TickerMode, refresh: WidgetRefresh): String = when (mode) {
    TickerMode.LIVE -> "Live"
    TickerMode.NEAR -> "Live (polling)"
    TickerMode.SLEEP -> "Live (paused)"
    TickerMode.TICK -> tickLabel(refresh)
}

/** `Every 5 min`; the status row is terse on purpose, the settings row spells it out. */
private fun tickLabel(refresh: WidgetRefresh): String = when (refresh) {
    WidgetRefresh.LIVE -> "Live"
    WidgetRefresh.MIN_1 -> "Every 1 min"
    WidgetRefresh.MIN_2 -> "Every 2 min"
    WidgetRefresh.MIN_5 -> "Every 5 min"
    WidgetRefresh.MIN_15 -> "Every 15 min"
}

private fun widgetsLabel(count: Int): String = if (count == 1) "1 widget" else "$count widgets"

private fun transportLabel(transport: Transport): String = when (transport) {
    Transport.NONE -> "No network"
    Transport.CELLULAR -> "Mobile data"
    Transport.WIFI -> "Wi-Fi"
    Transport.ETHERNET -> "Ethernet"
}

/** Wall-clock `HH:mm:ss`; [Locale.US] so the digits stay ASCII whatever the phone is set to. */
private fun clock(epochMs: Long, zone: ZoneId): String =
    CLOCK.format(Instant.ofEpochMilli(epochMs).atZone(zone))

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)

private const val SEPARATOR = " · "
private const val NO_WIDGETS = "No widgets"
private const val SERVICE_RUNNING = "Service running"
private const val SERVICE_IDLE = "Service idle"
private const val LAST_UPDATE = "Last update"
private const val SOCKETS = "sockets"
