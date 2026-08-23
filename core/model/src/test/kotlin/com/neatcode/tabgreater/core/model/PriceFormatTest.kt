package com.neatcode.tabgreater.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PriceFormatTest {

    // ── formatPrice ──────────────────────────────────────────────────────────

    @Test
    fun `formats prices at every tile precision`() {
        assertEquals("65,609.70", PriceFormat.formatPrice(65609.7, 2))
        assertEquals("76,751.40", PriceFormat.formatPrice(76751.4, 2))
        assertEquals("2,026.22", PriceFormat.formatPrice(2026.22, 2))
        assertEquals("1.6107", PriceFormat.formatPrice(1.6107, 4))
        assertEquals("1.884", PriceFormat.formatPrice(1.884, 3))
        assertEquals("0.03089", PriceFormat.formatPrice(0.03089, 5))
        assertEquals("11.262", PriceFormat.formatPrice(11.262, 3))
    }

    @Test
    fun `groups every three digits`() {
        assertEquals("0.00", PriceFormat.formatPrice(0.0, 2))
        assertEquals("999", PriceFormat.formatPrice(999.0, 0))
        assertEquals("1,000", PriceFormat.formatPrice(1000.0, 0))
        assertEquals("1,234,568", PriceFormat.formatPrice(1234567.891, 0))
        assertEquals("1,000,000,000.00", PriceFormat.formatPrice(1.0e9, 2))
    }

    @Test
    fun `pads to exactly the requested precision`() {
        assertEquals("5", PriceFormat.formatPrice(5.0, 0))
        assertEquals("5.0000000000", PriceFormat.formatPrice(5.0, 10))
        assertEquals("0.000071501", PriceFormat.formatPrice(0.000071501, 9))
    }

    @Test
    fun `clamps the precision to zero and ten`() {
        assertEquals("1", PriceFormat.formatPrice(1.0, -5))
        assertEquals("1.0000000000", PriceFormat.formatPrice(1.0, 25))
    }

    @Test
    fun `rounds half up`() {
        assertEquals("0.15", PriceFormat.formatPrice(0.145, 2))
        assertEquals("0.13", PriceFormat.formatPrice(0.125, 2))
        assertEquals("2.68", PriceFormat.formatPrice(2.675, 2))
        assertEquals("-0.15", PriceFormat.formatPrice(-0.145, 2))
    }

    @Test
    fun `keeps the minus sign but never renders negative zero`() {
        assertEquals("-1,234.5", PriceFormat.formatPrice(-1234.5, 1))
        assertEquals("0.00", PriceFormat.formatPrice(-0.001, 2))
        assertEquals("0", PriceFormat.formatPrice(-0.4, 0))
    }

    @Test
    fun `returns the placeholder for non finite prices`() {
        assertEquals(PriceFormat.NO_VALUE, PriceFormat.formatPrice(Double.NaN, 2))
        assertEquals(PriceFormat.NO_VALUE, PriceFormat.formatPrice(Double.POSITIVE_INFINITY, 2))
        assertEquals(PriceFormat.NO_VALUE, PriceFormat.formatPrice(Double.NEGATIVE_INFINITY, 2))
    }

    // ── formatChangePct ──────────────────────────────────────────────────────

    @Test
    fun `formats signed percentages with two decimals`() {
        assertEquals("+6.52%", PriceFormat.formatChangePct(6.52))
        assertEquals("+0.28%", PriceFormat.formatChangePct(0.28))
        assertEquals("-3.47%", PriceFormat.formatChangePct(-3.47))
        assertEquals("-0.20%", PriceFormat.formatChangePct(-0.2))
        assertEquals("+7.65%", PriceFormat.formatChangePct(7.6499))
    }

    @Test
    fun `formats zero without a sign`() {
        assertEquals("0.00%", PriceFormat.formatChangePct(0.0))
        assertEquals("0.00%", PriceFormat.formatChangePct(0.004))
        assertEquals("0.00%", PriceFormat.formatChangePct(-0.004))
        assertEquals("+0.01%", PriceFormat.formatChangePct(0.005))
    }

    @Test
    fun `groups large percentages and handles missing values`() {
        assertEquals("+1,234.50%", PriceFormat.formatChangePct(1234.5))
        assertEquals(PriceFormat.NO_VALUE, PriceFormat.formatChangePct(null))
        assertEquals(PriceFormat.NO_VALUE, PriceFormat.formatChangePct(Double.NaN))
    }

    // ── formatSignedAbs ──────────────────────────────────────────────────────

    @Test
    fun `formats the absolute changes shown on tiles`() {
        assertEquals("+1.10", PriceFormat.formatSignedAbs(1.1, 2))
        assertEquals("-132.87", PriceFormat.formatSignedAbs(-132.87, 2))
        assertEquals("-159.0", PriceFormat.formatSignedAbs(-159.0, 1))
        assertEquals("+0.000301", PriceFormat.formatSignedAbs(0.000301, 6))
        assertEquals("+2.920", PriceFormat.formatSignedAbs(2.92, 3))
    }

    @Test
    fun `formats an absolute change of zero without a sign`() {
        assertEquals("0.00", PriceFormat.formatSignedAbs(0.0, 2))
        assertEquals("0.00", PriceFormat.formatSignedAbs(-0.004, 2))
        assertEquals("0.00", PriceFormat.formatSignedAbs(0.004, 2))
        assertEquals("+0.01", PriceFormat.formatSignedAbs(0.005, 2))
    }

    @Test
    fun `groups and clamps absolute changes`() {
        assertEquals("+1,234.50", PriceFormat.formatSignedAbs(1234.5, 2))
        assertEquals("-1,000,000", PriceFormat.formatSignedAbs(-1.0e6, 0))
        assertEquals("+1.0000000000", PriceFormat.formatSignedAbs(1.0, 25))
        assertEquals(PriceFormat.NO_VALUE, PriceFormat.formatSignedAbs(Double.NaN, 2))
    }

    // ── formatVolume ─────────────────────────────────────────────────────────

    @Test
    fun `formats the volumes shown on tiles`() {
        assertEquals("713M", PriceFormat.formatVolume(713_000_000.0))
        assertEquals("52K", PriceFormat.formatVolume(52_000.0))
        assertEquals("1M", PriceFormat.formatVolume(1_000_000.0))
        assertEquals("262K", PriceFormat.formatVolume(262_000.0))
        assertEquals("30K", PriceFormat.formatVolume(30_000.0))
        assertEquals("56K", PriceFormat.formatVolume(56_000.0))
    }

    @Test
    fun `keeps one decimal below ten and drops it when it is zero`() {
        assertEquals("1.5M", PriceFormat.formatVolume(1_500_000.0))
        assertEquals("9.8M", PriceFormat.formatVolume(9_812_000.0))
        assertEquals("10M", PriceFormat.formatVolume(9_960_000.0))
        assertEquals("1K", PriceFormat.formatVolume(1_000.0))
        assertEquals("2.3B", PriceFormat.formatVolume(2.3e9))
        assertEquals("1.2T", PriceFormat.formatVolume(1.15e12))
    }

    @Test
    fun `promotes to the next unit when rounding carries`() {
        assertEquals("1M", PriceFormat.formatVolume(999_999.6))
        assertEquals("1B", PriceFormat.formatVolume(999_999_999.0))
    }

    @Test
    fun `keeps two decimals below one thousand`() {
        assertEquals("262.50", PriceFormat.formatVolume(262.5))
        assertEquals("0.00", PriceFormat.formatVolume(0.0))
        assertEquals("999.99", PriceFormat.formatVolume(999.99))
        assertEquals(PriceFormat.NO_VALUE, PriceFormat.formatVolume(Double.NaN))
    }

    // ── formatCompact ────────────────────────────────────────────────────────

    @Test
    fun `formats compact volumes`() {
        assertEquals("999.00", PriceFormat.formatCompact(999.0))
        assertEquals("1.00K", PriceFormat.formatCompact(1000.0))
        assertEquals("31.67K", PriceFormat.formatCompact(31665.9))
        assertEquals("9.81M", PriceFormat.formatCompact(9_812_345.0))
        assertEquals("2.30B", PriceFormat.formatCompact(2_300_000_000.0))
        assertEquals("1.15T", PriceFormat.formatCompact(1.15e12))
        assertEquals("-4.50M", PriceFormat.formatCompact(-4_500_000.0))
        assertEquals("1.2K", PriceFormat.formatCompact(1234.0, 1))
        // Rounding at a unit boundary promotes to the next suffix instead of "1,000.00K".
        assertEquals("1.00M", PriceFormat.formatCompact(999_999.4))
        assertEquals("1.00K", PriceFormat.formatCompact(999.999))
        assertEquals("1.00T", PriceFormat.formatCompact(999_999_999_999.5))
        assertEquals("999.99K", PriceFormat.formatCompact(999_994.0))
    }

    // ── shrinkZeros ──────────────────────────────────────────────────────────

    @Test
    fun `shrinks the zero run of a sub-cent price`() {
        // 0.0₃71501 == 0.000071501 (four zeros after the point, one of them inside the prefix)
        assertEquals(ShrunkPrice("0.0", 4, "71501"), PriceFormat.shrinkZeros("0.000071501"))
    }

    @Test
    fun `shrinks from three zeros onwards`() {
        assertEquals(ShrunkPrice("0.0", 3, "123"), PriceFormat.shrinkZeros("0.000123"))
        assertEquals(ShrunkPrice("0.0", 4, "124"), PriceFormat.shrinkZeros("0.0000124"))
        assertEquals(ShrunkPrice("0.0", 9, "5"), PriceFormat.shrinkZeros("0.0000000005"))
    }

    @Test
    fun `leaves short zero runs untouched`() {
        assertEquals(ShrunkPrice("0.00123", null, ""), PriceFormat.shrinkZeros("0.00123"))
        assertEquals(ShrunkPrice("0.03089", null, ""), PriceFormat.shrinkZeros("0.03089"))
        assertEquals(ShrunkPrice("0.7515", null, ""), PriceFormat.shrinkZeros("0.7515"))
    }

    @Test
    fun `leaves everything that is not a leading zero fraction untouched`() {
        assertEquals(ShrunkPrice("65,609.70", null, ""), PriceFormat.shrinkZeros("65,609.70"))
        assertEquals(ShrunkPrice("1,000", null, ""), PriceFormat.shrinkZeros("1,000"))
        assertEquals(ShrunkPrice("10.0001", null, ""), PriceFormat.shrinkZeros("10.0001"))
        assertEquals(ShrunkPrice(PriceFormat.NO_VALUE, null, ""), PriceFormat.shrinkZeros(PriceFormat.NO_VALUE))
        // no significant digit left after the zero run
        assertEquals(ShrunkPrice("0.0000", null, ""), PriceFormat.shrinkZeros("0.0000"))
    }

    @Test
    fun `keeps the sign when shrinking`() {
        assertEquals(ShrunkPrice("-0.0", 4, "123"), PriceFormat.shrinkZeros("-0.0000123"))
    }

    @Test
    fun `honours a custom threshold`() {
        assertEquals(ShrunkPrice("0.0", 2, "12"), PriceFormat.shrinkZeros("0.0012", minZeros = 2))
        assertEquals(ShrunkPrice("0.000123", null, ""), PriceFormat.shrinkZeros("0.000123", minZeros = 4))
        assertEquals(ShrunkPrice("0.000123", null, ""), PriceFormat.shrinkZeros("0.000123", minZeros = 0))
    }

    @Test
    fun `plain rebuilds the original string`() {
        for (input in listOf("0.000071501", "0.00123", "-0.0000123", "65,609.70", "0.0000")) {
            assertEquals(input, PriceFormat.shrinkZeros(input).plain)
        }
    }

    @Test
    fun `formatting and shrinking compose`() {
        val shrunk = PriceFormat.shrinkZeros(PriceFormat.formatPrice(0.000071501, 9))
        assertEquals(ShrunkPrice("0.0", 4, "71501"), shrunk)
    }
}
