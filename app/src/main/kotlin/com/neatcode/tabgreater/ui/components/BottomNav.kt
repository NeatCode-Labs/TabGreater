package com.neatcode.tabgreater.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.core.model.TGDimens
import com.neatcode.tabgreater.ui.icons.TGIcons
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType

/** The two root destinations reachable from the bottom navigation bar. */
enum class BottomNavItem { WATCHLISTS, SETTINGS }

private val PillShape = RoundedCornerShape(percent = 50)

/**
 * The bottom navigation bar: 56 dp on `navSurface` with a 1 dp lighter top
 * edge, an active item marked by a 58 × 30 dp `navPill` behind an 18 dp icon, and the system
 * navigation-bar inset painted in the same colour underneath.
 */
@Composable
fun BottomNav(
    selected: BottomNavItem,
    onSelect: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(TG.NavSurface)) {
        HorizontalDivider(thickness = 1.dp, color = TG.Outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(TGDimens.NAV_BAR_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(
                icon = TGIcons.ShowChart,
                label = stringResource(R.string.nav_watchlists),
                active = selected == BottomNavItem.WATCHLISTS,
                onClick = { onSelect(BottomNavItem.WATCHLISTS) },
                modifier = Modifier.weight(1f),
            )
            NavItem(
                icon = Icons.Outlined.Settings,
                label = stringResource(R.string.nav_settings),
                active = selected == BottomNavItem.SETTINGS,
                onClick = { onSelect(BottomNavItem.SETTINGS) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (active) TG.TextPrimary else TG.TextSecondary
    Column(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(58.dp)
                .height(30.dp)
                .clip(PillShape)
                .background(if (active) TG.NavPill else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(TopBarIconSize), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = if (active) TGType.navActive else TGType.nav,
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Preview(widthDp = 360, backgroundColor = 0xFF141515, showBackground = true)
@Composable
private fun BottomNavPreview() {
    BottomNav(selected = BottomNavItem.WATCHLISTS, onSelect = {})
}
