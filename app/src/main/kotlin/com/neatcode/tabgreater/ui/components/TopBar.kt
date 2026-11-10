package com.neatcode.tabgreater.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import com.neatcode.tabgreater.R
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neatcode.tabgreater.core.model.TGDimens
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType

/** Height of the tappable icon boxes in the top bar and tab row. */
val TopBarIconSize = 24.dp

/**
 * The 48 dp top bar shell: status-bar inset above it, screen background behind it, 16 dp side
 * margins. Every screen in the app uses it so the chrome lines up across destinations.
 */
@Composable
fun TGTopBar(
    modifier: Modifier = Modifier,
    endPadding: Dp = 16.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TG.Background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(TGDimens.APP_BAR_DP.dp)
            .padding(start = 16.dp, end = endPadding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Watchlists / Settings top bar: the brand — logo glyph plus the word mark, "Tab" in white and
 * "Greater" in the accent, like the icon's white bar over its green stem. A screen can supply a
 * trailing action without changing the height of the bar or adding controls to other screens.
 */
@Composable
fun TGAppBar(modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    // A full-size touch target has 12 dp more inset than the tab row's 24 dp icon.
    // Recover that inset so both visible icons share the same vertical centre line.
    val actionEndPadding = if (action == null) 16.dp else
        16.dp - (TGDimens.STATUS_TOUCH_DP.dp - TopBarIconSize) / 2
    TGTopBar(modifier, endPadding = actionEndPadding) {
        Image(
            painter = painterResource(R.drawable.ic_brand_glyph),
            contentDescription = null,
            modifier = Modifier.height(BrandGlyphHeight),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = buildAnnotatedString {
                append(BRAND_HEAD)
                withStyle(SpanStyle(color = TG.Accent)) { append(BRAND_TAIL) }
            },
            style = TGType.brandTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

private const val BRAND_HEAD = "Tab"
private const val BRAND_TAIL = "Greater"
private val BrandGlyphHeight = 24.dp

/**
 * A bare 24 dp icon target. The chrome puts its icons exactly 16 dp from the screen edge,
 * which Material's 48 dp `IconButton` cannot do, so the touch target is the glyph box itself.
 */
@Composable
fun TGIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TG.TextPrimary,
    size: Dp = TopBarIconSize,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size),
        )
    }
}
