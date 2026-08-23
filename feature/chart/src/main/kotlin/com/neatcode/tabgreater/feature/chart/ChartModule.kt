package com.neatcode.tabgreater.feature.chart

import com.neatcode.tabgreater.core.data.APP_SCOPE
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin module for the chart feature (WebView + KLineChart bridge, F4).
 *
 * Both definitions are process-wide singletons on purpose: there is exactly one cached WebView, so
 * there is one bridge owning its live-bar subscription, and the chart's preferences live in their
 * own DataStore file (`chart_settings`), separate from the watchlist settings.
 */
val chartModule = module {
    single { ChartSettingsStore(androidContext()) } bind ChartPreferences::class
    single { ChartBridge(scope = get(APP_SCOPE), registry = get(), markets = get()) }
}
