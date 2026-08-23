package com.neatcode.tabgreater.ui.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.core.model.TGDimens
import com.neatcode.tabgreater.core.model.Watchlist
import com.neatcode.tabgreater.ui.components.TGIconButton
import com.neatcode.tabgreater.ui.icons.TGIcons
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType

private val IndicatorShape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
private const val TAB_START_DP = 32
private const val TAB_GAP_DP = 24
private const val TAB_END_RESERVE_DP = 56

/**
 * The scrollable watchlist tab row: 48 dp tall, first label 32 dp from
 * the left, 24 dp between labels, a 3 dp accent indicator **exactly as wide as the label text**
 * (Material's `TabRow` spans the whole tab, which is why this is hand-rolled), the Watchlist
 * Manager button pinned at the end and a 1 dp `outline` divider underneath.
 */
@Composable
fun WatchlistTabs(
    watchlists: List<Watchlist>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(TG.Background)) {
        Box(Modifier.fillMaxWidth().height(TGDimens.TAB_ROW_DP.dp - 1.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = TAB_START_DP.dp, end = TAB_END_RESERVE_DP.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                watchlists.forEachIndexed { index, watchlist ->
                    if (index > 0) Spacer(Modifier.width(TAB_GAP_DP.dp))
                    WatchlistTab(
                        name = watchlist.name,
                        active = watchlist.id == selectedId,
                        onClick = { onSelect(watchlist.id) },
                    )
                }
            }

            // Opaque so the labels scroll underneath it instead of colliding with it.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .background(TG.Background)
                    .padding(start = 8.dp, end = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                TGIconButton(
                    imageVector = TGIcons.PlaylistAdd,
                    contentDescription = stringResource(R.string.cd_manage_watchlists),
                    onClick = onManage,
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = TG.Outline)
    }
}

@Composable
private fun WatchlistTab(
    name: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(
        // IntrinsicSize.Max pins the column to the label's own width, which the indicator then
        // fills — that is what makes the indicator match the text and not the tab.
        modifier = Modifier
            .fillMaxHeight()
            .width(IntrinsicSize.Max)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = name,
            style = if (active) TGType.tabActive else TGType.tab,
            color = if (active) TG.TextPrimary else TG.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(IndicatorShape)
                .background(if (active) TG.Accent else Color.Transparent),
        )
    }
}

@Preview(widthDp = 360, backgroundColor = 0xFF141515, showBackground = true)
@Composable
private fun WatchlistTabsPreview() {
    WatchlistTabs(
        watchlists = listOf(
            Watchlist(id = 1, name = "Main", position = 0),
            Watchlist(id = 2, name = "Alts", position = 1),
        ),
        selectedId = 1,
        onSelect = {},
        onManage = {},
    )
}
