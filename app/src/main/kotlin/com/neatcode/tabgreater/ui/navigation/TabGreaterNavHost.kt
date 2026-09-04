package com.neatcode.tabgreater.ui.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.neatcode.tabgreater.BuildConfig
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.ui.about.AboutScreen
import com.neatcode.tabgreater.ui.chart.ChartScreen
import com.neatcode.tabgreater.ui.search.TickerSearchScreen
import com.neatcode.tabgreater.ui.settings.SettingsScreen
import com.neatcode.tabgreater.ui.watchlist.WatchlistScreen

/**
 * The app's five destinations, wired with type-safe routes (navigation-compose 2.9.8):
 * `WatchlistsRoute`, `SettingsRoute`, `AboutRoute`, `SearchRoute(watchlistId)` and
 * `ChartRoute(key)`.
 *
 * [startDestination] is `WatchlistsRoute` for an ordinary launch and `ChartRoute(key)` when the
 * activity was started by a widget tap — `TabGreaterRoot` decides. No destination declares a deep
 * link, so the graph never builds a synthetic back stack of its own and the widget's chart stays
 * the single entry it was opened as.
 */
@Composable
fun TabGreaterNavHost(
    navController: NavHostController,
    startDestination: Any,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable<WatchlistsRoute> {
            WatchlistScreen(
                onOpenSearch = { watchlistId -> navController.navigate(SearchRoute(watchlistId)) { launchSingleTop = true } },
                onOpenChart = { key -> navController.navigate(ChartRoute(key.value)) { launchSingleTop = true } },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onOpenAbout = { navController.navigate(AboutRoute) { launchSingleTop = true } })
        }
        composable<AboutRoute> {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable<SearchRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<SearchRoute>()
            TickerSearchScreen(
                watchlistId = route.watchlistId,
                onBack = { navController.popBackStack() },
            )
        }
        composable<ChartRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ChartRoute>()
            val key = MarketKey.parseOrNull(route.key)
            val activity = LocalActivity.current
            // Leaving the chart goes back to wherever it was opened from — and out of the app when
            // it was opened from a widget, because then it is the whole back stack and the user
            // came from the home screen, not from the grid.
            val leaveChart: () -> Unit = { if (!navController.popBackStack()) activity?.finish() }
            if (key == null) {
                // A key that does not parse must never crash the app: leave the screen again. The
                // pop is a navigation side effect, so it belongs in a one-shot effect — run from
                // the composable body it fires again during the exit transition and empties the
                // whole back stack. Keyed on the raw key so a second bad route re-triggers it.
                LaunchedEffect(route.key) { leaveChart() }
            } else {
                ChartScreen(
                    key = key,
                    onBack = leaveChart,
                    debuggable = BuildConfig.DEBUG,
                )
            }
        }
    }
}

/**
 * Makes [route] the whole back stack, so Back from it leaves the app again — the state a widget
 * tap has to end in, whether the tap started the activity or reached a task that was already open.
 */
fun NavController.replaceStack(route: Any) {
    // The graph's own entry sits below every destination, so popping it inclusive is what clears
    // the stack; NavController re-adds it under the destination it is about to push.
    navigate(route) { popUpTo(graph.id) { inclusive = true } }
}

/**
 * `true` when a chart is the entire back stack — what a widget tap leaves behind — and, when [key]
 * is given, when the chart showing is that market's.
 */
fun NavController.showsOnlyChart(key: MarketKey? = null): Boolean {
    val entry = currentBackStackEntry ?: return false
    if (!entry.destination.isRoute<ChartRoute>() || previousBackStackEntry != null) return false
    return key == null || entry.toRoute<ChartRoute>().key == key.value
}

/** Switches between the two bottom-nav roots without stacking duplicates. */
fun NavController.switchRoot(route: Any) {
    navigate(route) {
        popUpTo(WatchlistsRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** `true` when [this] is the destination generated for route [T]. */
inline fun <reified T : Any> NavDestination?.isRoute(): Boolean =
    this?.hierarchy?.any { it.hasRoute<T>() } == true
