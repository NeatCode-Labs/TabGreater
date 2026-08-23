package com.neatcode.tabgreater

import android.util.Log
import com.neatcode.tabgreater.core.data.APP_SCOPE
import com.neatcode.tabgreater.core.exchange.ExchangeAdapter
import com.neatcode.tabgreater.core.exchange.binance.BinanceAdapter
import com.neatcode.tabgreater.core.exchange.gate.GateAdapter
import com.neatcode.tabgreater.core.exchange.kraken.KrakenAdapter
import com.neatcode.tabgreater.core.exchange.kucoin.KuCoinAdapter
import com.neatcode.tabgreater.core.exchange.mexc.MexcAdapter
import com.neatcode.tabgreater.core.live.LiveDiagnostics
import com.neatcode.tabgreater.core.live.LiveTickerLauncher
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.ui.chart.ChartViewModel
import com.neatcode.tabgreater.ui.manager.WatchlistManagerViewModel
import com.neatcode.tabgreater.ui.search.TickerSearchViewModel
import com.neatcode.tabgreater.ui.settings.SettingsViewModel
import com.neatcode.tabgreater.ui.watchlist.WatchlistViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * App-level Koin module: the Compose view models.
 *
 * Everything they depend on comes from the other modules —
 * `WatchlistRepository`, `MarketRepository`, `SparklineRepository` and `AppSettings` from
 * `dataModule`, `MarketDataRepository` from `liveModule`.
 */
val appModule = module {
    // Exchange adapters: every definition bound to ExchangeAdapter is collected by ExchangeRegistry.
    single<ExchangeAdapter>(named(ExchangeId.BINANCE.id)) {
        BinanceAdapter(client = get(), scope = get(APP_SCOPE), logger = exchangeLogger("Binance"))
    } bind ExchangeAdapter::class
    single<ExchangeAdapter>(named(ExchangeId.GATE.id)) {
        GateAdapter(client = get(), scope = get(APP_SCOPE), logger = exchangeLogger("Gate"))
    } bind ExchangeAdapter::class
    single<ExchangeAdapter>(named(ExchangeId.KRAKEN.id)) {
        KrakenAdapter(client = get(), scope = get(APP_SCOPE), logger = exchangeLogger("Kraken"))
    } bind ExchangeAdapter::class
    single<ExchangeAdapter>(named(ExchangeId.KUCOIN.id)) {
        KuCoinAdapter(client = get(), scope = get(APP_SCOPE), logger = exchangeLogger("KuCoin"))
    } bind ExchangeAdapter::class
    single<ExchangeAdapter>(named(ExchangeId.MEXC.id)) {
        MexcAdapter(client = get(), scope = get(APP_SCOPE), logger = exchangeLogger("MEXC"))
    } bind ExchangeAdapter::class

    viewModelOf(::WatchlistViewModel)
    viewModelOf(::WatchlistManagerViewModel)

    // Settings reads the live layer through three narrow seams (a StateFlow and two functions)
    // rather than through LiveDiagnostics itself, so its JVM tests need no Android at all.
    viewModel {
        // Both are resolved here rather than inside the lambdas: those outlive this scope.
        val application = androidApplication()
        val diagnostics = get<LiveDiagnostics>()
        SettingsViewModel(
            settings = get(),
            watchlistRepository = get(),
            liveSettings = get(),
            diagnostics = diagnostics.state,
            batteryUnrestricted = { diagnostics.isIgnoringBatteryOptimizations },
            onWidgetsChanged = { LiveTickerLauncher.onWidgetsChanged(application) },
            application = application,
        )
    }

    // watchlistId is a navigation argument, so it is injected as a runtime parameter.
    viewModel { parameters -> TickerSearchViewModel(parameters.get(), get(), get(), get()) }

    // Likewise the chart's market key (also carried by the tabgreater://chart deep link).
    viewModel { parameters -> ChartViewModel(parameters.get(), get(), get(), get(), get(), get(), get()) }
}

/** Adapter diagnostics go to logcat in debug builds only; release builds stay silent. */
private fun exchangeLogger(tag: String): (String) -> Unit =
    if (BuildConfig.DEBUG) { msg -> Log.d(tag, msg) } else { _ -> }
