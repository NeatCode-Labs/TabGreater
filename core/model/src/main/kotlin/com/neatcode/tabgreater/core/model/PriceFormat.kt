package com.neatcode.tabgreater.core.model

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * Result of [PriceFormat.shrinkZeros] — a price split so the UI can render the
 * "leading-zero compression" (`0.0₃71501` = `0.000071501`).
 *
 * When [zeroCount] is `null` the price needs no compression and [prefix] already holds the
 * complete string ([rest] is then empty).
 *
 * @property prefix `"0.0"` (or `"-0.0"`) when compressed, otherwise the full formatted price.
 * @property zeroCount digits to render as a subscript: the **total** number of zeros after the
 *   decimal point (the usual crypto convention: `0.0₄124` = `0.0000124`). The literal `0`
 *   shown in [prefix] is part of that count. `null` = no compression.
 * @property rest the significant digits that follow the zero run.
 */
data class ShrunkPrice(
    val prefix: String,
    val zeroCount: Int?,
    val rest: String,
) {
    /** The uncompressed string this instance represents (round-trip of [PriceFormat.shrinkZeros]). */
    val plain: String
        get() = if (zeroCount == null) prefix else prefix.dropLast(1) + "0".repeat(zeroCount) + rest
}

/**
 * en-US number formatting for tiles, chart headers and widgets — deliberately locale-independent
 * so every device prints the same grouping and separators (`65,609.70`, `+6.52%`).
 *
 * Pure Kotlin/JVM: no `java.text` and no `Locale`, so results are identical on every device.
 */
object PriceFormat {

    /** Highest number of decimals any exchange precision is clamped to. */
    const val MAX_PRECISION: Int = 10

    /** Placeholder shown when a value is unknown or not finite (em dash). */
    const val NO_VALUE: String = "—"

    private const val GROUP_SEPARATOR = ','
    private val THOUSAND = BigDecimal(1000)
    private val COMPACT_UNITS = listOf(1e12 to "T", 1e9 to "B", 1e6 to "M", 1e3 to "K")
    private const val DECIMAL_SEPARATOR = '.'

    /**
     * Formats [value] with exactly [precision] decimals (clamped to `0..`[MAX_PRECISION]),
     * `,` as the thousands separator and `.` as the decimal separator, rounding HALF_UP.
     *
     * A value that rounds to zero never keeps a negative sign (`-0.001` at 2 decimals is `0.00`).
     */
    fun formatPrice(value: Double, precision: Int): String {
        if (!value.isFinite()) return NO_VALUE
        val scale = precision.coerceIn(0, MAX_PRECISION)
        val rounded = BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP)
        val plain = rounded.abs().toPlainString()
        val negative = rounded.signum() < 0
        return if (negative) "-" + group(plain) else group(plain)
    }

    /**
     * Formats a signed percentage with 2 decimals: `+6.52%`, `-3.47%`.
     * A value that rounds to zero is rendered without a sign (`0.00%`); `null` gives [NO_VALUE].
     */
    fun formatChangePct(pct: Double?): String {
        if (pct == null || !pct.isFinite()) return NO_VALUE
        val rounded = BigDecimal.valueOf(pct).setScale(2, RoundingMode.HALF_UP)
        val body = group(rounded.abs().toPlainString())
        val sign = when {
            rounded.signum() > 0 -> "+"
            rounded.signum() < 0 -> "-"
            else -> ""
        }
        return "$sign$body%"
    }

    /**
     * Formats a signed absolute change with [precision] decimals: `+1.10`, `-132.87`.
     * A value that rounds to zero drops the sign (`0.00`); a non-finite value gives [NO_VALUE].
     *
     * This is the first half of the Compact/Medium/Large tiles' `+1.10 (0.04%)` line.
     */
    fun formatSignedAbs(delta: Double, precision: Int): String {
        if (!delta.isFinite()) return NO_VALUE
        val scale = precision.coerceIn(0, MAX_PRECISION)
        val rounded = BigDecimal.valueOf(delta).setScale(scale, RoundingMode.HALF_UP)
        val body = group(rounded.abs().toPlainString())
        val sign = when {
            rounded.signum() > 0 -> "+"
            rounded.signum() < 0 -> "-"
            else -> ""
        }
        return "$sign$body"
    }

    /**
     * The tile volume notation: `713M`, `52K`, `1.5M`, `262K`, `30K`.
     *
     * From 1,000 upwards the value is scaled to K/M/B/T and rendered with **no** decimals once the
     * scaled value reaches 10, otherwise with one decimal whose trailing `.0` is dropped
     * (`1,000,000` → `1M`, `1,500,000` → `1.5M`). Below 1,000 the plain two-decimal price form is
     * used (`262.50`), so small base volumes stay readable.
     */
    fun formatVolume(volume: Double): String {
        if (!volume.isFinite()) return NO_VALUE
        val magnitude = abs(volume)
        if (magnitude < 1000.0) return formatPrice(volume, 2)
        var unit = COMPACT_UNITS.indexOfFirst { magnitude >= it.first }
        while (true) {
            val (divisor, suffix) = COMPACT_UNITS[unit]
            val raw = volume / divisor
            val decimals = if (abs(raw) >= 10.0) 0 else 1
            val scaled = BigDecimal.valueOf(raw).setScale(decimals, RoundingMode.HALF_UP)
            // 999,999.6 / 1e3 rounds to 1,000.0K: promote to the next unit instead.
            if (scaled.abs() >= THOUSAND && unit > 0) { unit--; continue }
            return formatPrice(scaled.toDouble(), decimals).removeSuffix(".0") + suffix
        }
    }

    /**
     * Compact notation for large numbers (volumes): `31,665.90`, `1.24K`, `9.81M`, `2.30B`, `1.15T`.
     * Values below 1,000 are formatted exactly like [formatPrice].
     */
    fun formatCompact(value: Double, precision: Int = 2): String {
        if (!value.isFinite()) return NO_VALUE
        val magnitude = abs(value)
        var unit = COMPACT_UNITS.indexOfFirst { magnitude >= it.first }.let { if (it < 0) COMPACT_UNITS.size else it }
        if (unit == COMPACT_UNITS.size) {
            // Below 1,000 — unless rounding to `precision` decimals carries it up to 1,000.
            val rounded = BigDecimal(value).setScale(precision.coerceIn(0, MAX_PRECISION), RoundingMode.HALF_UP)
            if (rounded.abs() < THOUSAND) return formatPrice(value, precision)
            unit = COMPACT_UNITS.size - 1
        }
        while (true) {
            val (divisor, suffix) = COMPACT_UNITS[unit]
            val scaled = BigDecimal(value / divisor).setScale(precision.coerceIn(0, MAX_PRECISION), RoundingMode.HALF_UP)
            // 999,999.6 / 1e3 rounds to 1,000.00K: promote to the next unit instead.
            if (scaled.abs() >= THOUSAND && unit > 0) { unit--; continue }
            return formatPrice(scaled.toDouble(), precision) + suffix
        }
    }
    /**
     * Splits an already formatted price for the leading-zero compression.
     *
     * `0.0000124` has four zeros after the point, so it renders as `0.0` + subscript `4` +
     * `124` = `0.0₄124`, the way a low-priced pair such as EOS/BTC reads on a tile. Compression
     * only kicks in from [minZeros] zeros onwards, so `0.00123` stays as it is.
     *
     * Anything that is not a `0.…` fraction (grouped prices, integers, [NO_VALUE]) is returned
     * unchanged with a `null` [ShrunkPrice.zeroCount].
     */
    fun shrinkZeros(formatted: String, minZeros: Int = 3): ShrunkPrice {
        val unchanged = ShrunkPrice(formatted, null, "")
        if (minZeros < 1) return unchanged

        val negative = formatted.startsWith("-")
        val body = if (negative) formatted.substring(1) else formatted
        val dot = body.indexOf(DECIMAL_SEPARATOR)
        if (dot < 0 || body.substring(0, dot) != "0") return unchanged

        val fraction = body.substring(dot + 1)
        var zeros = 0
        while (zeros < fraction.length && fraction[zeros] == '0') zeros++
        if (zeros < minZeros) return unchanged

        val rest = fraction.substring(zeros)
        if (rest.isEmpty()) return unchanged

        return ShrunkPrice(prefix = if (negative) "-0.0" else "0.0", zeroCount = zeros, rest = rest)
    }

    /** Inserts `,` every three digits into the integer part of an unsigned plain decimal string. */
    private fun group(plain: String): String {
        val dot = plain.indexOf(DECIMAL_SEPARATOR)
        val integer = if (dot < 0) plain else plain.substring(0, dot)
        val fraction = if (dot < 0) "" else plain.substring(dot)
        if (integer.length <= 3) return integer + fraction

        val out = StringBuilder(integer.length + integer.length / 3 + fraction.length)
        for (i in integer.indices) {
            if (i > 0 && (integer.length - i) % 3 == 0) out.append(GROUP_SEPARATOR)
            out.append(integer[i])
        }
        out.append(fraction)
        return out.toString()
    }
}
