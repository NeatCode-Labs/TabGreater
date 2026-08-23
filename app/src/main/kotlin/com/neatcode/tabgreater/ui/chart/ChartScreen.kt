package com.neatcode.tabgreater.ui.chart

import android.content.Context
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.ViewTreeObserver
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.PriceFormat
import com.neatcode.tabgreater.core.model.TGDimens
import com.neatcode.tabgreater.core.model.Timeframe
import com.neatcode.tabgreater.feature.chart.ChartBridge
import com.neatcode.tabgreater.feature.chart.ChartPeriods
import com.neatcode.tabgreater.feature.chart.ChartView
import com.neatcode.tabgreater.ui.components.ExchangeGlyph
import com.neatcode.tabgreater.ui.components.TGIconButton
import com.neatcode.tabgreater.ui.components.TGTopBar
import com.neatcode.tabgreater.ui.icons.TGIcons
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType
import com.neatcode.tabgreater.ui.watchlist.shrunkPrice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

private const val TAG = "ChartScreen"
private val StatsGridHeight = 44.dp
private val ToolbarHeight = 48.dp
private val FullscreenToolbarHeight = 40.dp
private val PillShape = RoundedCornerShape(percent = 50)
private val TabIndicatorShape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)

/**
 * Below this window height the 44 dp statistics grid is folded into the header row.
 *
 * The rule is stated in available height rather than `orientation == LANDSCAPE` because that is
 * the actual constraint: at `h360dp` the header (56 + 44) plus the toolbar (48) leave ~210 dp for
 * the canvas, and KLineChart hands its sub-panes fixed pixel heights first, so the candle pane is
 * squeezed to nothing (emulator report F4-1). Height also covers split screen and freeform
 * windows, which an orientation check misses.
 */
private val CompactHeaderBelow = 480.dp

/**
 * The chart screen (F4): header · 2 × 3 statistics grid · KLineChart canvas · toolbar.
 *
 * Nothing here scrolls vertically — the WebView owns every gesture inside its box
 *, so the layout is a fixed `Column` with the
 * canvas on `weight(1f)`.
 */
@Composable
fun ChartScreen(
    key: MarketKey,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    debuggable: Boolean = false,
) {
    val viewModel: ChartViewModel = koinViewModel { parametersOf(key) }
    val bridge: ChartBridge = koinInject()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var fullscreen by rememberSaveable { mutableStateOf(false) }
    // A one-shot trigger, deliberately not saveable: restoring it would re-run an autoscale.
    var autoScaleTick by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<ChartSheet?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    // Hoisted so the chip row keeps its offset across a fullscreen round-trip: the two toolbars
    // are separate call sites, and a `rememberScrollState()` inside each would start over.
    val chipScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    // Landscape fits all nine chips, so the row cannot scroll there and `ScrollState` clamps
    // itself to 0. Take the offset on the way into fullscreen and put it back on the way out,
    // once the portrait row has been measured and can scroll again.
    var chipOffset by rememberSaveable { mutableIntStateOf(0) }
    // Window-pixel bounds of what "Share chart" captures. A plain holder, not snapshot state:
    // only the click handler reads it, so nothing should recompose when layout moves it.
    val captureBounds = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
    var sharing by remember { mutableStateOf(false) }
    val activity = LocalActivity.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val shareFailed = stringResource(R.string.chart_share_failed)
    // When the header is part of the picture (compact layout) its status-bar inset is not.
    val statusBarPx = WindowInsets.statusBars.getTop(density)

    fun shareChart() {
        if (sharing) return
        val bounds = captureBounds[0]
        val host = activity
        if (bounds == null || host == null || state.market == null) {
            scope.launch { snackbarHostState.showSnackbar(shareFailed) }
            return
        }
        // In fullscreen the toolbar floats over the bottom of the canvas box: leave it out.
        val toolbarPx = if (fullscreen) with(density) { FullscreenToolbarHeight.roundToPx() } else 0
        val rect = android.graphics.Rect(
            bounds.left.toInt(),
            maxOf(bounds.top.toInt(), statusBarPx),
            bounds.right.toInt(),
            bounds.bottom.toInt() - toolbarPx,
        )
        sharing = true
        scope.launch {
            try {
                ChartShare.share(host, rect, state.key, state.settings.timeframe)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "chart share failed", e)
                snackbarHostState.showSnackbar(shareFailed)
            } finally {
                sharing = false
            }
        }
    }

    FullscreenEffect(fullscreen)
    BackHandler(enabled = fullscreen) { fullscreen = false }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event -> snackbarHostState.showSnackbar(context.describe(event)) }
    }

    LaunchedEffect(fullscreen, chipScroll) {
        if (fullscreen || chipOffset <= 0) return@LaunchedEffect
        snapshotFlow { chipScroll.maxValue }.first { it > 0 }
        chipScroll.scrollTo(chipOffset)
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(TG.Background),
    ) {
        val compactHeader = maxHeight < CompactHeaderBelow

        Column(Modifier.fillMaxSize()) {
            // The app bar row stays out of the shared picture — a back arrow and a ★ belong to
            // the screen — unless the statistics have been folded into it (compact header).
            if (!fullscreen && !compactHeader) {
                ChartHeader(
                    state = state,
                    compactStats = false,
                    onBack = onBack,
                    onToggleStar = viewModel::toggleStar,
                )
            }
            // Statistics + canvas: the region "Share chart" captures.
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { captureBounds[0] = it.boundsInWindow() },
            ) {
                if (!fullscreen) {
                    if (compactHeader) {
                        ChartHeader(
                            state = state,
                            compactStats = true,
                            onBack = onBack,
                            onToggleStar = viewModel::toggleStar,
                        )
                    } else {
                        ChartStats(state)
                    }
                    HorizontalDivider(thickness = 1.dp, color = TG.Outline)
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val market = state.market
                    if (market != null) {
                        ChartView(
                            market = market,
                            timeframe = state.settings.timeframe,
                            indicators = state.settings.indicators,
                            candleType = state.settings.candleType,
                            logScale = state.settings.logScale,
                            autoScaleTick = autoScaleTick,
                            bridge = bridge,
                            modifier = Modifier.fillMaxSize(),
                            debuggable = debuggable,
                        )
                    } else if (state.unavailable) {
                        Text(
                            text = stringResource(R.string.chart_unavailable, state.pair),
                            style = TGType.body,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        )
                    }

                    ScalePills(
                        logScale = state.settings.logScale,
                        onToggleLog = { viewModel.setLogScale(!state.settings.logScale) },
                        onAuto = { autoScaleTick++ },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = if (fullscreen) FullscreenToolbarHeight + 6.dp else 6.dp),
                    )

                    if (fullscreen) {
                        ChartToolbar(
                            timeframe = state.settings.timeframe,
                            fullscreen = true,
                            height = FullscreenToolbarHeight,
                            chipScroll = chipScroll,
                            onTimeframe = viewModel::setTimeframe,
                            onShare = ::shareChart,
                            onCandleType = { sheet = ChartSheet.CANDLE_TYPE },
                            onIndicators = { sheet = ChartSheet.INDICATORS },
                            onFullscreen = { fullscreen = false },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }

                    // The host lives inside the canvas box, so the message floats just above the
                    // toolbar in both layouts instead of covering it.
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = if (fullscreen) FullscreenToolbarHeight else 0.dp),
                    ) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = TG.Surface,
                            contentColor = TG.TextPrimary,
                            actionColor = TG.Accent,
                        )
                    }
                }
            }

            if (!fullscreen) {
                ChartToolbar(
                    timeframe = state.settings.timeframe,
                    fullscreen = false,
                    height = ToolbarHeight,
                    chipScroll = chipScroll,
                    onTimeframe = viewModel::setTimeframe,
                    onShare = ::shareChart,
                    onCandleType = { sheet = ChartSheet.CANDLE_TYPE },
                    onIndicators = { sheet = ChartSheet.INDICATORS },
                    onFullscreen = {
                        chipOffset = chipScroll.value
                        fullscreen = true
                    },
                )
            }
        }
    }

    ChartSheets(
        sheet = sheet,
        state = state,
        onDismiss = { sheet = null },
        onCandleType = viewModel::setCandleType,
        onToggleIndicator = viewModel::toggleIndicator,
        immersive = fullscreen,
    )
}

/** The snackbar text for a one-shot [ChartEvent]. */
private fun Context.describe(event: ChartEvent): String = when (event) {
    is ChartEvent.WatchlistFull -> getString(R.string.chart_star_list_full, event.limit)
    ChartEvent.NoWatchlist -> getString(R.string.chart_star_no_list)
}

/**
 * Locks the activity to landscape and hides the system bars while [fullscreen] is on, and puts
 * both back when it goes off or the screen is left.
 *
 * `MainActivity` declares `configChanges`, so the orientation lock no longer recreates the
 * activity — the WebView keeps its chart and only `tg.resize()` fires.
 */
@Composable
private fun FullscreenEffect(fullscreen: Boolean) {
    val activity = LocalActivity.current
    val view = LocalView.current

    LaunchedEffect(activity, fullscreen) {
        val window = activity?.window ?: return@LaunchedEffect
        activity.requestedOrientation =
            if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (fullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Hiding the bars once per state change is not enough: any window that takes focus away
    // (a bottom sheet, the recents switcher, a system dialog) leaves them shown when focus comes
    // back, so re-assert the immersive state on every focus gain while fullscreen is on.
    DisposableEffect(view, activity, fullscreen) {
        val window = activity?.window
        if (!fullscreen || window == null) return@DisposableEffect onDispose { }
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (!hasFocus) return@OnWindowFocusChangeListener
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        onDispose { view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
    }

    DisposableEffect(activity) {
        onDispose {
            val window = activity?.window ?: return@onDispose
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/**
 * 56 dp: back · exchange + pair · last price with its trend caret · ★.
 *
 * With [compactStats] the 24 h change and the High/Low pair move in here on one line, because the
 * separate grid would leave the canvas too short to draw a candle pane (F4-1).
 */
@Composable
private fun ChartHeader(
    state: ChartUiState,
    compactStats: Boolean,
    onBack: () -> Unit,
    onToggleStar: () -> Unit,
) {
    val trend = if (state.isUp) TG.Up else TG.Down
    TGTopBar {
        TGIconButton(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = stringResource(R.string.cd_back),
            onClick = onBack,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = state.exchangeLabel, style = TGType.exchange, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ExchangeGlyph(state.key.exchange, size = 10.dp)
            }
            Spacer(Modifier.height(2.dp))
            Text(text = state.pair, style = TGType.pair, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = shrunkPrice(state.priceText, state.shrinkZeros),
                style = TGType.chartPrice,
                maxLines = 1,
            )
            if (state.hasTrend) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (state.isUp) TGIcons.CaretUp else TGIcons.CaretDown,
                    contentDescription = null,
                    tint = trend,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        if (compactStats) {
            Spacer(Modifier.width(16.dp))
            StatCell(
                label = stringResource(R.string.chart_stat_24h),
                value = AnnotatedString(state.changeText),
                valueColor = if (state.hasTrend) trend else TG.TextPrimary,
            )
            Spacer(Modifier.width(16.dp))
            StatCell(
                label = stringResource(R.string.chart_stat_high),
                value = shrunkPrice(state.highText, state.shrinkZeros),
            )
            Spacer(Modifier.width(16.dp))
            StatCell(
                label = stringResource(R.string.chart_stat_low),
                value = shrunkPrice(state.lowText, state.shrinkZeros),
            )
        }
        Spacer(Modifier.width(12.dp))
        TGIconButton(
            imageVector = if (state.starred) TGIcons.Star else TGIcons.StarOutline,
            contentDescription = stringResource(
                if (state.starred) R.string.cd_chart_unstar else R.string.cd_chart_star,
            ),
            onClick = onToggleStar,
            tint = if (state.starred) TG.Accent else TG.TextSecondary,
        )
    }
}

/** 44 dp: `24h` · `Ask` · `High` over `Volume` · `Bid` · `Low`, two rows of three cells. */
@Composable
private fun ChartStats(state: ChartUiState) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(StatsGridHeight)
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            StatCell(
                label = stringResource(R.string.chart_stat_24h),
                value = AnnotatedString(state.changeText),
                valueColor = if (state.hasTrend) (if (state.isUp) TG.Up else TG.Down) else TG.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            StatCell(
                label = stringResource(R.string.chart_stat_ask),
                value = shrunkPrice(state.askText, state.shrinkZeros),
                // The book is coloured by side: asks red, bids green.
                valueColor = state.askText.sideColor(TG.Down),
                modifier = Modifier.weight(1f),
            )
            StatCell(
                label = stringResource(R.string.chart_stat_high),
                value = shrunkPrice(state.highText, state.shrinkZeros),
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            StatCell(
                label = stringResource(R.string.chart_stat_volume),
                value = AnnotatedString(state.volumeText),
                modifier = Modifier.weight(1f),
            )
            StatCell(
                label = stringResource(R.string.chart_stat_bid),
                value = shrunkPrice(state.bidText, state.shrinkZeros),
                valueColor = state.bidText.sideColor(TG.Up),
                modifier = Modifier.weight(1f),
            )
            StatCell(
                label = stringResource(R.string.chart_stat_low),
                value = shrunkPrice(state.lowText, state.shrinkZeros),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** [side] for a real quote, secondary grey for the `—` an exchange without a book leaves behind. */
private fun String.sideColor(side: Color): Color = if (this == PriceFormat.NO_VALUE) TG.TextSecondary else side

@Composable
private fun StatCell(
    label: String,
    value: AnnotatedString,
    modifier: Modifier = Modifier,
    valueColor: Color = TG.TextPrimary,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = TGType.chartHeaderLabel, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        Text(
            text = value,
            style = TGType.chartHeaderValue,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The `log` / `auto` pills, drawn in Compose over the canvas' lower-right corner. */
@Composable
private fun ScalePills(
    logScale: Boolean,
    onToggleLog: () -> Unit,
    onAuto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ScalePill(stringResource(R.string.chart_scale_log), active = logScale, onClick = onToggleLog)
        ScalePill(stringResource(R.string.chart_scale_auto), active = false, onClick = onAuto)
    }
}

@Composable
private fun ScalePill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(TGDimens.CHIP_H_DP.dp)
            .clip(PillShape)
            .background(TG.ChipFill)
            .border(1.dp, TG.Outline, PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TGType.toolbarChip,
            color = if (active) TG.TextPrimary else TG.TextSecondary,
            maxLines = 1,
        )
    }
}

/** Bottom toolbar: scrolling timeframe chips · share · candle type · indicators · fullscreen. */
@Composable
private fun ChartToolbar(
    timeframe: Timeframe,
    fullscreen: Boolean,
    height: Dp,
    chipScroll: ScrollState,
    onTimeframe: (Timeframe) -> Unit,
    onShare: () -> Unit,
    onCandleType: () -> Unit,
    onIndicators: () -> Unit,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TG.NavSurface)
            .then(if (fullscreen) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            .height(height),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(chipScroll)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChartPeriods.toolbarOrder.forEach { entry ->
                TimeframeChip(
                    label = entry.label,
                    active = entry == timeframe,
                    onClick = { onTimeframe(entry) },
                )
            }
        }
        VerticalDivider(
            modifier = Modifier.height(height - 16.dp),
            thickness = 1.dp,
            color = TG.Outline,
        )
        ToolbarAction(Icons.Outlined.Share, stringResource(R.string.cd_chart_share), onShare)
        ToolbarAction(TGIcons.CandleType, stringResource(R.string.cd_chart_type), onCandleType)
        ToolbarAction(TGIcons.Indicators, stringResource(R.string.cd_chart_indicators), onIndicators)
        ToolbarAction(
            imageVector = if (fullscreen) TGIcons.FullscreenExit else TGIcons.Fullscreen,
            contentDescription = stringResource(
                if (fullscreen) R.string.cd_chart_fullscreen_exit else R.string.cd_chart_fullscreen,
            ),
            onClick = onFullscreen,
        )
        Spacer(Modifier.width(12.dp))
    }
}

/** A timeframe chip: accent label plus the 3 dp accent underline the watchlist tab row uses. */
@Composable
private fun TimeframeChip(label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(IntrinsicSize.Max)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = label,
            style = TGType.toolbarChip,
            color = if (active) TG.Accent else TG.TextSecondary,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(TabIndicatorShape)
                .background(if (active) TG.Accent else Color.Transparent),
        )
    }
}

@Composable
private fun ToolbarAction(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Spacer(Modifier.width(16.dp))
    TGIconButton(
        imageVector = imageVector,
        contentDescription = contentDescription,
        onClick = onClick,
        tint = TG.TextSecondary,
        size = 20.dp,
    )
}
