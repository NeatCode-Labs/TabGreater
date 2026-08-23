package com.neatcode.tabgreater.widget

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neatcode.tabgreater.core.model.TGColors

/**
 * The app's dark look, applied to the widget configuration screen.
 *
 * `:widget` cannot depend on `:app`, so the tokens are read from `core:model`'s [TGColors] — the
 * single source of truth both sides share — and the type scale mirrors `ui/theme/Theme.kt`,
 * `includeFontPadding = false` included.
 */
internal object TW {
    val Background = Color(TGColors.BACKGROUND.toInt())
    val Surface = Color(TGColors.SURFACE.toInt())
    val ChipFill = Color(TGColors.CHIP_FILL.toInt())
    val Outline = Color(TGColors.OUTLINE.toInt())
    val TextPrimary = Color(TGColors.TEXT_PRIMARY.toInt())
    val TextSecondary = Color(TGColors.TEXT_SECONDARY.toInt())
    val TextTertiary = Color(TGColors.TEXT_TERTIARY.toInt())
    val Accent = Color(TGColors.ACCENT.toInt())
    val Scrim = Color(TGColors.SCRIM.toInt())
    val Up = Color(TGColors.UP.toInt())
    val Down = Color(TGColors.DOWN.toInt())
}

private val Tight = PlatformTextStyle(includeFontPadding = false)

internal object TWType {
    val title = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 18.sp, lineHeight = 22.sp, color = TW.TextPrimary, platformStyle = Tight,
    )
    val sectionHeader = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp, color = TW.Accent, platformStyle = Tight,
    )
    val exchange = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 9.sp, lineHeight = 11.sp, letterSpacing = 0.5.sp, color = TW.TextTertiary, platformStyle = Tight,
    )
    val pair = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 18.sp, color = TW.TextPrimary, platformStyle = Tight,
    )
    val price = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 22.sp, lineHeight = 24.sp, color = TW.TextPrimary, platformStyle = Tight,
    )
    val change = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 15.sp, platformStyle = Tight,
    )
    val meta = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 9.sp, lineHeight = 11.sp, color = TW.TextTertiary, platformStyle = Tight,
    )
    val row = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 18.sp, color = TW.TextPrimary, platformStyle = Tight,
    )
    val subtitle = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 14.sp, color = TW.TextSecondary, platformStyle = Tight,
    )
    val searchInput = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 20.sp, color = TW.TextPrimary, platformStyle = Tight,
    )
    val button = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 18.sp, color = TW.Scrim, platformStyle = Tight,
    )
}

private val Scheme = darkColorScheme(
    primary = TW.Accent,
    onPrimary = TW.Scrim,
    background = TW.Background,
    onBackground = TW.TextPrimary,
    surface = TW.Background,
    onSurface = TW.TextPrimary,
    surfaceVariant = TW.Surface,
    onSurfaceVariant = TW.TextSecondary,
    outline = TW.Outline,
    outlineVariant = TW.Outline,
    error = TW.Down,
    onError = TW.TextPrimary,
)

private val WidgetShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
internal fun WidgetConfigTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, shapes = WidgetShapes, content = content)
}
