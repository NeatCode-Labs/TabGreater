package com.neatcode.tabgreater.widget

import com.neatcode.tabgreater.core.live.WidgetRefresher
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin module for the Glance ticker widget.
 *
 * It closes the `:core:live` → `:widget` seam: `LiveTickerService` and the WorkManager safety net
 * only ever see [WidgetRefresher], never this module's classes.
 */
val widgetModule = module {
    single { WidgetConfigStore(androidContext()) }
    single {
        GlanceWidgetRefresher(
            context = androidContext(),
            configs = get(),
            marketData = get(),
            markets = get(),
            snapshots = get(),
            sparklines = get(),
        )
    } bind WidgetRefresher::class
}
