package com.neatcode.tabgreater.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutTest {

    // The owner's pair: a five-digit price with two decimals and a signed 24 h percentage.
    private val exchange = "BINANCE"
    private val monogram = "BN"
    private val pair = "BTC/USDT"
    private val price = "77,362.58"
    private val change = "+0.02%"

    private fun plan(
        slotWidth: Float,
        slotHeight: Float,
        pairName: String = pair,
        priceText: String = price,
        sparkline: Boolean = true,
        fontScale: Float = 1f,
    ): WidgetPlan = widgetPlan(
        innerWidthDp = slotWidth - 2 * horizontalPaddingDp(slotHeight),
        innerHeightDp = slotHeight - 2 * verticalPaddingDp(slotHeight),
        pair = pairName,
        price = priceText,
        change = change,
        hasBadge = true,
        hasSparkline = sparkline,
        fontScale = fontScale,
    )

    /** What the two text lines really need at a plan's scale, in dp. */
    private fun headerNeed(p: WidgetPlan, pairName: String = pair, fontScale: Float = 1f): Float =
        textWidthDp(pairName, p.pairSp, fontScale) +
            if (p.showBadge) p.headerGapDp + p.badgeDp else 0f

    private fun priceNeed(p: WidgetPlan, priceText: String = price, fontScale: Float = 1f): Float =
        textWidthDp(priceText, p.priceSp, fontScale) + p.priceGapDp +
            textWidthDp(change, p.changeSp, fontScale)

    private fun textHeight(p: WidgetPlan, fontScale: Float = 1f): Float =
        (p.pairSp + p.priceSp + if (p.showMeta) p.metaSp else 0f) * LINE_FACTOR * fontScale +
            if (p.hasBand) 2 * p.bandGapDp else 0f

    // ---- Padding --------------------------------------------------------------------------------

    @Test
    fun `padding grows with the widget and is clamped at both ends`() {
        // A one-cell row: enough air that nothing sits on the rounded corner, not so much that the
        // chart starves.
        assertEquals(6.9f, verticalPaddingDp(92f), 0.01f)
        assertEquals(8.9f, horizontalPaddingDp(92f), 0.01f)
        // Floor and ceiling.
        assertEquals(6f, verticalPaddingDp(40f), EPS)
        assertEquals(12f, verticalPaddingDp(400f), EPS)
        assertEquals(14f, horizontalPaddingDp(400f), EPS)
        // The sides always get more than the top and bottom.
        for (h in 40..400 step 10) {
            assertTrue(horizontalPaddingDp(h.toFloat()) > verticalPaddingDp(h.toFloat()))
        }
    }

    /** The old widget put content 4 dp from the edge; every size now gets at least half again. */
    @Test
    fun `every slot gets more breathing room than the old flat four dp`() {
        for (h in 60..400 step 10) {
            assertTrue("h=$h", verticalPaddingDp(h.toFloat()) >= 6f)
            assertTrue("h=$h", horizontalPaddingDp(h.toFloat()) >= 8f)
        }
    }

    // ---- The slot -------------------------------------------------------------------------------

    /** `SizeMode.Exact` hands out a zero size until the host has measured the widget. */
    @Test
    fun `an unmeasured slot falls back to a two by one row`() {
        assertEquals(FALLBACK_SLOT, slotOf(DpSize(0.dp, 0.dp)))
        assertEquals(FALLBACK_SLOT, slotOf(DpSize(160.dp, 0.dp)))
        assertEquals(FALLBACK_SLOT, slotOf(DpSize.Zero))
        assertEquals(DpSize(240.dp, 130.dp), slotOf(DpSize(240.dp, 130.dp)))
    }

    // ---- Hosts that draw smaller than they measure --------------------------------------------------

    /**
     * One UI 8.5 reports a 2 × 1 as 150 × 91.7 dp and draws it in 124.8 × 76.4 — the
     * `hsResizeRatio` it puts in the options bundle. Without the correction the sparkline, the
     * only weighted child, pays the whole difference.
     */
    @Test
    fun `a one ui slot is corrected to the box the launcher really draws`() {
        val reported = DpSize(150.04445.dp, 91.73333.dp)
        val drawn = drawnSlotOf(reported, hostDrawRatio(0.8333333f))
        assertEquals(125.0f, drawn.width.value, 0.2f)
        assertEquals(76.4f, drawn.height.value, 0.2f)
    }

    @Test
    fun `a host that draws what it measures is left alone`() {
        val reported = DpSize(152.7.dp, 89.7.dp)
        assertEquals(reported, drawnSlotOf(reported, 1f))
        // The key is Samsung's; every other launcher simply has no such entry.
        assertEquals(1f, hostDrawRatio(null), EPS)
        assertEquals(1f, hostDrawRatio("nonsense"), EPS)
        assertEquals(1f, hostDrawRatio(Float.NaN), EPS)
        assertEquals(1f, hostDrawRatio(1.4f), EPS) // a host cannot draw bigger than it measures
        assertEquals(1f, hostDrawRatio(0.2f), EPS) // nor a fifth of it; that is a misread key
        assertEquals(0.8333333f, hostDrawRatio(0.8333333), EPS) // Double as well as Float
    }

    /** An unmeasured slot still falls back before the correction is applied. */
    @Test
    fun `the fallback slot survives a shrinking host`() {
        val drawn = drawnSlotOf(DpSize.Zero, 0.8333333f)
        assertEquals(FALLBACK_SLOT.width.value * 0.8333333f, drawn.width.value, 0.1f)
    }

    /** With the correction the One UI 2 × 1 gets the same picture the emulator's Pixel 2 × 1 does. */
    @Test
    fun `the corrected one ui slot keeps the chart's share and the gap before the change`() {
        val drawn = drawnSlotOf(DpSize(150.04445.dp, 91.73333.dp), 0.8333333f)
        val slotW = drawn.width.value
        val slotH = drawn.height.value
        val p = plan(slotW, slotH, priceText = "2,448.33")
        val innerW = slotW - 2 * horizontalPaddingDp(slotH)
        val innerH = slotH - 2 * verticalPaddingDp(slotH)
        assertTrue("band " + p.bandHeightDp + " of " + innerH, p.bandHeightDp >= innerH * 0.35f)
        // Real slack before the percentage, so it sits at the right edge instead of hugging the price.
        val slack = innerW - priceNeed(p, "2,448.33")
        assertTrue("slack " + slack, slack >= 10f)
    }

    // ---- The owner's phone ------------------------------------------------------------------------

    /**
     * The slot the owner photographed: a 2 × 1 on a five-column One UI grid. It used to ellipsise
     * `BTC/USDT` into `BTC/US…`; it now keeps the exchange, the whole pair and a chart worth
     * looking at, at essentially the reference scale.
     */
    @Test
    fun `a one ui two by one draws the reference design with the pair intact`() {
        val slotW = 153f
        val slotH = 92f
        val p = plan(slotW, slotH)
        val innerW = slotW - 2 * horizontalPaddingDp(slotH)
        val innerH = slotH - 2 * verticalPaddingDp(slotH)

        assertTrue("scale " + p.scale, p.scale in 0.95f..1.05f)
        assertTrue("badge kept", p.showBadge)
        assertFalse("no room for the meta line", p.showMeta)
        assertTrue("header " + headerNeed(p) + " of " + innerW, headerNeed(p) <= innerW)
        assertTrue("price " + priceNeed(p) + " of " + innerW, priceNeed(p) <= innerW)
        assertTrue("band " + p.bandHeightDp, p.bandHeightDp >= innerH * 0.35f)
    }

    /** The same widget on a Pixel's four-column grid: a different slot, the same picture. */
    @Test
    fun `a pixel two by one is the same design at a slightly different scale`() {
        val oneUi = plan(153f, 92f)
        val pixel = plan(152.7f, 89.7f)
        assertEquals(oneUi.showBadge, pixel.showBadge)
        assertEquals(oneUi.showMeta, pixel.showMeta)
        assertEquals("within 6 %", oneUi.scale, pixel.scale, 0.06f)
        // Same proportions: every size is the same multiple of the scale.
        assertEquals(REF_PRICE_SP / REF_PAIR_SP, pixel.priceSp / pixel.pairSp, EPS)
        assertEquals(REF_CHANGE_SP / REF_PAIR_SP, pixel.changeSp / pixel.pairSp, EPS)
    }

    // ---- Scaling behaviour ------------------------------------------------------------------------

    /**
     * The whole point of the rewrite: widening a widget must not restyle it. A 2 × 1, a 3 × 1 and
     * a 4 × 1 on one grid have the same height, so they get the same type — only the chart grows.
     */
    @Test
    fun `widening a row keeps every font and grows only the chart`() {
        val two = plan(153f, 92f)
        val three = plan(230f, 92f)
        val four = plan(307f, 92f)
        for (wider in listOf(three, four)) {
            assertEquals(two.scale, wider.scale, EPS)
            assertEquals(two.priceSp, wider.priceSp, EPS)
            assertEquals(two.pairSp, wider.pairSp, EPS)
            assertEquals(two.bandHeightDp, wider.bandHeightDp, EPS)
            assertTrue(wider.showBadge)
        }
    }

    /** Every size in the plan is one design multiplied by one scale — nothing steps on its own. */
    @Test
    fun `every font is the reference design times the scale`() {
        for (w in 110..420 step 10) {
            for (h in 60..320 step 20) {
                val p = plan(w.toFloat(), h.toFloat())
                assertEquals(REF_PAIR_SP * p.scale, p.pairSp, EPS)
                assertEquals(REF_PRICE_SP * p.scale, p.priceSp, EPS)
                assertEquals(REF_CHANGE_SP * p.scale, p.changeSp, EPS)
                assertEquals(REF_META_SP * p.scale, p.metaSp, EPS)
                assertEquals(REF_BADGE_DP * p.scale, p.badgeDp, EPS)
                assertEquals(REF_HEADER_GAP_DP * p.scale, p.headerGapDp, EPS)
                assertEquals(REF_PRICE_GAP_DP * p.scale, p.priceGapDp, EPS)
            }
        }
    }

    @Test
    fun `a taller widget scales up until the ceiling and then only the chart grows`() {
        val small = plan(153f, 92f)
        val medium = plan(153f, 130f)
        val huge = plan(321f, 400f)
        assertTrue(medium.scale > small.scale)
        assertEquals(SCALE_MAX, huge.scale, EPS)
        // Past the ceiling every extra dp is chart.
        val taller = plan(321f, 460f)
        assertEquals(huge.scale, taller.scale, EPS)
        assertTrue(taller.bandHeightDp > huge.bandHeightDp + 50f)
    }

    @Test
    fun `the scale never leaves its band`() {
        for (w in 100..500 step 7) {
            for (h in 40..420 step 11) {
                val p = plan(w.toFloat(), h.toFloat())
                assertTrue("$w x $h -> " + p.scale, p.scale >= SCALE_MIN - EPS && p.scale <= SCALE_MAX + EPS)
            }
        }
    }

    // ---- Nothing is ever clipped --------------------------------------------------------------------

    /**
     * The guarantee the old font ladders could not make: across every slot the provider allows,
     * both text lines fit the inner width and the text plus the chart fit the inner height.
     */
    @Test
    fun `text always fits the slot it was planned for`() {
        for (w in 110..460 step 5) {
            for (h in 50..400 step 10) {
                val slotW = w.toFloat()
                val slotH = h.toFloat()
                val innerW = slotW - 2 * horizontalPaddingDp(slotH)
                val innerH = slotH - 2 * verticalPaddingDp(slotH)
                val p = plan(slotW, slotH)
                assertTrue("header at $w x $h: " + headerNeed(p) + " of " + innerW, headerNeed(p) <= innerW + 0.1f)
                assertTrue("price at $w x $h: " + priceNeed(p) + " of " + innerW, priceNeed(p) <= innerW + 0.1f)
                assertTrue(
                    "height at $w x $h: " + (textHeight(p) + p.bandHeightDp) + " of " + innerH,
                    textHeight(p) + p.bandHeightDp <= innerH + 0.1f,
                )
            }
        }
    }

    /** Long pairs are what ellipsised before; now they shrink the design a little instead. */
    @Test
    fun `a long pair shrinks the widget instead of losing its tail`() {
        val long = plan(153f, 92f, pairName = "1000PEPE/USDT")
        val normal = plan(153f, 92f)
        val innerW = 153f - 2 * horizontalPaddingDp(92f)
        assertTrue(long.scale <= normal.scale)
        assertTrue(
            "header " + headerNeed(long, "1000PEPE/USDT"),
            headerNeed(long, "1000PEPE/USDT") <= innerW + 0.1f,
        )
    }

    /** A shrink-zero price is long, and it must not push the percentage off the widget either. */
    @Test
    fun `a shrink zero price still leaves room for the percentage`() {
        val p = plan(153f, 92f, priceText = "0.0₄12345")
        assertTrue(priceNeed(p, "0.0₄12345") <= 153f - 2 * horizontalPaddingDp(92f) + 0.1f)
    }

    /** Only when the width is hopeless does the badge go — never the pair. */
    @Test
    fun `a hopeless width drops the badge rather than the pair`() {
        val p = plan(102f, 92f, pairName = "1000CHEEMS/USDT")
        assertFalse(p.showBadge)
        assertTrue(headerNeed(p, "1000CHEEMS/USDT") <= 102f - 2 * horizontalPaddingDp(92f) + 0.1f)
    }

    /**
     * The badge is what the spelled-out exchange used to cost: `BINANCE` at 8 sp was ~39 dp of a
     * ~135 dp header, the square is 12 dp. That is the room a long pair now has.
     */
    @Test
    fun `the badge costs a third of what the exchange name did`() {
        val name = textWidthDp(exchange, 8f) + REF_HEADER_GAP_DP
        val badge = REF_BADGE_DP + REF_HEADER_GAP_DP
        assertTrue("name " + name + " vs badge " + badge, badge < name / 2f)
        assertEquals(2, monogram.length)
    }

    // ---- Accessibility --------------------------------------------------------------------------

    /** A large system font is honoured; the design shrinks around it instead of overflowing. */
    @Test
    fun `a large system font shrinks the design instead of clipping it`() {
        val normal = plan(153f, 92f)
        val big = plan(153f, 92f, fontScale = 1.4f)
        val innerW = 153f - 2 * horizontalPaddingDp(92f)
        val innerH = 92f - 2 * verticalPaddingDp(92f)
        assertTrue("scale " + big.scale, big.scale < normal.scale)
        assertTrue(headerNeed(big, fontScale = 1.4f) <= innerW + 0.1f)
        assertTrue(priceNeed(big, fontScale = 1.4f) <= innerW + 0.1f)
        assertTrue(textHeight(big, 1.4f) + big.bandHeightDp <= innerH + 0.1f)
    }

    // ---- The meta line and the band ----------------------------------------------------------------

    @Test
    fun `only a tall widget carries the updated line`() {
        assertFalse(plan(153f, 92f).showMeta)
        assertFalse(plan(307f, 92f).showMeta)
        assertTrue(plan(153f, 195f).showMeta)
        assertTrue(plan(321f, 195f).showMeta)
    }

    @Test
    fun `a widget too short for a chart drops the band instead of the price`() {
        val p = plan(153f, 44f)
        assertFalse(p.hasBand)
        assertEquals(0f, p.bandHeightDp, EPS)
        assertTrue("the two lines still fit", textHeight(p) <= 44f - 2 * verticalPaddingDp(44f) + 0.1f)
    }

    @Test
    fun `switching the sparkline off gives the text the whole widget`() {
        val withChart = plan(153f, 92f)
        val without = plan(153f, 92f, sparkline = false)
        assertFalse(without.hasBand)
        assertTrue(without.scale >= withChart.scale)
    }

    /** The chart keeps roughly the same share of every widget it is drawn on. */
    @Test
    fun `the chart share stays in the same band across sizes`() {
        for (h in 80..300 step 20) {
            val slotH = h.toFloat()
            val innerH = slotH - 2 * verticalPaddingDp(slotH)
            val p = plan(200f, slotH)
            val share = p.bandHeightDp / innerH
            assertTrue("h=$h share=$share", share >= 0.35f && share <= 0.80f)
        }
    }

    // ---- Text estimate ------------------------------------------------------------------------

    @Test
    fun `the estimate charges capitals more than the flat rate and digits their real advance`() {
        assertEquals(0f, textWidthDp("", 20f), EPS)
        assertTrue("capitals", textWidthDp(exchange, 10f) > 7 * EM_PER_CHAR * 10f)
        // 7 digits + a comma + a period, not 9 flat characters.
        assertTrue(textWidthDp(price, 16f) < 9 * EM_PER_CHAR * 16f)
        assertTrue(textWidthDp(price, 16f) > 7 * 0.5f * 16f)
        // The font scale is a straight multiplier.
        assertEquals(2f * textWidthDp(pair, 13f), textWidthDp(pair, 13f, 2f), EPS)
    }

    /** Shrink-zero prices (`0.0₄123`) are mostly subscripts, which are narrower still. */
    @Test
    fun `a shrink zero price is estimated narrower than the same digits full size`() {
        assertTrue(textWidthDp("0.0₄123", 16f) < textWidthDp("0.04123", 16f) + EPS)
    }

    @Test
    fun `the two line widths are the sum of their parts`() {
        assertEquals(
            REF_BADGE_DP + REF_HEADER_GAP_DP + textWidthDp(pair, REF_PAIR_SP),
            headerRefWidthDp(true, pair, 1f),
            EPS,
        )
        assertEquals(textWidthDp(pair, REF_PAIR_SP), headerRefWidthDp(false, pair, 1f), EPS)
        // The badge is a bitmap: a bigger system font grows the pair, never the square.
        assertEquals(
            REF_BADGE_DP + REF_HEADER_GAP_DP + textWidthDp(pair, REF_PAIR_SP, 1.4f),
            headerRefWidthDp(true, pair, 1.4f),
            EPS,
        )
        assertEquals(
            textWidthDp(price, REF_PRICE_SP) + REF_PRICE_GAP_DP + textWidthDp(change, REF_CHANGE_SP),
            priceRefWidthDp(price, change, 1f),
            EPS,
        )
    }

    // ---- Misc ---------------------------------------------------------------------------------

    @Test
    fun `the sparkline stroke is two pixels on a dense screen and one and a half elsewhere`() {
        assertEquals(1.5f, sparkStrokePx(1f), EPS)
        assertEquals(1.5f, sparkStrokePx(2f), EPS)
        assertEquals(2f, sparkStrokePx(2.5f), EPS)
        assertEquals(2f, sparkStrokePx(3f), EPS) // 480 dpi emulator
    }

    private companion object {
        const val EPS = 0.001f
    }
}
