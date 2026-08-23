package com.neatcode.tabgreater.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neatcode.tabgreater.core.data.APP_SCOPE
import com.neatcode.tabgreater.core.data.repo.MarketRepository
import com.neatcode.tabgreater.core.data.repo.SparklineRepository
import com.neatcode.tabgreater.core.live.LiveTickerLauncher
import com.neatcode.tabgreater.core.live.MarketDataRepository
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.SparkPeriod
import com.neatcode.tabgreater.core.model.TGColors
import com.neatcode.tabgreater.core.model.TGDimens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.Locale

/**
 * The configuration screen the launcher opens when a ticker widget is placed, and again on
 * "Reconfigure" (the provider declares `widgetFeatures="reconfigurable"`).
 *
 * The flow is: pick the instrument and its exchange, choose the background colour and its
 * transparency, switch the sparkline on or off, confirm — all against a live preview of the
 * widget being configured.
 */
class WidgetConfigActivity : ComponentActivity() {

    private val markets: MarketRepository by inject()
    private val configs: WidgetConfigStore by inject()
    private val refresher: GlanceWidgetRefresher by inject()
    private val marketData: MarketDataRepository by inject()
    private val sparklines: SparklineRepository by inject()
    private val appScope: CoroutineScope by inject(APP_SCOPE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras
            ?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Anything other than an explicit Save must leave the widget unplaced.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(TGColors.BACKGROUND.toInt()),
            navigationBarStyle = SystemBarStyle.dark(TGColors.BACKGROUND.toInt()),
        )
        setContent {
            WidgetConfigTheme {
                WidgetConfigScreen(
                    loadInitial = { configs.get(appWidgetId) },
                    refreshCatalogue = { markets.refreshAll() },
                    search = { query -> markets.search(query, SEARCH_LIMIT) },
                    loadPreview = { key -> previewModel(key) },
                    onSave = { config -> save(appWidgetId, config) },
                    onCancel = { finish() },
                )
            }
        }
    }

    /**
     * The real price, 24 h change and cached 24 h closes of the picked pair, so the preview shows
     * what the widget will actually draw instead of a fixed sample (F5-4). Cache-only, exactly
     * like [GlanceWidgetRefresher]: the configuration screen never blocks on the network, and the
     * sample stands in until the pair is known.
     */
    private suspend fun previewModel(key: MarketKey): WidgetRenderModel? {
        val ticker = refresher.tickerFor(key) ?: return null
        return WidgetModelFactory.build(
            config = WidgetConfig(key),
            ticker = ticker,
            pricePrecision = refresher.precisionFor(key),
            spark = refresher.sparkFor(key),
            now = System.currentTimeMillis(),
        )
    }

    /**
     * Stores the configuration and closes immediately; the process-wide scope then seeds the first
     * price and candle window, so the widget does not sit on "—" until the next live tick.
     */
    private fun save(appWidgetId: Int, config: WidgetConfig) {
        val context = applicationContext
        appScope.launch {
            configs.put(appWidgetId, config)
            refresher.refreshOne(appWidgetId, includeSparklines = true)
            LiveTickerLauncher.onWidgetsChanged(context)
            runCatching { marketData.refresh(listOf(config.key)) }
            runCatching { sparklines.refresh(listOf(config.key), SparkPeriod.HOURS_24) }
            refresher.refreshOne(appWidgetId, includeSparklines = true)
        }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }

    private companion object {
        const val SEARCH_LIMIT = 120
    }
}

/**
 * Every field the user can change is `rememberSaveable`: the activity has no `configChanges`, so
 * rotation, a font-size change or a process kill in the picker used to reset the whole screen to
 * "Pick a pair" (finding 15). [MarketKey] is a value class over `String` and not Parcelable, so
 * the pair travels as its raw key. `seeded` is saved too — otherwise the reconfigure path would
 * re-apply the persisted configuration on top of the restored edits.
 */
@Composable
private fun WidgetConfigScreen(
    loadInitial: suspend () -> WidgetConfig?,
    refreshCatalogue: suspend () -> Unit,
    search: suspend (String) -> List<Market>,
    loadPreview: suspend (MarketKey) -> WidgetRenderModel?,
    onSave: (WidgetConfig) -> Unit,
    onCancel: () -> Unit,
) {
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var background by rememberSaveable { mutableLongStateOf(TGColors.SURFACE) }
    var alpha by rememberSaveable { mutableFloatStateOf(1f) }
    var showSparkline by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    var seeded by rememberSaveable { mutableStateOf(false) }
    var results by remember { mutableStateOf(emptyList<Market>()) }
    var loading by remember { mutableStateOf(true) }
    var preview by remember { mutableStateOf<WidgetRenderModel?>(null) }

    val selected = selectedKey?.let { MarketKey.parseOrNull(it) }

    LaunchedEffect(Unit) {
        if (!seeded) {
            loadInitial()?.let { saved ->
                selectedKey = saved.key.value
                background = saved.backgroundArgb
                alpha = saved.alpha
                showSparkline = saved.showSparkline
            }
            seeded = true
        }
        refreshCatalogue()
        loading = false
    }

    LaunchedEffect(selected) {
        preview = selected?.let { loadPreview(it) }
    }

    LaunchedEffect(query, loading) {
        val text = query.trim()
        if (text.isEmpty()) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        results = search(text)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TW.Background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Text(
            text = stringResource(R.string.widget_config_title),
            style = TWType.title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )

        WidgetPreview(
            key = selected,
            data = preview,
            backgroundArgb = background,
            alpha = alpha,
            showSparkline = showSparkline,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(12.dp))

        SearchField(
            query = query,
            onQueryChange = { query = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (query.isBlank()) {
                AppearancePane(
                    backgroundArgb = background,
                    onBackgroundChange = { background = it },
                    alpha = alpha,
                    onAlphaChange = { alpha = it },
                    showSparkline = showSparkline,
                    onShowSparklineChange = { showSparkline = it },
                )
            } else {
                ResultsPane(
                    results = results,
                    loading = loading,
                    selected = selected,
                    onPick = { market ->
                        selectedKey = market.key.value
                        query = ""
                    },
                )
            }
        }

        HorizontalDivider(color = TW.Outline)
        SaveBar(
            enabled = selected != null,
            onCancel = onCancel,
            onSave = {
                val key = selected
                if (key != null) onSave(WidgetConfig(key, background, alpha, showSparkline))
            },
        )
    }
}

// ---- Preview -------------------------------------------------------------------------------

/**
 * The widget as it will land on the home screen, drawn 1 : 1 with the rules [widgetPlan] uses. Two
 * slots are shown — a 2 × 1 beside a taller one — not because they are different layouts (they are
 * the same picture at two scales, which is the point) but so colour, transparency and the
 * sparkline are judged at both. [data] is the pair's real cached price/change/sparkline once it is
 * known; until then the synthetic sample stands in, so the preview is never empty (F5-4).
 */
@Composable
private fun WidgetPreview(
    key: MarketKey?,
    data: WidgetRenderModel?,
    backgroundArgb: Long,
    alpha: Float,
    showSparkline: Boolean,
    modifier: Modifier = Modifier,
) {
    val fill = Color(backgroundArgb.toInt()).copy(alpha = alpha.coerceIn(0f, 1f))
    val live = data?.takeIf { it.hasData && it.marketKey == key }
    val sample = PreviewData(
        monogram = key?.exchange?.monogram,
        pair = key?.pair ?: stringResource(R.string.widget_config_preview_pair),
        price = live?.price ?: SAMPLE_PRICE,
        change = live?.change ?: SAMPLE_CHANGE,
        up = live?.changeUp ?: true,
        updated = stringResource(
            R.string.widget_updated_at,
            live?.updatedLabel?.takeIf { it.isNotEmpty() } ?: SAMPLE_TIME,
        ),
        points = live?.spark?.takeIf { it.size >= 2 }?.toFloatArray() ?: SAMPLE_SPARK,
        showSparkline = showSparkline,
        fill = fill,
    )
    Column(modifier) {
        Text(stringResource(R.string.widget_config_preview), style = TWType.sectionHeader)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PreviewWidget(
                data = sample,
                slotWidthDp = PREVIEW_ROW_W_DP,
                slotHeightDp = PREVIEW_ROW_H_DP,
                modifier = Modifier.width(PREVIEW_ROW_W_DP.dp),
            )
            PreviewWidget(
                data = sample,
                slotWidthDp = PREVIEW_TALL_W_DP,
                slotHeightDp = PREVIEW_TALL_H_DP,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Everything both preview shapes draw, so the two composables stay signature-free. */
private class PreviewData(
    val monogram: String?,
    val pair: String,
    val price: String,
    val change: String,
    val up: Boolean,
    val updated: String,
    val points: FloatArray,
    val showSparkline: Boolean,
    val fill: Color,
)

/**
 * One preview slot, run through exactly the same [widgetPlan] the launcher's widget runs through,
 * so what the sheet shows is what lands: same padding, same scale, same three bands.
 */
/**
 * The exchange badge, drawn with the fractions [ExchangeBadgeRenderer] rasterises for the widget,
 * so the sheet and the home screen agree down to the corner radius.
 */
@Composable
private fun PreviewBadge(monogram: String, sideDp: Float) {
    Box(
        modifier = Modifier
            .size(sideDp.dp)
            .border(
                width = ExchangeBadgeRenderer.BORDER_DP.dp,
                color = TW.TextTertiary,
                shape = RoundedCornerShape((sideDp * ExchangeBadgeRenderer.CORNER_FRACTION).dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = monogram,
            style = TWType.exchange.copy(
                fontSize = (sideDp * ExchangeBadgeRenderer.FONT_FRACTION).sp,
                lineHeight = (sideDp * ExchangeBadgeRenderer.FONT_FRACTION).sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun PreviewWidget(
    data: PreviewData,
    slotWidthDp: Float,
    slotHeightDp: Float,
    modifier: Modifier = Modifier,
) {
    val padH = horizontalPaddingDp(slotHeightDp)
    val padV = verticalPaddingDp(slotHeightDp)
    val plan = widgetPlan(
        innerWidthDp = slotWidthDp - 2 * padH,
        innerHeightDp = slotHeightDp - 2 * padV,
        pair = data.pair,
        price = data.price,
        change = data.change,
        hasBadge = data.monogram != null,
        hasSparkline = data.showSparkline,
    )
    Box(
        modifier = modifier
            .height(slotHeightDp.dp)
            .clip(RoundedCornerShape(TGDimens.WIDGET_CORNER_DP.dp))
            .checkerboard()
            .background(data.fill)
            .padding(horizontal = padH.dp, vertical = padV.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = data.pair,
                    style = previewPair(plan.pairSp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (plan.showBadge && data.monogram != null) {
                    Spacer(Modifier.width(plan.headerGapDp.dp))
                    PreviewBadge(data.monogram, plan.badgeDp)
                }
            }
            if (plan.hasBand) {
                Spacer(Modifier.height(plan.bandGapDp.dp))
                PreviewSparkline(data.points, data.up, Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(plan.bandGapDp.dp))
            } else {
                Spacer(Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(data.price, style = previewPrice(plan.priceSp), maxLines = 1)
                Spacer(Modifier.width(plan.priceGapDp.dp))
                Spacer(Modifier.weight(1f))
                Text(data.change, style = previewChange(plan.changeSp, data.up), maxLines = 1)
            }
            if (plan.showMeta) {
                Text(
                    text = data.updated,
                    style = previewMeta(plan.metaSp),
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * The 24 h shape in the preview — the pair's cached closes once they are known, the synthetic
 * sample before that — drawn through the same [SparklinePath] maths the widget bitmap uses, at the
 * same [sparkStrokePx] stroke.
 */
@Composable
private fun PreviewSparkline(values: FloatArray, up: Boolean, modifier: Modifier = Modifier) {
    val tint = if (up) TW.Up else TW.Down
    Canvas(modifier) {
        val stroke = sparkStrokePx(density)
        val points = SparklinePath.points(values, size.width, size.height, stroke)
        if (points.isEmpty()) return@Canvas
        val line = Path()
        line.moveTo(points[0], points[1])
        for (i in 2 until points.size step 2) line.lineTo(points[i], points[i + 1])
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                0f to tint.copy(alpha = TGDimens.SPARK_FILL_ALPHA),
                1f to tint.copy(alpha = 0f),
            ),
        )
        drawPath(
            path = line,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** Two-tone grid behind the preview, so a transparent background reads as transparent. */
private fun Modifier.checkerboard(): Modifier = drawBehind {
    val cell = 10.dp.toPx()
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = 0f
        var col = 0
        while (x < size.width) {
            drawRect(
                color = if ((row + col) % 2 == 0) CHECKER_DARK else CHECKER_LIGHT,
                topLeft = Offset(x, y),
                size = Size(minOf(cell, size.width - x), minOf(cell, size.height - y)),
            )
            x += cell
            col++
        }
        y += cell
        row++
    }
}

// ---- Search --------------------------------------------------------------------------------

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    // BasicTextField exposes no label of its own, so the hint is published as the field's name for
    // TalkBack and for uiautomator (F5-4).
    val label = stringResource(R.string.widget_config_search_label)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics { contentDescription = label }
            .clip(RoundedCornerShape(8.dp))
            .background(TW.ChipFill)
            .border(1.dp, TW.Outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (query.isEmpty()) {
            Text(stringResource(R.string.widget_config_search_hint), style = TWType.subtitle)
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TWType.searchInput,
            cursorBrush = SolidColor(TW.Accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ResultsPane(
    results: List<Market>,
    loading: Boolean,
    selected: MarketKey?,
    onPick: (Market) -> Unit,
) {
    if (results.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(
                    if (loading) R.string.widget_config_loading else R.string.widget_config_no_match,
                ),
                style = TWType.subtitle,
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(results, key = { it.key.value }) { market ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(market) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = market.key.exchange.displayName.uppercase(),
                    style = TWType.exchange,
                    modifier = Modifier.width(62.dp),
                )
                Text(market.key.pair, style = TWType.row, maxLines = 1, modifier = Modifier.weight(1f))
                if (market.key == selected) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(TW.Accent))
                }
            }
        }
    }
}

// ---- Appearance ----------------------------------------------------------------------------

@Composable
private fun AppearancePane(
    backgroundArgb: Long,
    onBackgroundChange: (Long) -> Unit,
    alpha: Float,
    onAlphaChange: (Float) -> Unit,
    showSparkline: Boolean,
    onShowSparklineChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(stringResource(R.string.widget_config_background), style = TWType.sectionHeader)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(WidgetConfig.BACKGROUNDS) { argb ->
                val picked = argb == backgroundArgb
                // A bare colour circle has nothing to announce, so each swatch names itself by its
                // hex value and reports whether it is the chosen one (F5-4).
                val label = stringResource(R.string.widget_config_background_swatch, hexOf(argb))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(argb.toInt()))
                        .border(
                            width = if (picked) 2.dp else 1.dp,
                            color = if (picked) TW.Accent else TW.Outline,
                            shape = CircleShape,
                        )
                        .semantics {
                            contentDescription = label
                            selected = picked
                        }
                        .clickable { onBackgroundChange(argb) },
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.widget_config_transparency),
                style = TWType.sectionHeader,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.widget_config_percent, ((1f - alpha) * 100f).toInt()),
                style = TWType.subtitle,
            )
        }
        Slider(
            value = 1f - alpha,
            onValueChange = { onAlphaChange(1f - it) },
            colors = SliderDefaults.colors(
                thumbColor = TW.Accent,
                activeTrackColor = TW.Accent,
                inactiveTrackColor = TW.Outline,
            ),
        )

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.widget_config_sparkline), style = TWType.row)
                Text(stringResource(R.string.widget_config_sparkline_note), style = TWType.subtitle)
            }
            Switch(
                checked = showSparkline,
                onCheckedChange = onShowSparklineChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TW.Scrim,
                    checkedTrackColor = TW.Accent,
                    checkedBorderColor = TW.Accent,
                    uncheckedThumbColor = TW.TextSecondary,
                    uncheckedTrackColor = TW.ChipFill,
                    uncheckedBorderColor = TW.Outline,
                ),
            )
        }
    }
}

// ---- Save bar ------------------------------------------------------------------------------

@Composable
private fun SaveBar(enabled: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.widget_config_cancel),
            style = TWType.row.copy(color = TW.TextSecondary),
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .clickable(onClick = onCancel)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .height(TGDimens.FAB_H_DP.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (enabled) TW.Accent else TW.Outline)
                .clickable(enabled = enabled, onClick = onSave)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.widget_config_save), style = TWType.button)
        }
    }
}

/** `#RRGGBB` of an opaque swatch, the shortest thing a screen reader can say about a colour. */
private fun hexOf(argb: Long): String = String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)

private const val SEARCH_DEBOUNCE_MS = 200L
private const val SAMPLE_PRICE = "65,609.70"
private const val SAMPLE_CHANGE = "+6.52%"
private const val SAMPLE_TIME = "12:34:56"

/** A 2 × 1 slot on a five-column launcher, i.e. the shape the owner actually places. */
private const val PREVIEW_ROW_W_DP = 168f
private const val PREVIEW_ROW_H_DP = 92f

/** A taller slot, where the widget earns its "Updated" line; the width is whatever is left. */
private const val PREVIEW_TALL_W_DP = 150f
private const val PREVIEW_TALL_H_DP = 150f

/**
 * The widget's type scale, borrowed by the preview. `lineHeight` is pinned to the widget's own
 * [LINE_FACTOR] — the config sheet draws with `includeFontPadding = false` while Glance cannot —
 * so the bands land at the same heights on both sides.
 */
private fun previewPair(sp: Float) = TWType.pair.copy(fontSize = sp.sp, lineHeight = (sp * LINE_FACTOR).sp)

private fun previewMeta(sp: Float) = TWType.meta.copy(fontSize = sp.sp, lineHeight = (sp * LINE_FACTOR).sp)

private fun previewPrice(sp: Float) =
    TWType.price.copy(fontSize = sp.sp, lineHeight = (sp * LINE_FACTOR).sp)

private fun previewChange(sp: Float, up: Boolean) =
    TWType.change.copy(fontSize = sp.sp, lineHeight = (sp * LINE_FACTOR).sp, color = if (up) TW.Up else TW.Down)

private val CHECKER_DARK = Color(0xFF1A1B1B)
private val CHECKER_LIGHT = Color(0xFF242525)
private val SAMPLE_SPARK = floatArrayOf(
    12f, 13f, 12.4f, 14f, 15.2f, 14.6f, 16f, 15.4f, 17f, 18.2f,
    17.4f, 18.8f, 20f, 19.2f, 21f, 20.4f, 22f, 23.4f, 22.6f, 24f,
)
