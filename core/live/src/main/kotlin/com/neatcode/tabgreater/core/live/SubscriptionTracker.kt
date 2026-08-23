package com.neatcode.tabgreater.core.live

import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.MarketKey

/**
 * Reference counts the market keys that collectors are currently interested in, so overlapping
 * subscriptions (watchlist grid, chart header, widget) share one stream per exchange.
 *
 * [acquire] and [release] return the exchanges whose *set* of subscribed markets changed; only
 * those streams have to be restarted. Pure, synchronous and free of Android APIs so it can be
 * unit tested on the JVM.
 */
internal class SubscriptionTracker {

    private val counts = LinkedHashMap<MarketKey, Int>()

    @Synchronized
    fun acquire(keys: Collection<MarketKey>): Set<ExchangeId> {
        val changed = LinkedHashSet<ExchangeId>()
        for (key in keys.toSet()) {
            val previous = counts[key] ?: 0
            counts[key] = previous + 1
            if (previous == 0) changed += key.exchange
        }
        return changed
    }

    @Synchronized
    fun release(keys: Collection<MarketKey>): Set<ExchangeId> {
        val changed = LinkedHashSet<ExchangeId>()
        for (key in keys.toSet()) {
            val previous = counts[key] ?: continue
            if (previous <= 1) {
                counts.remove(key)
                changed += key.exchange
            } else {
                counts[key] = previous - 1
            }
        }
        return changed
    }

    @Synchronized
    fun keysOf(exchange: ExchangeId): Set<MarketKey> =
        counts.keys.filterTo(LinkedHashSet()) { it.exchange == exchange }

    @Synchronized
    fun countOf(key: MarketKey): Int = counts[key] ?: 0

    @Synchronized
    fun exchanges(): Set<ExchangeId> = counts.keys.mapTo(LinkedHashSet()) { it.exchange }
}
