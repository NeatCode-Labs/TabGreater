package com.neatcode.tabgreater.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.core.model.TGColors

/** The app's colour tokens as Compose colours (source of truth: core/model TGColors). */
object TG {
    val Background = Color(TGColors.BACKGROUND)
    val Surface = Color(TGColors.SURFACE)
    val NavSurface = Color(TGColors.NAV_SURFACE)
    val Scrim = Color(TGColors.SCRIM)
    val ChipFill = Color(TGColors.CHIP_FILL)
    val Outline = Color(TGColors.OUTLINE)
    val TextPrimary = Color(TGColors.TEXT_PRIMARY)
    val TextSecondary = Color(TGColors.TEXT_SECONDARY)
    val TextTertiary = Color(TGColors.TEXT_TERTIARY)
    val Accent = Color(TGColors.ACCENT)
    val NavPill = Color(TGColors.NAV_PILL)
    val Up = Color(TGColors.UP)
    val Down = Color(TGColors.DOWN)
}

/** `includeFontPadding = false` everywhere: this vertical rhythm is too tight for legacy font padding. */
val Tight = PlatformTextStyle(includeFontPadding = false)

/** Righteous by Astigmatic, SIL Open Font License 1.1 (LICENSES/OFL-1.1-Righteous.txt). */
val Brand: FontFamily = FontFamily(Font(R.font.righteous_regular, FontWeight.Normal))

/** The app's text roles (design values at fontScale 1.0). */
object TGType {
    val exchange = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 9.sp, lineHeight = 11.sp, letterSpacing = 0.5.sp,
        color = TG.TextTertiary, platformStyle = Tight,
    )
    val pair = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 18.sp, color = TG.TextPrimary, platformStyle = Tight,
    )
    val subLabel = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 11.sp, lineHeight = 13.sp, color = TG.TextSecondary, platformStyle = Tight,
    )
    val price = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 22.sp, lineHeight = 24.sp, color = TG.TextPrimary, platformStyle = Tight,
    )
    val change = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 15.sp, platformStyle = Tight,
    )
    val chip = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 14.sp, color = TG.TextSecondary, platformStyle = Tight,
    )
    val tab = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 14.sp, platformStyle = Tight,
    )
    val tabActive = tab.copy(fontWeight = FontWeight.Medium, color = TG.TextPrimary)
    val nav = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 11.sp, lineHeight = 13.sp, platformStyle = Tight,
    )
    val navActive = nav.copy(fontWeight = FontWeight.Medium, color = TG.TextPrimary)
    val fab = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 18.sp, color = TG.Scrim, platformStyle = Tight,
    )
    val appBarTitle = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = 1.sp, color = TG.TextPrimary, platformStyle = Tight,
    )

    /** The brand word mark next to the logo glyph: Righteous (SIL OFL 1.1), mixed case. */
    val brandTitle = TextStyle(
        fontFamily = Brand, fontWeight = FontWeight.Normal,
        fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = 0.5.sp, color = TG.TextPrimary, platformStyle = Tight,
    )

    /** Editable text in the market-search top bar. */
    val searchInput = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 20.sp, color = TG.TextPrimary, platformStyle = Tight,
    )

    /** Empty states and placeholder screens. */
    val body = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 18.sp, color = TG.TextSecondary, platformStyle = Tight,
    )

    // ---- F3: larger tiles ------------------------------------------------------------------

    /** Absolute change line on Compact/Medium/Large tiles: `+1.10 (0.04%)` (colour = trend). */
    val absChange = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 15.sp, platformStyle = Tight,
    )

    /** `High` / `Low` / `Volume` labels in the tile detail micro-rows. */
    val tileDetailLabel = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 8.sp, lineHeight = 10.sp, color = TG.TextTertiary, platformStyle = Tight,
    )

    /** Values in the tile detail micro-rows. */
    val tileDetailValue = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 9.sp, lineHeight = 11.sp, color = TG.TextSecondary, platformStyle = Tight,
    )

    // ---- F3: sheets, manager, settings -----------------------------------------------------

    /** Bottom-sheet title ("Tickers Timeframe", "Watchlists"), 16 sp. */
    val sheetTitle = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 20.sp, color = TG.TextPrimary, platformStyle = Tight,
    )

    /** Bottom-sheet option row ("24 hours", "Compact", "Exchange + Pair"). */
    val sheetItem = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 18.sp, color = TG.TextPrimary, platformStyle = Tight,
    )

    /** Second line of a bottom-sheet option row ("Recommended", "keeps a connection open"). */
    val sheetItemSupport = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 11.sp, lineHeight = 13.sp, color = TG.TextSecondary, platformStyle = Tight,
    )

    /** Watchlist Manager row name. */
    val listTitle = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 17.sp, lineHeight = 20.sp, color = TG.TextPrimary, platformStyle = Tight,
    )

    /** Watchlist Manager row subtitle (`24 hours · Small · Custom · 45/100`), settings subtitles. */
    val listSubtitle = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 14.sp, color = TG.TextSecondary, platformStyle = Tight,
    )

    /** Contextual action bar title in selection mode ("3 selected"). */
    val actionBarTitle = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 18.sp, lineHeight = 22.sp, color = TG.TextPrimary, platformStyle = Tight,
    )

    /** Settings section header (accent caps). */
    val sectionHeader = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp, color = TG.Accent, platformStyle = Tight,
    )

    /** Settings row title. */
    val settingTitle = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 18.sp, color = TG.TextPrimary, platformStyle = Tight,
    )

    /** Text buttons (dialogs, snackbar "UNDO"). */
    val button = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp, color = TG.Accent, platformStyle = Tight,
    )

    // ---- F4: chart screen ------------------------------------------------------------------

    /** `24h` / `Ask` / `High` labels in the chart header's 2 × 3 statistics grid. */
    val chartHeaderLabel = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 10.sp, lineHeight = 12.sp, color = TG.TextTertiary, platformStyle = Tight,
    )

    /** Values in the chart header's statistics grid. */
    val chartHeaderValue = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 14.sp, color = TG.TextPrimary, platformStyle = Tight,
    )

    /** The chart header's last price. */
    val chartPrice = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 22.sp, lineHeight = 24.sp, color = TG.TextPrimary, platformStyle = Tight,
    )

    /** Timeframe chips in the chart toolbar and the `log` / `auto` pills over the canvas. */
    val toolbarChip = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 14.sp, color = TG.TextSecondary, platformStyle = Tight,
    )
}

private val DarkScheme = darkColorScheme(
    primary = TG.Accent,
    onPrimary = TG.Scrim,
    primaryContainer = TG.NavPill,
    onPrimaryContainer = TG.TextPrimary,
    secondary = TG.Accent,
    onSecondary = TG.Scrim,
    secondaryContainer = TG.NavPill,
    onSecondaryContainer = TG.TextPrimary,
    background = TG.Background,
    onBackground = TG.TextPrimary,
    surface = TG.Background,
    onSurface = TG.TextPrimary,
    surfaceVariant = TG.Surface,
    onSurfaceVariant = TG.TextSecondary,
    surfaceContainer = TG.Surface,
    surfaceContainerLow = TG.Surface,
    surfaceContainerHigh = TG.NavSurface,
    surfaceContainerHighest = TG.NavSurface,
    outline = TG.Outline,
    outlineVariant = TG.Outline,
    error = TG.Down,
    onError = TG.TextPrimary,
)

private val TGShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun TabGreaterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        shapes = TGShapes,
        content = content,
    )
}
