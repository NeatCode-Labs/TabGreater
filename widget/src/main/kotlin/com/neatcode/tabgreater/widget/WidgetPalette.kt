package com.neatcode.tabgreater.widget

import com.neatcode.tabgreater.core.model.TGColors
import kotlin.math.pow

/**
 * The foreground one widget draws with, on the background that widget was configured with.
 *
 * Every colour here has already been through the contrast test in [WidgetPalette]: [primary] is
 * whichever of white / near-black reads better on [background], and the rest fall back to it when
 * their own colour would not.
 *
 * @property background the *effective* background — the user's colour already composited over the
 *   launcher ([WidgetPalette.onScrim]) — which is what every ratio is measured against.
 * @property tertiary the quietest ink: the "Updated hh:mm:ss" line and the "↻" glyph. It is held to
 *   the 3:1 graphics threshold rather than to a text one — which is exactly the ratio
 *   [TGColors.TEXT_TERTIARY] has always had on [TGColors.SURFACE] (3.36:1), so the dark surfaces
 *   keep the hierarchy they had and the accents get the same one.
 * @property badge tint of the exchange monogram and its hairline frame — [tertiary], on a graphic
 *   that is a frame rather than text.
 * @property up colour of a gain (the change text and the sparkline): the palette green, or
 *   [WidgetPalette.DARK_UP] where a light background cannot carry it, or [primary] where neither
 *   stands out from the background — the sign in `+6.52%` still carries the meaning.
 * @property stale [primary] faded to say the snapshot is old ([WidgetModelFactory.STALE_AFTER_MS]),
 *   and [staleUp] / [staleDown] the same fade of [up] / [down]. Opaque, and no dimmer than the
 *   threshold of the thing they colour, because fading a dark foreground moves it *towards* a light
 *   background rather than away from it ([WidgetPalette.dimmed]).
 */
internal data class WidgetScheme(
    val background: Int,
    val primary: Int,
    val secondary: Int,
    val tertiary: Int,
    val badge: Int,
    val up: Int,
    val down: Int,
    val stale: Int,
    val staleUp: Int,
    val staleDown: Int,
)

/**
 * Which foreground a widget background can carry, decided by WCAG contrast maths rather than by
 * eye.
 *
 * The widget's background is the user's choice out of [WidgetConfig.BACKGROUNDS]: three dark
 * surfaces and the eight accent hues. The fixed dark-theme foreground the widget used to draw with
 * — white text, a grey badge, a green or red sparkline — is unreadable on most of those: white on
 * `#FCCD0B` is 1.6:1, and a green line on the green background is worse still.
 *
 * The maths is plain sRGB, on ARGB ints and with no Android import, so it is JVM-testable
 * ([WidgetPaletteTest] checks all eleven backgrounds at three transparencies).
 *
 * Two decisions are worth spelling out:
 *
 * 1. **What "the background" is.** A translucent widget shows the launcher through it, so the
 *    colour is first composited over [TGColors.SCRIM]: the app is dark-only and this design assumes
 *    the dark wallpaper it was drawn for. That is also what keeps a very transparent yellow widget
 *    on the white foreground instead of flipping it to black over a dark home screen.
 * 2. **Which foreground.** The one whose primary text has the higher contrast ratio, white winning
 *    a tie — no luminance threshold picked by eye, and no per-colour table to keep in step with
 *    [TGColors.ACCENT_PALETTE].
 */
internal object WidgetPalette {

    /**
     * Text on a light background. Pure black rather than [TGColors.SCRIM]: at the crossover — the
     * background luminance 0.1791 where white and black are equally readable, which no palette
     * colour lands on but a partly transparent one does — black is the only value that still clears
     * 4.5:1, at 4.58:1 against SCRIM's 4.16:1. Everywhere else the two are indistinguishable.
     */
    const val DARK_PRIMARY: Int = 0xFF000000.toInt()

    /**
     * The secondary and tertiary greys of the dark foreground, the counterparts of
     * [TGColors.TEXT_SECONDARY] / [TGColors.TEXT_TERTIARY].
     *
     * Both are the *lightest* neutral grey that still passes on the darkest **opaque** background
     * that selects this foreground — vermilion `#F95B3A`, luminance 0.28 — because that is what
     * bounds them there; on the lightest palette colour (yellow `#FCCD0B`) they have room to spare:
     *
     * | grey                | on vermilion | on yellow | target |
     * |---------------------|--------------|-----------|--------|
     * | `#262727` secondary | 4.70:1       | 9.90:1    | 4.5:1  |
     * | `#3F4040` tertiary  | 3.26:1       | 6.87:1    | 3:1    |
     *
     * The slider is continuous, though, so an accent composited over [TGColors.SCRIM] can land just
     * past the crossover (luminance 0.179 — lime at 58 % alpha, vermilion at 80 %), where the greys
     * are 3.27:1 and 2.27:1 and neither passes. That band is not theirs to cover: [legibleOn]
     * collapses secondary, tertiary and the badge onto [DARK_PRIMARY] until the background is light
     * enough to carry them, and nothing in the widget is left below its threshold.
     *
     * They keep the app's warm-neutral grey hue (R a shade under G = B, as in [TGColors.SURFACE]).
     * Anything lighter would read as a clearer hierarchy but would fall back to [DARK_PRIMARY] on
     * the warm end of the palette, which reads worse — a light background simply leaves a dark
     * foreground less room than a dark background leaves a light one.
     */
    const val DARK_SECONDARY: Int = 0xFF262727.toInt()
    const val DARK_TERTIARY: Int = 0xFF3F4040.toInt()

    /**
     * The gain and loss inks of the dark foreground, the counterparts of [TGColors.UP] /
     * [TGColors.DOWN] — which a light background never carries: the palette green is 1.11:1 on
     * vermilion and 1.90:1 on yellow, the palette red 1.17:1 and 2.46:1. Without a deep variant to
     * fall to, the change text and the sparkline would go black on every accent and the widget would
     * lose the one colour that is not decoration.
     *
     * Each is its seed hue scaled towards black — Material's deep green `#1B5E20` at 80 %, a deep
     * red `#8B1A1A` at 92 % — which keeps the hue and leaves the two at the same weight (luminance
     * 0.0528 and 0.0531, so neither reads as the louder half of the pair). That is the *lightest*
     * pair that still passes on the darkest **opaque** accent, vermilion `#F95B3A`; on the lightest
     * one (yellow `#FCCD0B`) they have room to spare:
     *
     * | ink            | on vermilion | on yellow | target |
     * |----------------|--------------|-----------|--------|
     * | `#164B1A` up   | 3.20:1       | 6.75:1    | 3:1    |
     * | `#801818` down | 3.19:1       | 6.73:1    | 3:1    |
     *
     * All eight of [TGColors.ACCENT_PALETTE] pass at full opacity — lavender, the next darkest after
     * vermilion, is 3.70:1 and 3.69:1. Transparency is another matter: an accent composited over
     * [TGColors.SCRIM] can select this foreground while still being as dark as luminance 0.179,
     * which leaves 0.026 for the ink, and no green or red worth the name fits under that. There, as
     * with [DARK_SECONDARY] / [DARK_TERTIARY], [legibleOn] collapses the pair onto [DARK_PRIMARY]
     * and the sign in `+6.52%` carries the meaning by itself.
     */
    const val DARK_UP: Int = 0xFF164B1A.toInt()
    const val DARK_DOWN: Int = 0xFF801818.toInt()

    /** WCAG AA for body text. */
    private const val TEXT_RATIO = 4.5

    /** WCAG AA for graphics and large text: the badge, the sparkline, the change. */
    private const val GRAPHIC_RATIO = 3.0

    /** How far a stale snapshot fades before [dimmed] starts walking the fade back, and in what steps. */
    private const val STALE_ALPHA = 0.5
    private const val STALE_STEP = 1.0 / 32

    private val LIGHT_ON_DARK = Foreground(
        primary = TGColors.TEXT_PRIMARY.toInt(),
        secondary = TGColors.TEXT_SECONDARY.toInt(),
        tertiary = TGColors.TEXT_TERTIARY.toInt(),
    )

    private val DARK_ON_LIGHT = Foreground(
        primary = DARK_PRIMARY,
        secondary = DARK_SECONDARY,
        tertiary = DARK_TERTIARY,
    )

    /** @param blendedArgb the background with its transparency in the alpha channel — [WidgetConfig.blendedArgb]. */
    fun of(blendedArgb: Int): WidgetScheme {
        val background = onScrim(blendedArgb)
        val base = if (
            contrastRatio(LIGHT_ON_DARK.primary, background) >= contrastRatio(DARK_ON_LIGHT.primary, background)
        ) {
            LIGHT_ON_DARK
        } else {
            DARK_ON_LIGHT
        }
        val primary = base.primary
        val tertiary = legibleOn(base.tertiary, background, GRAPHIC_RATIO, primary)
        // Up and down have one rung more than the rest: the palette colour, then — only under the
        // dark foreground, where that colour is the one that fails and a deep variant is still
        // legible; on a dark background it would be all but invisible — [DARK_UP] / [DARK_DOWN],
        // and only then the primary.
        val deepUp = if (base === DARK_ON_LIGHT) legibleOn(DARK_UP, background, GRAPHIC_RATIO, primary) else primary
        val deepDown = if (base === DARK_ON_LIGHT) legibleOn(DARK_DOWN, background, GRAPHIC_RATIO, primary) else primary
        val up = legibleOn(TGColors.UP.toInt(), background, GRAPHIC_RATIO, deepUp)
        val down = legibleOn(TGColors.DOWN.toInt(), background, GRAPHIC_RATIO, deepDown)
        return WidgetScheme(
            background = background,
            primary = primary,
            secondary = legibleOn(base.secondary, background, TEXT_RATIO, primary),
            tertiary = tertiary,
            badge = tertiary,
            up = up,
            down = down,
            stale = dimmed(primary, background, TEXT_RATIO),
            staleUp = dimmed(up, background, GRAPHIC_RATIO),
            staleDown = dimmed(down, background, GRAPHIC_RATIO),
        )
    }

    /**
     * The opaque colour a translucent widget really shows: [argb] composited over [TGColors.SCRIM],
     * in sRGB space, which is where the launcher composites it too.
     */
    fun onScrim(argb: Int): Int {
        val alpha = channel(argb, ALPHA_SHIFT) / 255.0
        return if (alpha >= 1.0) argb or OPAQUE else composite(argb, TGColors.SCRIM.toInt(), alpha)
    }

    /** WCAG 2.1 contrast, `(lighter + 0.05) / (darker + 0.05)`: 1:1 … 21:1. Alpha is ignored. */
    fun contrastRatio(argbA: Int, argbB: Int): Double {
        val a = relativeLuminance(argbA)
        val b = relativeLuminance(argbB)
        return if (a >= b) (a + 0.05) / (b + 0.05) else (b + 0.05) / (a + 0.05)
    }

    /** WCAG 2.1 relative luminance of an opaque colour: 0.0 for black, 1.0 for white. */
    fun relativeLuminance(argb: Int): Double =
        0.2126 * linear(channel(argb, RED_SHIFT)) +
            0.7152 * linear(channel(argb, GREEN_SHIFT)) +
            0.0722 * linear(channel(argb, BLUE_SHIFT))

    /** [colour] when it carries [minRatio] on [background], otherwise [fallback] — which always does. */
    private fun legibleOn(colour: Int, background: Int, minRatio: Double, fallback: Int): Int =
        if (contrastRatio(colour, background) >= minRatio) colour else fallback

    /**
     * [colour] faded towards [background] to say "this price is old", but only as far as [minRatio]
     * still allows.
     *
     * Halving the alpha of a light foreground moves it away from its dark background, which is why
     * it worked while the widget was white-on-dark. Halving a *dark* foreground moves it towards a
     * light one: the price, the widget's largest text, is black at 50 % on vermilion — 2.89:1 on the
     * opaque hue and 2.24:1 on a half-transparent one, below even the 3:1 the sparkline is held to.
     * So the fade starts at [STALE_ALPHA] and is walked back in [STALE_STEP]s until the composite
     * passes — contrast against a fixed background rises with the alpha, so the first pass is also
     * the faintest one that qualifies. The walk always terminates: at alpha 1.0 this is [colour]
     * itself, and every colour in a [WidgetScheme] carries its own threshold. The result is opaque,
     * so what the launcher draws is what was measured here.
     */
    private fun dimmed(colour: Int, background: Int, minRatio: Double): Int {
        var alpha = STALE_ALPHA
        while (alpha < 1.0) {
            val faded = composite(colour, background, alpha)
            if (contrastRatio(faded, background) >= minRatio) return faded
            alpha += STALE_STEP
        }
        return colour
    }

    /** [argb] at [alpha] over the opaque [under], in sRGB space — where the launcher composites too. */
    private fun composite(argb: Int, under: Int, alpha: Double): Int {
        var out = OPAQUE
        for (shift in intArrayOf(RED_SHIFT, GREEN_SHIFT, BLUE_SHIFT)) {
            val mixed = channel(argb, shift) * alpha + channel(under, shift) * (1.0 - alpha)
            out = out or ((mixed + 0.5).toInt().coerceIn(0, 255) shl shift)
        }
        return out
    }

    private fun linear(component: Int): Double {
        val c = component / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun channel(argb: Int, shift: Int): Int = (argb ushr shift) and 0xFF

    /** One foreground set before the contrast test; [WidgetScheme] is what comes out of it. */
    private class Foreground(val primary: Int, val secondary: Int, val tertiary: Int)

    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val BLUE_SHIFT = 0
    private const val OPAQUE = 0xFF shl ALPHA_SHIFT
}
