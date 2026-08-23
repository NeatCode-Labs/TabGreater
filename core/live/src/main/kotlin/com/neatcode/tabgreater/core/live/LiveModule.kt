package com.neatcode.tabgreater.core.live

import com.neatcode.tabgreater.core.data.APP_SCOPE
import com.neatcode.tabgreater.core.data.PINNED_KEYS
import com.neatcode.tabgreater.core.model.MarketKey
import kotlinx.coroutines.flow.Flow
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module

/**
 * Koin module for the live layer.
 *
 * Requires from [com.neatcode.tabgreater.core.data.dataModule]: `TickerSnapshotDao`,
 * `MarketRepository`, `SparklineRepository`, `ExchangeRegistry` and the [APP_SCOPE] coroutine
 * scope.
 *
 * [WidgetRefresher] is intentionally **not** declared here: `:widget` binds the Glance
 * implementation and the live layer resolves it optionally, falling back to [NoWidgets].
 *
 * [workerOf] needs Koin's WorkManager factory, which `TabGreaterApp` installs with
 * `workManagerFactory()`; `core/live`'s manifest removes the default `androidx.startup`
 * initializer so that call is the one that configures WorkManager.
 */
val liveModule = module {
    single<MarketDataRepository> { LiveMarketDataRepository(get(), get(), get(), get(APP_SCOPE)) }
    single<LiveSettings> { LiveSettingsStore(androidContext()) }
    single { LiveDiagnostics(androidContext()) }
    // The widget pairs, so `CacheMaintenance` (which cannot see `:widget`) stops pruning the
    // candles and snapshots a widget-only market renders from. Resolved lazily, so the fallback
    // applies to a build or test without the widget module.
    single<Flow<Set<MarketKey>>>(PINNED_KEYS) {
        (getOrNull<WidgetRefresher>() ?: NoWidgets).observeWidgetKeys()
    }
    workerOf(::WidgetRefreshWorker)
}
