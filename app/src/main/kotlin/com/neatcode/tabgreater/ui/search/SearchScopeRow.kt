package com.neatcode.tabgreater.ui.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.core.model.AssetClass
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType

private val IndicatorShape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
private const val SCOPE_ROW_DP = 36
private const val SCOPE_TAB_MIN_DP = 48

/** Each label is padded by half the watchlist row's 24 dp label gap, on both sides. */
private const val SCOPE_TAB_PAD_DP = 12

/** With the first tab's own padding this puts the first label 16 dp in, under the back arrow. */
private const val SCOPE_ROW_START_DP = 4

/**
 * The search's "Crypto | Stocks" scope: a 36 dp row of two text tabs styled after the watchlist
 * tab row — the active label in the primary text colour over a 3 dp accent indicator exactly as
 * wide as the text, the inactive one in the secondary text colour, a 1 dp `outline` divider underneath. Each
 * tab is at least 48 dp wide and reports itself as a selected or unselected [Role.Tab].
 */
@Composable
fun SearchScopeRow(
    scope: AssetClass,
    onScopeChange: (AssetClass) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(TG.Background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SCOPE_ROW_DP.dp - 1.dp)
                .padding(start = SCOPE_ROW_START_DP.dp)
                .selectableGroup(),
        ) {
            ScopeTab(
                label = stringResource(R.string.search_scope_crypto),
                active = scope == AssetClass.CRYPTO,
                onClick = { onScopeChange(AssetClass.CRYPTO) },
            )
            ScopeTab(
                label = stringResource(R.string.search_scope_stocks),
                active = scope == AssetClass.STOCK,
                onClick = { onScopeChange(AssetClass.STOCK) },
            )
        }
        HorizontalDivider(thickness = 1.dp, color = TG.Outline)
    }
}

@Composable
private fun ScopeTab(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = SCOPE_TAB_MIN_DP.dp)
            .selectable(selected = active, role = Role.Tab, onClick = onClick)
            .padding(horizontal = SCOPE_TAB_PAD_DP.dp),
        contentAlignment = Alignment.Center,
    ) {
        // IntrinsicSize.Max pins the column to the label's own width, which the indicator then
        // fills, exactly as in the watchlist tab row.
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(IntrinsicSize.Max),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                text = label,
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
}

/**
 * The Stocks scope's one quiet caption: these are tokens traded on crypto exchanges, not the
 * shares themselves. It wraps rather than truncates (two lines at 360 dp); the line cap only
 * guards the results list against the largest font scales.
 */
@Composable
fun StockScopeCaption(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.search_scope_stocks_caption),
        style = TGType.subLabel,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .background(TG.Background)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Preview(widthDp = 360, backgroundColor = 0xFF141515, showBackground = true)
@Composable
private fun SearchScopeRowPreview() {
    Column {
        SearchScopeRow(scope = AssetClass.STOCK, onScopeChange = {})
        StockScopeCaption()
    }
}
