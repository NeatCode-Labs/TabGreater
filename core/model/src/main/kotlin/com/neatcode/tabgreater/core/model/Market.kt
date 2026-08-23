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
 * A tradable spot market as reported by the exchange's instrument list.
 *
 * @property nativeSymbol the symbol string the exchange itself uses (REST + WS).
 * @property pricePrecision number of decimals to show for prices (derived from tick size).
 * @property tickSize price increment; `null` if the exchange does not expose it.
 */
@Serializable
data class Market(
    val key: MarketKey,
    val nativeSymbol: String,
    val pricePrecision: Int,
    val tickSize: Double? = null,
    val active: Boolean = true,
) {
    val exchange: ExchangeId get() = key.exchange
    val base: String get() = key.base
    val quote: String get() = key.quote
}
