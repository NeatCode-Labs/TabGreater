package com.neatcode.tabgreater.ui.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neatcode.tabgreater.core.model.SortMode
import com.neatcode.tabgreater.core.model.SparkPeriod
import com.neatcode.tabgreater.core.model.TGDimens
import com.neatcode.tabgreater.core.model.TileSize
import com.neatcode.tabgreater.ui.icons.TGIcons
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType

private val ChipShape = RoundedCornerShape(percent = 50)

/**
 * The `period | size | sort` chip row. The chips always display the current
 * value and have no selected state; tapping one opens the matching bottom sheet
 * ([PeriodSheet], [TileSizeSheet], [SortSheet]).
 */
@Composable
fun FilterChipsRow(
    period: SparkPeriod,
    tileSize: TileSize,
    sort: SortMode,
    onPeriodClick: () -> Unit,
    onTileSizeClick: () -> Unit,
    onSortClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TG.Background)
            .height(TGDimens.CHIP_ROW_DP.dp)
            .horizontalScroll(rememberScrollState())
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(TGIcons.ShowChart, period.label, onPeriodClick)
        Spacer(Modifier.width(8.dp))
        FilterChip(TGIcons.forTileSize(tileSize), tileSize.label, onTileSizeClick)
        Spacer(Modifier.width(8.dp))
        FilterChip(TGIcons.SortLines, sort.label, onSortClick)
    }
}

@Composable
private fun FilterChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TG.TextSecondary,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text = label, style = TGType.chip, maxLines = 1)
    }
}

@Preview(widthDp = 360, backgroundColor = 0xFF141515, showBackground = true)
@Composable
private fun FilterChipsRowPreview() {
    FilterChipsRow(
        period = SparkPeriod.HOURS_24,
        tileSize = TileSize.SMALL,
        sort = SortMode.CUSTOM,
        onPeriodClick = {},
        onTileSizeClick = {},
        onSortClick = {},
    )
}
