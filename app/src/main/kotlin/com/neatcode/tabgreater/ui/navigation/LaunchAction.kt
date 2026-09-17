package com.neatcode.tabgreater.ui.navigation

import com.neatcode.tabgreater.core.model.MarketKey

/** What the shell does with a launch intent, decided from its data and what the stack shows. */
sealed interface LaunchAction {
    /** A widget tap: the chart of [key] becomes the whole back stack. */
    data class OpenChart(val key: MarketKey) : LaunchAction

    /** The launcher icon over a widget-opened chart: the grid replaces it. */
    data object OpenGrid : LaunchAction

    /** A chart link this app never sends — worth a log line, nothing to open. */
    data class BadLink(val data: String) : LaunchAction

    /** Nothing to do: the stack already shows the right screen. */
    data object Stay : LaunchAction
}

/**
 * [data] is `intent.data?.toString()`; [showsOnlyChart] answers whether a chart is the entire back
 * stack and, given a key, whether it is that market's. Pure, so the rules are unit-tested.
 */
fun launchAction(data: String?, showsOnlyChart: (MarketKey?) -> Boolean): LaunchAction {
    val key = chartDeepLinkKey(data)
    return when {
        // Tapping the widget of the chart already filling the stack would otherwise throw its
        // WebView away and re-fetch 500 bars to redraw the very same screen.
        key != null -> if (showsOnlyChart(key)) LaunchAction.Stay else LaunchAction.OpenChart(key)
        isChartLink(data) -> LaunchAction.BadLink(data!!)
        // Anything else is the launcher bringing the task forward with its MAIN intent. A
        // widget-opened chart is the whole stack and hides the bottom nav, so leaving it on screen
        // would make the app icon a dead end — the only way on would be Back, which by contract
        // exits. Send the icon where it always went: the grid.
        showsOnlyChart(null) -> LaunchAction.OpenGrid
        else -> LaunchAction.Stay
    }
}
