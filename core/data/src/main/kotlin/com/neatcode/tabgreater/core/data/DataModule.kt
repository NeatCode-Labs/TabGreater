package com.neatcode.tabgreater.core.data

import com.neatcode.tabgreater.core.data.db.RoomTransactionRunner
import com.neatcode.tabgreater.core.data.db.TabGreaterDatabase
import com.neatcode.tabgreater.core.data.db.TransactionRunner
import com.neatcode.tabgreater.core.data.maintenance.CacheMaintenance
import com.neatcode.tabgreater.core.data.popular.CoinGeckoPopularPairsSource
import com.neatcode.tabgreater.core.data.popular.PopularPairsCache
import com.neatcode.tabgreater.core.data.popular.PopularPairsRepository
import com.neatcode.tabgreater.core.data.popular.PopularPairsSource
import com.neatcode.tabgreater.core.data.popular.PopularPairsStore
import com.neatcode.tabgreater.core.data.repo.MarketRepository
import com.neatcode.tabgreater.core.data.repo.RoomMarketRepository
import com.neatcode.tabgreater.core.data.repo.RoomSparklineRepository
import com.neatcode.tabgreater.core.data.repo.RoomWatchlistRepository
import com.neatcode.tabgreater.core.data.repo.SparklineRepository
import com.neatcode.tabgreater.core.data.repo.WatchlistRepository
import com.neatcode.tabgreater.core.data.settings.AppSettings
import com.neatcode.tabgreater.core.data.settings.SettingsStore
import com.neatcode.tabgreater.core.exchange.ExchangeAdapter
import com.neatcode.tabgreater.core.exchange.ExchangeRegistry
import com.neatcode.tabgreater.core.model.MarketKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.StringQualifier
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * Qualifier of the process-wide [CoroutineScope] (`SupervisorJob() + Dispatchers.Default`).
 * Exchange adapters and the live layer are constructed with it so a single failing stream can
 * never take the others down.
 */
val APP_SCOPE: StringQualifier = named("appScope")

/**
 * Qualifier of an optional `Flow<Set<MarketKey>>` of markets [CacheMaintenance] must never prune,
 * on top of everything the watchlists reference. `:core:live` binds it from the home-screen
 * widgets; when no module provides it the maintenance falls back to an empty set.
 */
val PINNED_KEYS: StringQualifier = named("pinnedKeys")

/**
 * Koin module for the persistence layer (Room + DataStore), the shared OkHttp client, the app
 * scope and the [ExchangeRegistry].
 *
 * The registry is built from **every** definition bound to [ExchangeAdapter], so `:app` only has
 * to declare the adapters, e.g.
 * ```
 * single<ExchangeAdapter>(named("binance")) { BinanceAdapter(get(), get(APP_SCOPE)) } bind ExchangeAdapter::class
 * ```
 */
val dataModule = module {
    single { TabGreaterDatabase.build(androidContext()) }
    single { get<TabGreaterDatabase>().watchlistDao() }
    single { get<TabGreaterDatabase>().watchlistItemDao() }
    single { get<TabGreaterDatabase>().marketDao() }
    single { get<TabGreaterDatabase>().candleDao() }
    single { get<TabGreaterDatabase>().tickerSnapshotDao() }
    single<AppSettings> { SettingsStore(androidContext()) }
    single<TransactionRunner> { RoomTransactionRunner(get()) }

    single(APP_SCOPE) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single {
        OkHttpClient.Builder()
            .connectTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // Adapters drive their own keep-alive/ping schedule per exchange protocol.
            .pingInterval(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    single { ExchangeRegistry(getAll<ExchangeAdapter>()) }

    // Quick-add chips of the "+ Ticker" screen: CoinGecko ranking, cached for 24 h.
    single<PopularPairsCache> { PopularPairsStore(androidContext()) }
    single<PopularPairsSource> { CoinGeckoPopularPairsSource(get()) }
    single { PopularPairsRepository(get(), get()) }

    single<WatchlistRepository> { RoomWatchlistRepository(get(), get(), get()) }
    single<MarketRepository> { RoomMarketRepository(get(), get()) }
    single<SparklineRepository> { RoomSparklineRepository(get(), get(), get()) }
    single {
        CacheMaintenance(
            get(), get(), get(), get(APP_SCOPE),
            extraKeys = getOrNull<Flow<Set<MarketKey>>>(PINNED_KEYS) ?: flowOf(emptySet()),
        )
    }
}

private const val HTTP_TIMEOUT_SECONDS = 15L
