package com.neatcode.tabgreater.core.exchange

import com.neatcode.tabgreater.core.model.AssetClass
import com.neatcode.tabgreater.core.model.Market

/**
 * Tokenized shares ("stock tokens") are listed by Binance, Gate, KuCoin and MEXC as ordinary spot
 * markets next to their coins; Kraken keeps them in a catalogue of their own. These rules tell them
 * apart from the machine-readable markers each catalogue carries (checked against the live
 * catalogues on 2026-10-04) and name the share a token tracks: `TSLA` for `TSLAX`, `TSLAON`, `TSLAB`,
 * `TSLAG` and Kraken's `TSLAx` alike.
 *
 * Every rule answers the underlying ticker, or `null` for a crypto asset. A ticker suffix alone
 * never classifies anything — `AVAX`, `TRX`, `BNB`, `SHIB` and `ELON` are coins — it is only
 * stripped once a marker has said "stock".
 */
internal object StockTokens {

    /**
     * Binance trading group that, on 2026-10-04, sat in the `permissionSets` of exactly the 87 bStock
     * symbols and nothing else. It is undocumented: if Binance renumbers it, the tokens simply fall
     * back to crypto.
     */
    const val BINANCE_STOCK_GROUP: String = "TRD_GRP_261"

    /** Binance: bStocks are `<ticker>B` (`TSLAB`) in [BINANCE_STOCK_GROUP]. */
    fun binance(baseAsset: String, inStockGroup: Boolean): String? =
        if (inStockGroup && baseAsset.length > 1 && baseAsset.endsWith(BSTOCK_SUFFIX)) {
            baseAsset.dropLast(BSTOCK_SUFFIX.length)
        } else {
            null
        }

    /**
     * Gate: the `category` list `/spot/currencies` reports for the base currency. Every stock token
     * carries [GATE_STOCKS] next to its issuer's tag; tokens without one (pre-IPO shares such as
     * `SPCX` or `OPENAI`) are their own underlying. The Ondo tag alone is no marker: the ONDO coin
     * itself carries only that one.
     */
    fun gate(base: String, categories: List<String>): String? = when {
        GATE_STOCKS !in categories -> null
        GATE_XSTOCKS in categories -> strip(base, XSTOCK_SUFFIX)
        GATE_ONDO_STOCKS in categories -> strip(base, ONDO_SUFFIX)
        GATE_GSTOCKS in categories -> strip(base, GSTOCK_SUFFIX)
        else -> base
    }

    /**
     * Gate without its currency list: only xStocks (`"Tesla xStock"`) and Ondo tokens
     * (`"Apple Ondo Tokenized"`) can be told from the pair's `base_name`; everything else stays crypto.
     */
    fun gateByName(base: String, baseName: String): String? = when {
        baseName.endsWith(GATE_XSTOCK_NAME) -> strip(base, XSTOCK_SUFFIX)
        baseName.endsWith(GATE_ONDO_NAME) -> strip(base, ONDO_SUFFIX)
        else -> null
    }

    /**
     * Gate's leveraged tokens (`<root>3L`, `<root>5S`) carry no category at all. One whose root is a
     * share in [stockRoots] (`TSLA3L`) tracks that share; `BTC3L` stays crypto.
     */
    fun gateLeveraged(base: String, stockRoots: Set<String>): String? =
        GATE_LEVERAGED.matchEntire(base)?.groupValues?.get(1)?.takeIf { it in stockRoots }

    /**
     * KuCoin: the symbol's `market` is [KUCOIN_STOCKS_MARKET] for its xStocks, except the few in
     * [KUCOIN_STRAY_XSTOCKS].
     */
    fun kucoin(baseCurrency: String, market: String?): String? =
        if (market == KUCOIN_STOCKS_MARKET || baseCurrency in KUCOIN_STRAY_XSTOCKS) {
            strip(baseCurrency, XSTOCK_SUFFIX)
        } else {
            null
        }

    /**
     * MEXC: the [MEXC_STOCKS_PLATE] concept plate, or a `fullName` naming an xStock or an Ondo token
     * (`SOXLON` and `SOXSON` lack the plate). The plates "Stock Meme Tokens" and "Pre-IPO" are
     * deliberately not markers: the first tags meme coins, the second is not a listed share.
     * xStocks are Solana tokens whose mint address starts with [XSTOCK_MINT_PREFIX].
     */
    fun mexc(baseAsset: String, fullName: String, contractAddress: String, conceptPlates: List<String>): String? {
        val stock = MEXC_STOCKS_PLATE in conceptPlates ||
            XSTOCK_NAME in fullName ||
            MEXC_ONDO_NAME in fullName
        if (!stock) return null
        return if (contractAddress.startsWith(XSTOCK_MINT_PREFIX) || XSTOCK_NAME in fullName) {
            strip(baseAsset, XSTOCK_SUFFIX)
        } else {
            strip(baseAsset, ONDO_SUFFIX)
        }
    }

    /**
     * Kraken: the marker is the catalogue itself — every pair of `aclass_base=tokenized_asset` is a
     * share — so this rule never answers `null`. Bases are `<ticker>x` with a lowercase `x` (`AAPLx`);
     * only that suffix is stripped, an upper-case `X` belongs to the ticker (`TONXx` tracks `TONX`).
     */
    fun kraken(base: String): String = strip(base, KRAKEN_XSTOCK_SUFFIX)

    /** [base] without [suffix]; [base] itself when the suffix is missing or nothing would be left. */
    fun strip(base: String, suffix: String): String =
        if (base.length > suffix.length && base.endsWith(suffix)) base.dropLast(suffix.length) else base

    private const val BSTOCK_SUFFIX = "B"
    private const val XSTOCK_SUFFIX = "X"
    private const val KRAKEN_XSTOCK_SUFFIX = "x"
    private const val ONDO_SUFFIX = "ON"
    private const val GSTOCK_SUFFIX = "G"

    private const val XSTOCK_NAME = "xStock"
    private const val XSTOCK_MINT_PREFIX = "Xs"

    private const val GATE_STOCKS = "stocks"
    private const val GATE_XSTOCKS = "xstocks"
    private const val GATE_ONDO_STOCKS = "ondo-stocks"
    private const val GATE_GSTOCKS = "gstocks"
    private const val GATE_XSTOCK_NAME = " xStock"
    private const val GATE_ONDO_NAME = " Ondo Tokenized"
    private val GATE_LEVERAGED = Regex("(.+)[35][LS]")

    private const val KUCOIN_STOCKS_MARKET = "Stocks"

    /** xStocks KuCoin files under another market: `SPCXX` (SpaceX) sat in "USDS" on 2026-10-04. */
    private val KUCOIN_STRAY_XSTOCKS = setOf("SPCXX")

    private const val MEXC_STOCKS_PLATE = "Tokenized Stocks"
    private const val MEXC_ONDO_NAME = "(Ondo)"
}

/**
 * This market tagged with what a [StockTokens] rule answered: [AssetClass.STOCK] plus the upper-cased
 * underlying, or the market unchanged (crypto) for `null`.
 */
internal fun Market.classified(underlying: String?): Market =
    if (underlying == null) this else copy(assetClass = AssetClass.STOCK, underlying = underlying.uppercase())
