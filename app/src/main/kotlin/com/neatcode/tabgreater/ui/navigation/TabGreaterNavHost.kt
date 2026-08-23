package com.neatcode.tabgreater.ui.navigation

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
import androidx.navigation.navDeepLink
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
 * `ChartRoute(key)` — the last one also reachable through the `tabgreater://chart/{key}` deep
 * link the widgets fire.
 */
@Composable
fun TabGreaterNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = WatchlistsRoute,
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
        composable<ChartRoute>(
            deepLinks = listOf(navDeepLink<ChartRoute>(basePath = CHART_DEEP_LINK_BASE)),
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<ChartRoute>()
            val key = MarketKey.parseOrNull(route.key)
            if (key == null) {
                // A malformed deep link must never crash the app: fall straight back to the grid.
                // The pop is a navigation side effect, so it belongs in a one-shot effect — run
                // from the composable body it fires again during the exit transition and empties
                // the whole back stack. Keyed on the raw key so a second bad link re-triggers it.
                LaunchedEffect(route.key) {
                    // A deep link clears the stack, so there is usually nothing left to pop.
                    if (!navController.popBackStack()) navController.navigate(WatchlistsRoute)
                }
            } else {
                ChartScreen(
                    key = key,
                    onBack = {
                        // A cold start straight into the chart has nothing to pop back to.
                        if (!navController.popBackStack()) navController.navigate(WatchlistsRoute)
                    },
                    debuggable = BuildConfig.DEBUG,
                )
            }
        }
    }
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
