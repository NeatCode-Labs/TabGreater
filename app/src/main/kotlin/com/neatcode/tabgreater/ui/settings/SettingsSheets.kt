package com.neatcode.tabgreater.ui.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.core.data.settings.WatchlistRefreshRates
import com.neatcode.tabgreater.core.live.WidgetRefresh
import com.neatcode.tabgreater.ui.components.TGBottomSheet
import com.neatcode.tabgreater.ui.components.TGSheetOption
import com.neatcode.tabgreater.ui.watchlist.rememberSheetDismiss

/** Which cadence sheet is on screen; `NONE` means neither. */
enum class RefreshSheet { NONE, WATCHLIST, WIDGET }

/**
 * "Watchlist refresh rate": how often the tiles and the chart header are allowed to redraw. It is
 * purely a sampling rate — the exchange sockets are untouched by every option here, which is what
 * the row's subtitle says out loud.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistRateSheet(
    current: Long,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val dismiss = rememberSheetDismiss(sheetState)
    TGBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_watchlist_rate),
        sheetState = sheetState,
    ) {
        WatchlistRefreshRates.OPTIONS.forEach { ms ->
            TGSheetOption(
                label = secondsLabel(ms),
                checked = ms == current,
                onClick = { dismiss { onPick(ms) } },
            )
        }
    }
}

/**
 * "Widget refresh": the one knob that decides what the background service does. Each row carries
 * its own one-line consequence, because the difference between the options is entirely a battery
 * story and nothing on the row itself would show it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetRefreshSheet(
    current: WidgetRefresh,
    onPick: (WidgetRefresh) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val dismiss = rememberSheetDismiss(sheetState)
    TGBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_widget_refresh),
        sheetState = sheetState,
    ) {
        WidgetRefresh.entries.forEach { option ->
            TGSheetOption(
                label = stringResource(widgetRefreshOptionLabel(option)),
                checked = option == current,
                onClick = { dismiss { onPick(option) } },
                supportingText = widgetRefreshOptionHint(option)?.let { stringResource(it) },
            )
        }
    }
}

/** The sheet row's own label: short, because the hint under it carries the explanation. */
private fun widgetRefreshOptionLabel(option: WidgetRefresh): Int = when (option) {
    WidgetRefresh.LIVE -> R.string.settings_widget_refresh_live
    WidgetRefresh.MIN_1 -> R.string.settings_widget_refresh_1m
    WidgetRefresh.MIN_2 -> R.string.settings_widget_refresh_2m
    WidgetRefresh.MIN_5 -> R.string.settings_widget_refresh_5m
    WidgetRefresh.MIN_15 -> R.string.settings_widget_refresh_15m
}

/** Only the two rows that need one: the expensive option and the recommended one. */
private fun widgetRefreshOptionHint(option: WidgetRefresh): Int? = when (option) {
    WidgetRefresh.LIVE -> R.string.settings_widget_refresh_live_hint
    WidgetRefresh.MIN_5 -> R.string.settings_widget_refresh_5m_hint
    else -> null
}

/** What the "Widget refresh" settings row shows as its current value. */
@Composable
fun widgetRefreshLabel(option: WidgetRefresh): String = stringResource(
    if (option == WidgetRefresh.LIVE) {
        R.string.settings_widget_refresh_live_row
    } else {
        widgetRefreshOptionLabel(option)
    },
)

/** `1 s` · `10 s`; the watchlist rates are all sub-minute by design. */
@Composable
fun secondsLabel(ms: Long): String = stringResource(R.string.settings_interval_seconds, ms / SECOND_MS)

private const val SECOND_MS = 1_000L
