package com.neatcode.tabgreater.core.data.popular

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * Turns one CoinGecko `/coins/markets` body into the quick-add pairs of the "+ Ticker" screen.
 *
 * Pure Kotlin on purpose: no Android, no OkHttp, no clock — the network lives in
 * [CoinGeckoPopularPairsSource], the caching in [PopularPairsRepository], so this class can be
 * tested with a JSON fixture. Unknown keys are ignored (the endpoint returns ~25 fields per coin
 * and adds new ones over time), and a body that cannot be decoded yields an empty list rather
 * than an exception: the caller treats "empty" as "keep the cache".
 *
 * @property quote quote asset appended to every ranked base, `USDT` by default.
 */
class PopularPairParser(private val quote: String = POPULAR_QUOTE) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The first [limit] chartable bases of [body], highest market cap first, as `BASE/[quote]`.
     * Stablecoins and wrapped / staked receipts are dropped by [PopularPairRules].
     */
    fun parse(body: String, limit: Int = DEFAULT_LIMIT): List<String> {
        if (limit <= 0) return emptyList()
        val coins = try {
            json.decodeFromString<List<CoinDto>>(body)
        } catch (e: SerializationException) {
            return emptyList()
        }
        return coins.asSequence()
            // The endpoint is already ordered by market cap; re-sorting keeps this correct even
            // if a caller ever passes an unordered body. sortedByDescending is stable, so rows
            // without a market cap keep the feed's own order.
            .sortedByDescending { it.marketCap ?: 0.0 }
            .filterNot { PopularPairRules.isExcluded(it.id, it.symbol, it.name) }
            .map { it.symbol.trim().uppercase(Locale.ROOT) }
            .filter { SYMBOL.matches(it) }
            .distinct()
            .take(limit)
            .map { "$it/$quote" }
            .toList()
    }

    /** The fields of a `/coins/markets` row this app reads; everything else is ignored. */
    @Serializable
    private data class CoinDto(
        val id: String = "",
        val symbol: String = "",
        val name: String = "",
        @SerialName("market_cap") val marketCap: Double? = null,
    )

    private companion object {
        const val DEFAULT_LIMIT = 5

        /** Guards against a junk ticker becoming a search query. */
        val SYMBOL = Regex("^[A-Z0-9]{2,15}$")
    }
}
