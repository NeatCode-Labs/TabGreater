package com.neatcode.tabgreater.core.data.popular

import java.util.Locale

/**
 * The pairs the "+ Ticker" screen offers before the first CoinGecko answer has ever been cached,
 * and whenever every refresh has failed. Same shape as a ranked pair: `BASE/QUOTE`.
 */
val DEFAULT_POPULAR_PAIRS: List<String> =
    listOf("BTC/USDT", "ETH/USDT", "BNB/USDT", "SOL/USDT", "XRP/USDT")

/** Quote asset every quick-add chip is built against. */
const val POPULAR_QUOTE: String = "USDT"

/**
 * The single place that decides which CoinGecko rows may **not** become a quick-add chip.
 *
 * A market-cap ranking is full of assets nobody wants to chart: stablecoins (they are flat by
 * construction) and wrapped / staked / bridged receipts of a coin that is already in the list.
 * Three layers catch them, in order of precision:
 *
 * 1. [DENIED_IDS] — exact CoinGecko ids, for rows no generic rule would recognise.
 * 2. [DENIED_SYMBOLS] — exact tickers, for the same reason on the symbol side.
 * 3. Generic rules — a fiat marker inside the symbol, a `w` + known base ticker, or a
 *    wrapper word in the id or the name.
 *
 * The generic rules are what keep the list correct when CoinGecko lists a stablecoin or a
 * liquid-staking token the denylists have never seen.
 */
object PopularPairRules {

    /** Exact CoinGecko ids of assets that must never appear as a chip. */
    private val DENIED_IDS: Set<String> = setOf(
        // Stablecoins.
        "tether", "usd-coin", "usds", "dai", "first-digital-usd", "true-usd", "ethena-usde",
        "paypal-usd", "binance-usd", "usd1", "euro-coin", "usdd", "frax", "gemini-dollar",
        "blackrock-usd-institutional-digital-liquidity-fund", "sky-dollar", "ripple-usd",
        // Wrapped / staked / bridged receipts.
        "wrapped-bitcoin", "wrapped-steth", "staked-ether", "weth", "wrapped-eeth",
        "coinbase-wrapped-btc", "binance-bridged-usdt-bnb-smart-chain", "bridged-usdc",
        "wrapped-beacon-eth", "rocket-pool-eth", "jito-staked-sol", "binance-staked-sol",
        "mantle-staked-ether", "solv-btc", "lombard-staked-btc",
    )

    /** Exact tickers of the same assets, for feeds that rank an id the denylist has not seen. */
    private val DENIED_SYMBOLS: Set<String> = setOf(
        "dai", "frax", "wbtc", "wsteth", "steth", "weth", "weeth", "cbbtc", "cbeth", "reth",
        "jitosol", "bnsol", "meth", "solvbtc", "lbtc", "sfrxeth", "frxeth", "beth",
    )

    /**
     * Bases whose `w`-prefixed ticker is a wrapper (`wbtc`, `weth`, `wbnb`, `weeth`). Kept as a
     * closed set so a genuine coin that merely starts with `w` (`WIF`, `WLD`, `WAL`) survives.
     */
    private val WRAPPABLE_BASES: Set<String> = setOf(
        "btc", "eth", "bnb", "sol", "trx", "xrp", "avax", "hbar", "matic", "pol", "steth",
        "eeth", "beth", "ldo", "ada", "dot", "near",
    )

    /** A ticker carrying one of these is a fiat-pegged unit, not something to chart. */
    private val FIAT_MARKERS: List<String> = listOf("usd", "eur", "gbp")

    /** A wrapper word anywhere in the id or the name marks a receipt token. */
    private val WRAPPER_WORDS: List<String> = listOf("wrapped", "staked", "bridged", "pegged")

    /** True when the CoinGecko row must be skipped. All three arguments may be empty. */
    fun isExcluded(id: String, symbol: String, name: String): Boolean {
        val lowerId = id.lowercase(Locale.ROOT)
        val lowerSymbol = symbol.lowercase(Locale.ROOT)
        val lowerName = name.lowercase(Locale.ROOT)

        if (lowerId in DENIED_IDS || lowerSymbol in DENIED_SYMBOLS) return true
        if (FIAT_MARKERS.any { it in lowerSymbol }) return true
        if (lowerSymbol.length > 1 && lowerSymbol[0] == 'w' && lowerSymbol.drop(1) in WRAPPABLE_BASES) return true
        return WRAPPER_WORDS.any { it in lowerId || it in lowerName }
    }
}
