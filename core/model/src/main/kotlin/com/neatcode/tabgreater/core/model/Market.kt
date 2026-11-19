package com.neatcode.tabgreater.core.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Canonical market key: `"exchange:BASE/QUOTE"`, e.g. `"kraken:BTC/EUR"`.
 *
 * This is the only identity used across the app (database, watchlists, widgets, navigation).
 * Native exchange symbols (`XXBTZEUR`, `BTC-USDT`, `BTCEUR`) never leak out of the exchange adapters.
 */
@Serializable
@JvmInline
value class MarketKey(val value: String) {
    init {
        require(isValid(value)) { "Invalid market key: $value" }
    }

    val exchange: ExchangeId get() = ExchangeId.fromId(value.substringBefore(':'))
    val base: String get() = value.substringAfter(':').substringBefore('/')
    val quote: String get() = value.substringAfter('/')

    /** `BTC/EUR` — the display pair without the exchange prefix. */
    val pair: String get() = value.substringAfter(':')

    override fun toString(): String = value

    companion object {
        fun of(exchange: ExchangeId, base: String, quote: String): MarketKey =
            MarketKey("${exchange.id}:${base.uppercase()}/${quote.uppercase()}")

        fun parseOrNull(value: String): MarketKey? = if (isValid(value)) MarketKey(value) else null

        private fun isValid(v: String): Boolean {
            val colon = v.indexOf(':')
            val slash = v.indexOf('/')
            if (colon <= 0 || slash <= colon + 1 || slash == v.lastIndex) return false
            return ExchangeId.fromIdOrNull(v.substring(0, colon)) != null
        }
    }
}

/**
 * One market exposed by an exchange or protocol.
 *
 * @property nativeSymbol the symbol string the exchange itself uses (REST + WS).
 * @property pricePrecision number of decimals to show for prices (derived from tick size).
 * @property tickSize price increment; `null` if the exchange does not expose it.
 * @property assetClass Source routing flag for Kraken tokenized assets, including saved markets.
 * @property underlying ticker, index, or other underlying used for search and display when known.
 * @property displayName human-readable instrument or event-side label, when it adds context.
 * @property unit display unit for derivative price quotes, for example "USD per share".
 * @property eventId parent outcome question id, when applicable.
 * @property outcomeId protocol outcome id, when applicable.
 * @property outcomeSide outcome side name such as "Yes" or "No".
 * @property expiresAt scheduled expiry or settlement time in epoch milliseconds, when known.
 */
@Serializable
data class Market(
    val key: MarketKey,
    val nativeSymbol: String,
    val pricePrecision: Int,
    val tickSize: Double? = null,
    val active: Boolean = true,
    val assetClass: AssetClass = AssetClass.CRYPTO,
    val underlying: String? = null,
    val instrumentKind: InstrumentKind = InstrumentKind.SPOT,
    val displayName: String? = null,
    val description: String? = null,
    val unit: String? = null,
    val venue: String? = null,
    val eventId: String? = null,
    val outcomeId: String? = null,
    val outcomeSide: String? = null,
    val expiresAt: Long? = null,
    /** Stable family id for renewable contracts; market keys and history remain contract-specific. */
    val rolloverSeriesId: String? = null,
) {
    val exchange: ExchangeId get() = key.exchange
    val base: String get() = key.base
    val quote: String get() = key.quote
}
