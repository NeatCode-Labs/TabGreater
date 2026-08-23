package com.neatcode.tabgreater.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neatcode.tabgreater.core.data.popular.DEFAULT_POPULAR_PAIRS
import com.neatcode.tabgreater.core.model.TGDimens
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType

private val ChipShape = RoundedCornerShape(percent = 50)

/**
 * Quick-add chips under the "+ Ticker" search field: the top coins by market cap against USDT.
 * Tapping one types the pair into the search field, so the normal results list then shows that
 * pair on every exchange and the user multi-selects as usual.
 *
 * The ranking comes from CoinGecko; the credit for it lives on the About screen, in the README
 * and in NOTICE rather than in this row, which has no room for it.
 */
@Composable
fun PopularPairsRow(
    pairs: List<String>,
    onPairClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pairs.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TG.Background)
            .height(TGDimens.CHIP_ROW_DP.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pairs.forEachIndexed { index, pair ->
                if (index > 0) Spacer(Modifier.width(8.dp))
                PopularPairChip(pair = pair, onClick = { onPairClick(pair) })
            }
        }
    }
}

@Composable
private fun PopularPairChip(pair: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(TGDimens.CHIP_H_DP.dp)
            .clip(ChipShape)
            .background(TG.ChipFill)
            .border(1.dp, TG.Outline, ChipShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = pair, style = TGType.chip, maxLines = 1)
    }
}

@Preview(widthDp = 360, backgroundColor = 0xFF141515, showBackground = true)
@Composable
private fun PopularPairsRowPreview() {
    PopularPairsRow(pairs = DEFAULT_POPULAR_PAIRS, onPairClick = {})
}
