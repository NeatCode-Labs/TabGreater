package com.neatcode.tabgreater.core.model

import kotlinx.serialization.Serializable

/** Supported spot exchanges. [id] is the stable string used in [MarketKey] and persistence. */
@Serializable
enum class ExchangeId(val id: String, val displayName: String) {
    BINANCE("binance", "Binance"),
    GATE("gate", "Gate.io"),
    KRAKEN("kraken", "Kraken"),
    KUCOIN("kucoin", "KuCoin"),
    MEXC("mexc", "MEXC");

    /**
     * Two letters that still tell the five exchanges apart at 10 dp — the badge the tiles, the
     * chart header and the home-screen widget draw instead of a logo. Deliberately not the
     * exchanges' own marks: those are their trademarks and a public app has no licence to them.
     */
    val monogram: String
        get() = when (this) {
            BINANCE -> "BN"
            GATE -> "GT"
            KRAKEN -> "KR"
            KUCOIN -> "KC"
            MEXC -> "MX"
        }

    companion object {
        private val byId = entries.associateBy { it.id }
        fun fromId(id: String): ExchangeId =
            byId[id] ?: throw IllegalArgumentException("Unknown exchange id: $id")
        fun fromIdOrNull(id: String): ExchangeId? = byId[id]
    }
}
