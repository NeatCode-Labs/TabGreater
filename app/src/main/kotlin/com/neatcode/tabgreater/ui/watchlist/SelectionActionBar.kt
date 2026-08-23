package com.neatcode.tabgreater.ui.watchlist

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.ui.components.TGIconButton
import com.neatcode.tabgreater.ui.components.TGTopBar
import com.neatcode.tabgreater.ui.icons.TGIcons
import com.neatcode.tabgreater.ui.theme.TGType

/** Gap between the four bulk actions; keeps "99 selected" readable at 360 dp. */
private val ActionGap = 16.dp

/**
 * The contextual action bar that replaces the app bar while tiles are ticked:
 * `✕  3 selected … ⬆ 🎨 📂 🗑`. Same 56 dp shell and 16 dp edge margins as [TGTopBar], so the
 * tab row underneath does not shift when selection mode starts.
 */
@Composable
fun SelectionActionBar(
    count: Int,
    onClose: () -> Unit,
    onMoveToTop: () -> Unit,
    onColour: () -> Unit,
    onMoveToList: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TGTopBar(modifier) {
        TGIconButton(
            imageVector = Icons.Outlined.Close,
            contentDescription = stringResource(R.string.cd_clear_selection),
            onClick = onClose,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = pluralStringResource(R.plurals.selection_count, count, count),
            style = TGType.actionBarTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        TGIconButton(
            imageVector = TGIcons.MoveToTop,
            contentDescription = stringResource(R.string.cd_move_to_top),
            onClick = onMoveToTop,
        )
        Spacer(Modifier.width(ActionGap))
        TGIconButton(
            imageVector = TGIcons.Palette,
            contentDescription = stringResource(R.string.cd_ticker_colour),
            onClick = onColour,
        )
        Spacer(Modifier.width(ActionGap))
        TGIconButton(
            imageVector = TGIcons.MoveToList,
            contentDescription = stringResource(R.string.cd_move_to_list),
            onClick = onMoveToList,
        )
        Spacer(Modifier.width(ActionGap))
        TGIconButton(
            imageVector = Icons.Outlined.Delete,
            contentDescription = stringResource(R.string.cd_delete_selected),
            onClick = onDelete,
        )
    }
}

@Preview(widthDp = 360, backgroundColor = 0xFF141515, showBackground = true)
@Composable
private fun SelectionActionBarPreview() {
    SelectionActionBar(
        count = 3,
        onClose = {},
        onMoveToTop = {},
        onColour = {},
        onMoveToList = {},
        onDelete = {},
    )
}
