package com.neatcode.tabgreater.ui.watchlist

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.core.model.TGDimens
import com.neatcode.tabgreater.ui.components.TGBottomSheet
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType

/** Every state occupies the same header space; price changes never add a row above the grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PriceStatusButton(status: PriceRefreshStatus, onClick: () -> Unit) {
    val label = stringResource(status.labelRes)
    val actionLabel = stringResource(R.string.price_status_button)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(stringResource(R.string.price_status_title)) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(TGDimens.STATUS_TOUCH_DP.dp).semantics {
                contentDescription = actionLabel
                stateDescription = label
                liveRegion = LiveRegionMode.Polite
            },
        ) {
            if (status == PriceRefreshStatus.REFRESHING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(TGDimens.STATUS_ICON_DP.dp).clearAndSetSemantics { },
                    color = TG.Accent,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = when (status) {
                        PriceRefreshStatus.CURRENT -> Icons.Outlined.Check
                        PriceRefreshStatus.STALE -> Icons.Outlined.Warning
                        PriceRefreshStatus.ERROR -> Icons.Filled.Warning
                        else -> Icons.Outlined.Info
                    },
                    contentDescription = null,
                    tint = when (status) {
                        PriceRefreshStatus.CURRENT -> TG.Up
                        PriceRefreshStatus.STALE -> TG.Warning
                        PriceRefreshStatus.ERROR -> TG.Down
                        else -> TG.TextSecondary
                    },
                    modifier = Modifier.size(TGDimens.STATUS_ICON_DP.dp),
                )
            }
        }
    }
}

/** Details wrap and scroll, independently of the grid and of the size of the system font. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PriceStatusSheet(
    status: PriceRefreshStatus,
    issues: List<PriceRefreshIssue>,
    refreshInProgress: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val maxDetailsHeight = with(LocalDensity.current) { windowHeight.toDp() * 0.55f }
    TGBottomSheet(
        title = stringResource(R.string.price_status_title),
        onDismiss = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = maxDetailsHeight)
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(status.labelRes), style = TGType.sheetItem)
            Text(stringResource(status.detailRes), style = TGType.sheetItemSupport)
            if (issues.isNotEmpty()) {
                Text(stringResource(R.string.price_status_affected), style = TGType.sheetItem)
                issues.forEach { issue ->
                    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Text(
                            stringResource(R.string.price_status_pair, issue.key.pair, issue.key.exchange.displayName),
                            style = TGType.sheetItem,
                        )
                        Text(
                            stringResource(when (issue.kind) {
                                PriceIssueKind.OLD -> R.string.price_status_issue_old
                                PriceIssueKind.UNCONFIRMED -> R.string.price_status_issue_unconfirmed
                                PriceIssueKind.ERROR -> R.string.price_status_issue_error
                            }),
                            style = TGType.sheetItemSupport,
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            if (status != PriceRefreshStatus.EMPTY && status != PriceRefreshStatus.CURRENT) {
                TextButton(onClick = onRetry, enabled = !refreshInProgress) {
                    Text(stringResource(R.string.price_status_retry))
                }
            }
        }
    }
}

private val PriceRefreshStatus.labelRes: Int
    @StringRes get() = when (this) {
        PriceRefreshStatus.EMPTY -> R.string.price_status_empty
        PriceRefreshStatus.CURRENT -> R.string.price_status_current
        PriceRefreshStatus.REFRESHING -> R.string.price_status_refreshing
        PriceRefreshStatus.STALE -> R.string.price_status_stale
        PriceRefreshStatus.ERROR -> R.string.price_status_error
    }

private val PriceRefreshStatus.detailRes: Int
    @StringRes get() = when (this) {
        PriceRefreshStatus.EMPTY -> R.string.price_status_empty_detail
        PriceRefreshStatus.CURRENT -> R.string.price_status_current_detail
        PriceRefreshStatus.REFRESHING -> R.string.price_status_refreshing_detail
        PriceRefreshStatus.STALE -> R.string.price_status_stale_detail
        PriceRefreshStatus.ERROR -> R.string.price_status_error_detail
    }
