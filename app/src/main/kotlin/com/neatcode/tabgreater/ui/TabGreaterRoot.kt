package com.neatcode.tabgreater.ui

import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.util.Consumer
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.neatcode.tabgreater.ui.components.BottomNav
import com.neatcode.tabgreater.ui.components.BottomNavItem
import com.neatcode.tabgreater.ui.navigation.CHART_DEEP_LINK_BASE
import com.neatcode.tabgreater.ui.navigation.ChartRoute
import com.neatcode.tabgreater.ui.navigation.SettingsRoute
import com.neatcode.tabgreater.ui.navigation.TabGreaterNavHost
import com.neatcode.tabgreater.ui.navigation.WatchlistsRoute
import com.neatcode.tabgreater.ui.navigation.chartDeepLinkKey
import com.neatcode.tabgreater.ui.navigation.isRoute
import com.neatcode.tabgreater.ui.navigation.replaceStack
import com.neatcode.tabgreater.ui.navigation.showsOnlyChart
import com.neatcode.tabgreater.ui.navigation.switchRoot
import com.neatcode.tabgreater.ui.theme.TG

private const val TAG = "TabGreaterRoot"

/**
 * Application shell: the navigation host fills the screen and the bottom navigation bar is shown
 * only for the two root destinations, so the market search gets the full height (and the IME).
 */
@Composable
fun TabGreaterRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    val onSettings = destination.isRoute<SettingsRoute>()
    // Only the two roots keep the bar: the market search gets the full height (and the IME), and
    // the chart needs every pixel plus an uninterrupted gesture area for its WebView.
    val showBottomNav = onSettings || destination.isRoute<WatchlistsRoute>()

    val activity = LocalActivity.current as? ComponentActivity
    // A widget tap must land on the chart and nowhere else. On a cold start that is done by making
    // the chart the graph's start destination: it becomes the only back-stack entry, so there is no
    // grid to flash on the way in and Back returns to the home screen the tap came from. Read once
    // per activity, because the start destination is only consulted when the graph is created.
    val launchKey = remember(activity) { chartDeepLinkKey(activity?.intent?.data?.toString()) }
    val startDestination: Any = launchKey?.let { ChartRoute(it.value) } ?: WatchlistsRoute

    // A warm start (`singleTask`: the task is already showing the grid, the settings, the search or
    // another chart) arrives through onNewIntent instead, so repeat the contract by hand — the
    // chart replaces the whole stack, ending up as the single entry again.
    DisposableEffect(activity, navController) {
        val listener = Consumer<Intent> { intent ->
            val data = intent.data?.toString()
            val key = chartDeepLinkKey(data)
            when {
                // Tapping the widget of the chart already filling the stack would otherwise throw
                // its WebView away and re-fetch 500 bars to redraw the very same screen.
                key != null ->
                    if (!navController.showsOnlyChart(key)) {
                        navController.replaceStack(ChartRoute(key.value))
                    }
                data?.startsWith(CHART_DEEP_LINK_BASE) == true ->
                    Log.w(TAG, "chart link without a usable market key: $data")
                // Anything else is the launcher bringing the task forward with its MAIN intent.
                // A widget-opened chart is the whole stack and hides the bottom nav, so leaving it
                // on screen would make the app icon a dead end — the only way on would be Back,
                // which by contract exits. Send the icon where it always went: the grid.
                navController.showsOnlyChart() -> navController.replaceStack(WatchlistsRoute)
            }
        }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(TG.Background),
    ) {
        Box(Modifier.weight(1f)) {
            TabGreaterNavHost(navController, startDestination, Modifier.fillMaxSize())
        }
        if (showBottomNav) {
            BottomNav(
                selected = if (onSettings) BottomNavItem.SETTINGS else BottomNavItem.WATCHLISTS,
                onSelect = { item ->
                    when (item) {
                        BottomNavItem.WATCHLISTS -> navController.switchRoot(WatchlistsRoute)
                        BottomNavItem.SETTINGS -> navController.switchRoot(SettingsRoute)
                    }
                },
            )
        }
    }
}
