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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.neatcode.tabgreater.core.exchange.ExchangeFailureKind
import com.neatcode.tabgreater.core.live.MarketState
import com.neatcode.tabgreater.core.model.TGDimens
import com.neatcode.tabgreater.ui.components.TGBottomSheet
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType
import kotlinx.coroutines.delay

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
    val hasAges = issues.any { it.lastTradeAtEpochMs != null || it.lastPriceReceivedAtEpochMs != null }
    var nowEpochMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(hasAges) {
        if (hasAges) {
            while (true) {
                delay(1_000L)
                nowEpochMs = System.currentTimeMillis()
            }
        }
    }
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
            status.detailRes?.let { Text(stringResource(it), style = TGType.sheetItemSupport) }
            if (issues.isNotEmpty()) {
                Text(stringResource(R.string.price_status_affected), style = TGType.sheetItem)
                issues.forEach { issue ->
                    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Text(
                            stringResource(
                                R.string.price_status_pair,
                                issue.marketLabel?.takeIf(String::isNotBlank) ?: issue.key.pair,
                                issue.key.exchange.displayName,
                            ),
                            style = TGType.sheetItem,
                        )
                        issue.explanationRes(nowEpochMs)?.let {
                            Text(stringResource(it), style = TGType.sheetItemSupport)
                        }
                        if (issue.lastTradeAtEpochMs != null && issue.lastTradePrice != null) {
                            Text(
                                stringResource(
                                    R.string.price_status_last_trade,
                                    issue.lastTradePrice,
                                    formatTradeAge(nowEpochMs, issue.lastTradeAtEpochMs),
                                ),
                                style = TGType.sheetItemSupport,
                            )
                        }
                        issue.lastPriceReceivedAtEpochMs?.let {
                            Text(
                                stringResource(R.string.price_status_price_received, formatTradeAge(nowEpochMs, it)),
                                style = TGType.sheetItemSupport,
                            )
                        }
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

/** Human-readable age of the exchange execution timestamp, independent of receive/cache time. */
internal fun formatTradeAge(nowEpochMs: Long, tradeAtEpochMs: Long): String {
    val seconds = ((nowEpochMs - tradeAtEpochMs).coerceAtLeast(0L) / 1_000L)
    return when {
        seconds < 60L -> "${seconds}s"
        seconds < 3_600L -> "${seconds / 60L}m ${seconds % 60L}s"
        seconds < 86_400L -> "${seconds / 3_600L}h ${(seconds % 3_600L) / 60L}m"
        else -> "${seconds / 86_400L}d ${(seconds % 86_400L) / 3_600L}h"
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

@StringRes
internal fun PriceRefreshIssue.explanationRes(nowEpochMs: Long): Int? = when (kind) {
    PriceIssueKind.ERROR -> when (failure) {
        ExchangeFailureKind.RATE_LIMITED -> R.string.price_status_issue_rate_limit
        ExchangeFailureKind.FORBIDDEN -> R.string.price_status_issue_forbidden
        ExchangeFailureKind.REGION_RESTRICTED -> R.string.price_status_issue_region
        ExchangeFailureKind.INVALID_MARKET -> R.string.price_status_issue_market
        ExchangeFailureKind.INVALID_RESPONSE -> R.string.price_status_issue_response
        ExchangeFailureKind.DEFERRED -> R.string.price_status_issue_deferred
        ExchangeFailureKind.TRANSIENT, null -> R.string.price_status_issue_error
    }
    PriceIssueKind.UNCONFIRMED -> if (hasDisplayedPrice) R.string.price_status_issue_cached
        else R.string.price_status_issue_missing
    PriceIssueKind.OLD -> if (lastTradeAtEpochMs != null && lastTradePrice != null &&
        lastPriceReceivedAtEpochMs != null && nowEpochMs - lastPriceReceivedAtEpochMs in 0 until MarketState.STALE_AFTER_MS
    ) null else R.string.price_status_issue_old
}

private val PriceRefreshStatus.detailRes: Int?
    @StringRes get() = when (this) {
        PriceRefreshStatus.EMPTY -> R.string.price_status_empty_detail
        PriceRefreshStatus.CURRENT -> R.string.price_status_current_detail
        PriceRefreshStatus.REFRESHING -> R.string.price_status_refreshing_detail
        PriceRefreshStatus.STALE, PriceRefreshStatus.ERROR -> null
    }
