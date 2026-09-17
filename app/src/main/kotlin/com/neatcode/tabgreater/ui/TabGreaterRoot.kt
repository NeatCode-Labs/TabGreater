package com.neatcode.tabgreater.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.neatcode.tabgreater.ui.components.BottomNav
import com.neatcode.tabgreater.ui.components.BottomNavItem
import com.neatcode.tabgreater.ui.navigation.ChartRoute
import com.neatcode.tabgreater.ui.navigation.LaunchAction
import com.neatcode.tabgreater.ui.navigation.PendingLaunch
import com.neatcode.tabgreater.ui.navigation.SettingsRoute
import com.neatcode.tabgreater.ui.navigation.TabGreaterNavHost
import com.neatcode.tabgreater.ui.navigation.WatchlistsRoute
import com.neatcode.tabgreater.ui.navigation.chartDeepLinkKey
import com.neatcode.tabgreater.ui.navigation.isRoute
import com.neatcode.tabgreater.ui.navigation.launchAction
import com.neatcode.tabgreater.ui.navigation.replaceStack
import com.neatcode.tabgreater.ui.navigation.showsOnlyChart
import com.neatcode.tabgreater.ui.navigation.switchRoot
import com.neatcode.tabgreater.ui.theme.TG

private const val TAG = "TabGreaterRoot"

/**
 * Application shell: the navigation host fills the screen and the bottom navigation bar is shown
 * only for the two root destinations, so the market search gets the full height (and the IME).
 *
 * [launch] carries the intent the activity was started or re-launched with; the shell acts on it
 * here, once the graph exists, whichever came first — the intent or the composition.
 */
@Composable
fun TabGreaterRoot(launch: PendingLaunch) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    val onSettings = destination.isRoute<SettingsRoute>()
    // Only the two roots keep the bar: the market search gets the full height (and the IME), and
    // the chart needs every pixel plus an uninterrupted gesture area for its WebView.
    val showBottomNav = onSettings || destination.isRoute<WatchlistsRoute>()

    // A widget tap must land on the chart and nowhere else. When the tap started the activity, the
    // chart is made the graph's start destination: it becomes the only back-stack entry, so there is
    // no grid to flash on the way in and Back returns to the home screen the tap came from. Read
    // once per activity, because the start destination is only consulted when the graph is created
    // — and only when it starts empty: a recreated activity shows its restored back stack instead,
    // and the effect below is what enforces the contract there.
    val startDestination: Any = remember {
        chartDeepLinkKey(launch.intent?.data?.toString())?.let { ChartRoute(it.value) } ?: WatchlistsRoute
    }

    // Every launch intent ends up here: the start destination above already honoured a fresh
    // activity's widget link (so this finds the chart in place and stays), a warm start's
    // onNewIntent replaces the stack by hand, and the one delivered before the first frame to an
    // activity recreated under a task still in Recents — where the restored back stack, not the
    // start destination, decides what is showing — is replaced the same way.
    val pending = launch.intent
    LaunchedEffect(pending) {
        val intent = pending ?: return@LaunchedEffect
        // Consuming rewrites the key this effect is remembered by, which cancels it at its next
        // suspension point — so nothing below may suspend, or the tap would be dropped.
        launch.consume(intent)
        when (val action = launchAction(intent.data?.toString(), navController::showsOnlyChart)) {
            is LaunchAction.OpenChart -> navController.replaceStack(ChartRoute(action.key.value))
            LaunchAction.OpenGrid -> navController.replaceStack(WatchlistsRoute)
            is LaunchAction.BadLink -> Log.w(TAG, "chart link without a usable market key: ${action.data}")
            LaunchAction.Stay -> Unit
        }
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
