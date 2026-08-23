package com.neatcode.tabgreater.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.util.Consumer
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.neatcode.tabgreater.ui.components.BottomNav
import com.neatcode.tabgreater.ui.components.BottomNavItem
import com.neatcode.tabgreater.ui.navigation.SettingsRoute
import com.neatcode.tabgreater.ui.navigation.TabGreaterNavHost
import com.neatcode.tabgreater.ui.navigation.WatchlistsRoute
import com.neatcode.tabgreater.ui.navigation.isRoute
import com.neatcode.tabgreater.ui.navigation.switchRoot
import com.neatcode.tabgreater.ui.theme.TG

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

    // Cold-start deep links are handled by NavHost itself; a warm start (`singleTask`) arrives
    // through onNewIntent, which the graph only sees if it is handed the intent explicitly.
    val activity = LocalActivity.current as? ComponentActivity
    DisposableEffect(activity, navController) {
        val listener = Consumer<Intent> { intent -> navController.handleDeepLink(intent) }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(TG.Background),
    ) {
        Box(Modifier.weight(1f)) {
            TabGreaterNavHost(navController, Modifier.fillMaxSize())
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
