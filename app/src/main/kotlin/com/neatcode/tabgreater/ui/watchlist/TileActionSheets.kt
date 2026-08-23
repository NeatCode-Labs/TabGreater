package com.neatcode.tabgreater.ui.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.core.model.Limits
import com.neatcode.tabgreater.core.model.TGColors
import com.neatcode.tabgreater.core.model.Watchlist
import com.neatcode.tabgreater.ui.components.TGBottomSheet
import com.neatcode.tabgreater.ui.components.TGSheetEmpty
import com.neatcode.tabgreater.ui.components.TGSheetOption
import com.neatcode.tabgreater.ui.theme.TG

private val SwatchSize = 32.dp
private val SwatchGap = 12.dp

/**
 * The palette behind the "Colour" action: the eight stripe colours plus "None".
 *
 * @param checkedColour the ARGB every ticked tile already carries, `null` when they differ.
 * @param noneChecked `true` when every ticked tile has no stripe at all.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TileColourSheet(
    checkedColour: Long?,
    noneChecked: Boolean,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val dismiss = rememberSheetDismiss(sheetState)
    TGBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.sheet_colour_title),
        sheetState = sheetState,
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(SwatchGap),
            verticalArrangement = Arrangement.spacedBy(SwatchGap),
        ) {
            TGColors.ACCENT_PALETTE.forEachIndexed { index, argb ->
                Swatch(
                    fill = Color(argb),
                    checked = argb == checkedColour,
                    checkTint = TG.Scrim,
                    contentDescription = stringResource(R.string.cd_colour_swatch, index + 1),
                    onClick = { dismiss { onPick(argb) } },
                )
            }
            Swatch(
                fill = Color.Transparent,
                checked = noneChecked,
                checkTint = TG.TextPrimary,
                contentDescription = stringResource(R.string.cd_colour_none),
                onClick = { dismiss { onPick(null) } },
                outlined = true,
            )
        }
    }
}

/**
 * One palette circle. The label and the "currently applied" state live on the circle itself, not
 * on the check glyph, which is only composed in one of the states — a swatch that is neither
 * checked nor the "None" one would otherwise announce nothing at all to TalkBack. The palette is
 * a single-choice group, so the circle is [Role.RadioButton] and the glyph is decorative.
 */
@Composable
private fun Swatch(
    fill: Color,
    checked: Boolean,
    checkTint: Color,
    contentDescription: String,
    onClick: () -> Unit,
    outlined: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(SwatchSize)
            .clip(CircleShape)
            .background(fill)
            .then(if (outlined) Modifier.border(1.dp, TG.Outline, CircleShape) else Modifier)
            .selectable(selected = checked, role = Role.RadioButton, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        when {
            checked -> Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = checkTint,
                modifier = Modifier.size(20.dp),
            )

            outlined -> Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = null,
                tint = TG.TextSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * The sheet behind the "Move to watchlist" action: every list except the one being edited, with
 * its `n/100` fill so the user can see which lists still have room. A list that is already full
 * is shown greyed out and cannot be picked — moving into it could only drop tickers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToWatchlistSheet(
    targets: List<Watchlist>,
    itemCounts: Map<Long, Int>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val dismiss = rememberSheetDismiss(sheetState)
    TGBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.sheet_move_title),
        sheetState = sheetState,
    ) {
        if (targets.isEmpty()) {
            TGSheetEmpty(stringResource(R.string.sheet_move_empty))
        } else {
            targets.forEach { watchlist ->
                val count = itemCounts[watchlist.id] ?: 0
                TGSheetOption(
                    label = watchlist.name,
                    checked = false,
                    onClick = { dismiss { onPick(watchlist.id) } },
                    trailingText = itemCounts[watchlist.id]?.let {
                        stringResource(R.string.watchlist_fill, it, Limits.MAX_ITEMS_PER_WATCHLIST)
                    },
                    enabled = count < Limits.MAX_ITEMS_PER_WATCHLIST,
                )
            }
        }
    }
}
