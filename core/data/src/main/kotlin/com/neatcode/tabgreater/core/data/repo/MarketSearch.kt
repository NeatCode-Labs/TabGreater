package com.neatcode.tabgreater.core.data.repo

/**
 * Pure query parsing for [MarketRepository.searchPage]. Kept free of Room so it can be unit tested
 * on the JVM.
 *
 * @property base matched against `markets.base` — as a **prefix** while the user has not typed a
 *   `/` (`"ETH"` finds `ETH/…` and `ETHFI/…`), but **exactly** once a `/` is present (`"ETH/USD"`
 *   finds `ETH/USD`, `ETH/USDT`, `ETH/USDC`, never `ETHFI/USD`: the divider says the base is
 *   complete). Empty means "any base".
 * @property quote prefix matched against `markets.quote`; `null` when the user did not type a
 *   `/`, in which case the query is also tried as a quote prefix and as a concatenated
 *   `"BASEQUOTE"` form.
 */
internal data class MarketQuery(val base: String, val quote: String?) {
    val isBlank: Boolean get() = base.isEmpty() && quote.isNullOrEmpty()
}

/**
 * Trims, uppercases and removes everything that cannot occur in a market symbol. Whitespace and
 * SQL `LIKE` wildcards (`%`, `_`) are dropped so user input can be interpolated into a prefix
 * pattern safely; `/` survives because it separates base from quote.
 */
internal fun normaliseSearchQuery(raw: String): String = buildString(raw.length) {
    for (c in raw.trim().uppercase()) {
        if (c in 'A'..'Z' || c in '0'..'9' || c == '/') append(c)
    }
}

private val searchWhitespace = Regex("\\s+")

/** Keep punctuation literal in names, outcome thresholds and source identifiers. */
internal fun normaliseSearchText(raw: String): String = raw.trim().replace(searchWhitespace, " ")

/** A slash query is a pair only when both sides are compact symbols, with optional slash spacing. */
internal fun isCanonicalPairSearch(raw: String): Boolean {
    val value = raw.trim()
    val slash = value.indexOf('/')
    if (slash < 0 || value.indexOf('/', slash + 1) >= 0) return false
    val base = value.substring(0, slash).trim()
    val quote = value.substring(slash + 1).trim()
    return (base.isEmpty() || base.all(::isPairSymbolCharacter)) &&
        (quote.isEmpty() || quote.all(::isPairSymbolCharacter))
}

/** Pair spelling used to match display-name/native aliases without substring-matching similar bases. */
internal fun normalisePairDisplayPrefix(raw: String): String? {
    if (!isCanonicalPairSearch(raw)) return null
    val value = raw.trim().uppercase()
    val slash = value.indexOf('/')
    return "${value.substring(0, slash).trim()}/${value.substring(slash + 1).trim()}"
}

private fun isPairSymbolCharacter(char: Char): Boolean =
    char.isLetterOrDigit() || char in "@#:_-."

/** Splits a normalised query into the (exact) base and the quote prefix. */
internal fun parseSearchQuery(normalised: String): MarketQuery {
    val slash = normalised.indexOf('/')
    if (slash < 0) return MarketQuery(base = normalised, quote = null)
    val base = normalised.substring(0, slash)
    val quote = normalised.substring(slash + 1).replace("/", "")
    return MarketQuery(base = base, quote = quote)
}

/** True when `"$base$quote"` starts with the (normalised, slash-free) query. */
internal fun matchesConcatenated(base: String, quote: String, query: String): Boolean =
    query.length > base.length && (base + quote).startsWith(query)
