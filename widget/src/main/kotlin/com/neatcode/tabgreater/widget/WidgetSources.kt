package com.neatcode.tabgreater.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.neatcode.tabgreater.core.data.db.TickerSnapshotDao
import com.neatcode.tabgreater.core.data.db.TickerSnapshotEntity
import com.neatcode.tabgreater.core.live.MarketDataRepository
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.Ticker

/**
 * The `appWidgetId`s the widget host actually owns right now.
 *
 * [WidgetConfigStore] is the app's own record of the placed widgets and it can outlive them:
 * `ACTION_APPWIDGET_DELETED` is a normal explicit broadcast and is not delivered to a package in
 * the stopped state, and auto-backup restores the
 * `widget_configs` DataStore with `appWidgetId`s no host ever allocated. The `AppWidgetManager`
 * enumeration is the authoritative list, so every key set and every repaint is reconciled against
 * it (findings 13 / 21).
 */
internal fun interface BoundWidgetIds {
    /**
     * The bound ids, or `null` when the enumeration failed — `null` means "cannot tell" and every
     * caller then keeps the stored configuration untouched rather than reporting zero widgets.
     */
    fun current(): Set<Int>?
}

/** The real enumeration: the ids the platform has bound to [TickerWidgetReceiver]. */
internal fun boundWidgetIds(context: Context): BoundWidgetIds = BoundWidgetIds {
    runCatching {
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, TickerWidgetReceiver::class.java))
            .toSet()
    }.getOrNull()
}

/**
 * Stored configurations restricted to the widgets that still exist. A `null` [bound] (enumeration
 * failure) is deliberately a no-op: a transient error must never wipe real configurations or stop
 * the live service.
 */
internal fun reconcileConfigs(
    stored: Map<Int, WidgetConfig>,
    bound: Set<Int>?,
): Map<Int, WidgetConfig> = if (bound == null) stored else stored.filterKeys { it in bound }

/**
 * Picks the newest known [Ticker] for a market out of the two places the live layer keeps one.
 *
 * [MarketDataRepository.latest] is written **only** by the WebSocket collector and is never
 * shrunk when a stream stops, while every REST round (`SLEEP` tick, `NEAR` polling, the 15-minute
 * worker) persists into `ticker_snapshots` and leaves `latest` alone. Preferring the in-memory map
 * therefore froze the widget on the last socket price for as long as the process lived
 * (findings 16 / 22), so both sources are read and the newer timestamp wins — the same rule
 * `LiveMarketDataRepository.mergeTickers` applies for the app UI.
 */
internal class TickerResolver(
    private val marketData: MarketDataRepository,
    private val snapshots: TickerSnapshotDao,
) {
    suspend fun resolve(key: MarketKey): Ticker? {
        val live = marketData.latest.value[key]
        val stored = runCatching { snapshots.get(key.value) }.getOrNull()?.toTicker(key)
        return newest(live, stored)
    }

    internal companion object {
        /** Ties go to the live value, matching `mergeTickers`' `>=`. */
        fun newest(live: Ticker?, stored: Ticker?): Ticker? = when {
            live == null -> stored
            stored == null -> live
            live.timestamp >= stored.timestamp -> live
            else -> stored
        }
    }
}

internal fun TickerSnapshotEntity.toTicker(key: MarketKey): Ticker = Ticker(
    key = key,
    last = last,
    open24h = open24h,
    high24h = high24h,
    low24h = low24h,
    volumeBase24h = volumeBase24h,
    volumeQuote24h = volumeQuote24h,
    changePct24h = changePct24h,
    timestamp = timestamp,
)
