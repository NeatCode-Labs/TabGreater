package com.neatcode.tabgreater.core.exchange.hyperliquid

internal val PAIR_OR_INDEX_QUOTE_HINTS = setOf("fx", "forex", "rates", "bonds", "index", "indices", "indexes")

/** Native contract denominations that are easy to confuse with the collateral currency. */
internal fun hyperliquidUnit(coin: String, quoteHint: String?): String? = when (coin) {
    "io:ANTH", "io:OAI" -> "USD billions"
    "xyz:ANT", "para:ANTH", "xyz:OURA" -> "USD/share"
    "xyz:JPY" -> "USD/JPY"
    "xyz:EUR" -> "EUR/USD"
    "xyz:GBP" -> "GBP/USD"
    "para:BTCD" -> "%"
    "para:OTHERS", "para:TOTAL2" -> null
    else -> when (quoteHint) {
        "rates", "bonds" -> "%"
        "index", "indices", "indexes" -> "index points"
        else -> null
    }
}
