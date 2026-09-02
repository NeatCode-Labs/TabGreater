package com.neatcode.tabgreater.widget

import com.neatcode.tabgreater.core.model.TGColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget may sit on any of [WidgetConfig.BACKGROUNDS] at any transparency, so its foreground is
 * judged the way WCAG judges one: 4.5:1 for the text that carries the price, 3:1 for the graphics
 * next to it (the badge and the sparkline, which is what the change colour is).
 *
 * The three transparencies are the ends and the middle of the sheet's slider; 0 % is the case that
 * has to stay on the white foreground, because a transparent widget is drawn over the launcher's
 * dark wallpaper, not over its own colour.
 */
class WidgetPaletteTest {

    private val alphas = listOf(1f, 0.5f, 0f)

    /** WCAG AA for text and for graphics; the two thresholds the widget is held to. */
    private val textRatio = 4.5
    private val graphicRatio = 3.0

    @Test
    fun `every background at every transparency stays legible`() {
        for (background in WidgetConfig.BACKGROUNDS) {
            for (alpha in alphas) {
                val scheme = WidgetPalette.of(WidgetConfig.blend(background, alpha))
                val where = "#${hex(background)} at alpha $alpha"
                assertRatio("$where primary", scheme.primary, scheme.background, textRatio)
                assertRatio("$where badge", scheme.badge, scheme.background, graphicRatio)
                assertRatio("$where up", scheme.up, scheme.background, graphicRatio)
                assertRatio("$where down", scheme.down, scheme.background, graphicRatio)
                // The stale variants are drawn as often as the fresh ones — a widget sits faded
                // all night once the live layer has been gone for STALE_AFTER_MS.
                assertRatio("$where stale", scheme.stale, scheme.background, textRatio)
                assertRatio("$where stale up", scheme.staleUp, scheme.background, graphicRatio)
                assertRatio("$where stale down", scheme.staleDown, scheme.background, graphicRatio)
            }
        }
    }

    /**
     * The guard must not quietly turn staleness off: on every background there is still room to
     * fade the price, so "old" always looks different from "live".
     */
    @Test
    fun `a stale price is faded on every background`() {
        for (background in WidgetConfig.BACKGROUNDS) {
            for (alpha in alphas) {
                val scheme = WidgetPalette.of(WidgetConfig.blend(background, alpha))
                val where = "#${hex(background)} at alpha $alpha"
                val faded = WidgetPalette.contrastRatio(scheme.stale, scheme.background)
                val full = WidgetPalette.contrastRatio(scheme.primary, scheme.background)
                assertTrue("$where: stale is $faded:1, as loud as the live $full:1", faded < full)
            }
        }
    }

    @Test
    fun `the dark surfaces keep the widget exactly as it looks today`() {
        val dark = listOf(TGColors.SURFACE, 0xFF000000L, 0xFF0F1A24L)
        for (background in dark) {
            for (alpha in alphas) {
                val scheme = WidgetPalette.of(WidgetConfig.blend(background, alpha))
                val where = "#${hex(background)} at alpha $alpha"
                assertEquals(where, TGColors.TEXT_PRIMARY.toInt(), scheme.primary)
                assertEquals(where, TGColors.TEXT_SECONDARY.toInt(), scheme.secondary)
                assertEquals(where, TGColors.TEXT_TERTIARY.toInt(), scheme.tertiary)
                assertEquals(where, TGColors.UP.toInt(), scheme.up)
                assertEquals(where, TGColors.DOWN.toInt(), scheme.down)
            }
        }
    }

    @Test
    fun `an opaque accent background turns the foreground dark`() {
        for (background in TGColors.ACCENT_PALETTE) {
            val scheme = WidgetPalette.of(WidgetConfig.blend(background, 1f))
            val where = "#${hex(background)}"
            assertEquals(where, WidgetPalette.DARK_PRIMARY, scheme.primary)
            assertEquals(where, WidgetPalette.DARK_SECONDARY, scheme.secondary)
            assertEquals(where, WidgetPalette.DARK_TERTIARY, scheme.tertiary)
            // Green on green and red on salmon are the reason for this change: the palette pair
            // never survives an accent, so neither is what the change ends up drawn in.
            assertTrue(where, scheme.up != TGColors.UP.toInt())
            assertTrue(where, scheme.down != TGColors.DOWN.toInt())
        }
    }

    /**
     * The point of [WidgetPalette.DARK_UP] / [WidgetPalette.DARK_DOWN]: a dark foreground must not
     * cost the widget its up/down cue. They were tuned to clear 3:1 on all eight opaque accents, so
     * this asserts both the choice and the tuning — a palette edit that put one of them under the
     * threshold would keep the fallback honest but should be noticed here.
     */
    @Test
    fun `an opaque accent keeps the up down cue in a deep variant`() {
        for (background in TGColors.ACCENT_PALETTE) {
            val scheme = WidgetPalette.of(WidgetConfig.blend(background, 1f))
            val where = "#${hex(background)}"
            assertRatio("$where deep up", WidgetPalette.DARK_UP, scheme.background, graphicRatio)
            assertRatio("$where deep down", WidgetPalette.DARK_DOWN, scheme.background, graphicRatio)
            assertEquals(where, WidgetPalette.DARK_UP, scheme.up)
            assertEquals(where, WidgetPalette.DARK_DOWN, scheme.down)
        }
    }

    /**
     * The deep variants are only ever reached for; they are never forced. Below their threshold —
     * the band where an accent composited over the scrim is light enough for a dark foreground but
     * too dark for a deep green — the widget falls back to the primary rather than emitting a
     * colour it cannot carry.
     */
    @Test
    fun `a deep variant is never emitted below its threshold`() {
        for (background in WidgetConfig.BACKGROUNDS) {
            for (alpha in alphas) {
                val scheme = WidgetPalette.of(WidgetConfig.blend(background, alpha))
                val where = "#${hex(background)} at alpha $alpha"
                val allowed = mapOf(
                    "up" to listOf(TGColors.UP.toInt(), WidgetPalette.DARK_UP, scheme.primary),
                    "down" to listOf(TGColors.DOWN.toInt(), WidgetPalette.DARK_DOWN, scheme.primary),
                )
                for ((name, ink) in listOf("up" to scheme.up, "down" to scheme.down)) {
                    val choices = allowed.getValue(name)
                    assertTrue("$where $name is #${hex(ink.toLong())}, off the ladder", ink in choices)
                    assertRatio("$where $name", ink, scheme.background, graphicRatio)
                }
            }
        }
    }

    @Test
    fun `a fully transparent widget is judged against the launcher scrim`() {
        val scheme = WidgetPalette.of(WidgetConfig.blend(0xFFFCCD0BL, 0f))
        assertEquals(TGColors.SCRIM.toInt(), scheme.background)
        assertEquals(TGColors.TEXT_PRIMARY.toInt(), scheme.primary)
    }

    @Test
    fun `contrast ratio matches the WCAG anchors`() {
        val white = TGColors.TEXT_PRIMARY.toInt()
        val black = 0xFF000000.toInt()
        assertEquals(21.0, WidgetPalette.contrastRatio(white, black), 0.01)
        assertEquals(21.0, WidgetPalette.contrastRatio(black, white), 0.01)
        assertEquals(1.0, WidgetPalette.contrastRatio(white, white), 0.001)
    }

    @Test
    fun `relative luminance matches the WCAG anchors`() {
        assertEquals(1.0, WidgetPalette.relativeLuminance(TGColors.TEXT_PRIMARY.toInt()), 0.001)
        assertEquals(0.0, WidgetPalette.relativeLuminance(0xFF000000.toInt()), 0.001)
        // Rec. 709 luma of pure green, the coefficient the formula weighs heaviest.
        assertEquals(0.7152, WidgetPalette.relativeLuminance(0xFF00FF00.toInt()), 0.001)
    }

    private fun assertRatio(where: String, foreground: Int, background: Int, min: Double) {
        val ratio = WidgetPalette.contrastRatio(foreground, background)
        assertTrue("$where: #${hex(foreground.toLong())} is $ratio:1, wanted $min:1", ratio >= min)
    }

    private fun hex(argb: Long): String = java.lang.Long.toHexString(argb and 0xFFFFFF).padStart(6, '0')
}
