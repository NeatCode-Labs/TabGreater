package com.neatcode.tabgreater.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.core.model.TGDimens
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType

private val PillShape = RoundedCornerShape(percent = 50)

/**
 * The app's extended FAB: a **40 dp** accent pill — not Material's 56 dp — with
 * 16 dp start / 12 dp gap / 20 dp end padding and near-black content. Used for "+ Ticker" on the
 * watchlist and for "Add N" on the search screen.
 *
 * The `+` glyph is drawn at 22 dp so its ink measures the ≈13 dp the design calls for
 * inside a 24 dp box.
 *
 * "+ Ticker" only measures ≈107 dp from its paddings alone, so the pill is also held to the
 * [TGDimens.FAB_W_DP] minimum and centres its content inside it.
 */
@Composable
fun TGPillButton(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Add,
) {
    Row(
        modifier = modifier
            .height(TGDimens.FAB_H_DP.dp)
            .widthIn(min = TGDimens.FAB_W_DP.dp)
            .clip(PillShape)
            .background(TG.Accent)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(TopBarIconSize), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = TG.Scrim,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = TGType.fab, maxLines = 1)
    }
}

/** "+ Ticker" — opens the market search for [onClick]'s watchlist. */
@Composable
fun AddTickerFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TGPillButton(
        label = stringResource(R.string.fab_add_ticker),
        contentDescription = stringResource(R.string.cd_add_ticker),
        onClick = onClick,
        modifier = modifier,
    )
}

@Preview(widthDp = 200, heightDp = 60, backgroundColor = 0xFF141515, showBackground = true)
@Composable
private fun AddTickerFabPreview() {
    Box(Modifier.padding(8.dp)) { AddTickerFab(onClick = {}) }
}
