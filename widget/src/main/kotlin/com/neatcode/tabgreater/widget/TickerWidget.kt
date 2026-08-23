package com.neatcode.tabgreater.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalGlanceId
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.neatcode.tabgreater.core.model.TGColors
import com.neatcode.tabgreater.core.model.TGDimens

/**
 * The home-screen ticker widget: **one pair per widget**, configured with
 * [WidgetConfigActivity] and repainted by [GlanceWidgetRefresher].
 *
 * `provideGlance` is deliberately a pure read of the widget's Glance state — the JSON
 * [WidgetRenderModel] the refresher stored. Nothing here touches Room, the network or Koin, so the
 * launcher can re-inflate the widget after a process death without showing a loading state.
 *
 * `SizeMode.Exact`: `LocalSize.current` is the launcher's real slot, not a declared breakpoint, so
 * every home grid — the Pixel's and One UI's five-column one alike — gets the *same* picture,
 * scaled to the space it actually has ([widgetPlan]).
 */
class TickerWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Read once per update rather than per recomposition: it is a binder call, and the factor
        // is a property of the launcher, not of the price that just ticked.
        val hostRatio = hostDrawRatio(context, id)
        provideContent { TickerWidgetContent(hostRatio) }
    }

    /**
     * What fraction of the reported slot this host actually draws in ([hostDrawRatio]).
     *
     * The key is a launcher's own, so its type is whatever that launcher put there: One UI writes a
     * float, another host could write a double. `Bundle.get` would cover both but is deprecated, so
     * both typed getters are tried — each returns its default when the stored type does not match.
     */
    private fun hostDrawRatio(context: Context, id: GlanceId): Float = runCatching {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
            ?: return@runCatching 1f
        if (!options.containsKey(OPTION_HOST_DRAW_RATIO)) return@runCatching 1f
        val asDouble = options.getDouble(OPTION_HOST_DRAW_RATIO, Double.NaN)
        if (!asDouble.isNaN()) {
            hostDrawRatio(asDouble)
        } else {
            hostDrawRatio(options.getFloat(OPTION_HOST_DRAW_RATIO, Float.NaN))
        }
    }.getOrDefault(1f)

    companion object {
        /** The one preferences key of the widget state: the render model as JSON. */
        val MODEL: Preferences.Key<String> = stringPreferencesKey("render_model")
    }
}

@Composable
private fun TickerWidgetContent(hostRatio: Float) {
    val context = LocalContext.current
    val model = currentState(TickerWidget.MODEL)?.let { decodeModel(it) }
    val background = ColorProvider(Color(model?.backgroundArgb ?: TGColors.SURFACE.toInt()))
    val slot = drawnSlotOf(LocalSize.current, hostRatio)
    val padH = horizontalPaddingDp(slot.height.value)
    val padV = verticalPaddingDp(slot.height.value)
    val root = GlanceModifier
        .fillMaxSize()
        .appWidgetBackground()
        .background(background)
        .roundedCorners()

    if (model == null) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(LocalGlanceId.current)
        Box(
            modifier = root
                .clickable(actionStartActivity(configureIntent(context, appWidgetId)))
                .padding(horizontal = padH.dp, vertical = padV.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(context.getString(R.string.widget_tap_to_configure), style = Styles.placeholder)
        }
        return
    }

    val innerWidth = slot.width.value - 2 * padH
    val innerHeight = slot.height.value - 2 * padV
    val monogram = model.marketKey?.exchange?.monogram
    val plan = widgetPlan(
        innerWidthDp = innerWidth,
        innerHeightDp = innerHeight,
        pair = model.pair,
        price = model.price,
        change = model.change,
        hasBadge = monogram != null,
        hasSparkline = model.hasSparkline,
        fontScale = context.resources.configuration.fontScale,
    )

    Box(modifier = root.clickable(actionStartActivity(chartIntent(context, model.key)))) {
        Box(modifier = GlanceModifier.fillMaxSize().padding(horizontal = padH.dp, vertical = padV.dp)) {
            TickerBody(model, plan, innerWidth, monogram)
        }
        // The refresh target floats above the content instead of sitting in the header row: a
        // 36 dp tappable box would otherwise eat a third of a one-cell widget (F5-5). A short
        // widget has no room for even the glyph, and its whole surface is already a tap target.
        if (plan.showMeta) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                RefreshButton(padH)
            }
        }
    }
}

/**
 * The one composition, at every size: `EXCHANGE PAIR` on top, the sparkline taking every dp the
 * text leaves, `price … ±%` at the bottom, and — only where a widget is tall enough to spare the
 * line — "Updated hh:mm:ss" under it. Nothing overlaps the chart and nothing is ellipsised: the
 * type is already scaled to fit by [widgetPlan].
 */
@Composable
private fun TickerBody(
    model: WidgetRenderModel,
    plan: WidgetPlan,
    innerWidth: Float,
    monogram: String?,
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        HeaderLine(model, plan, monogram)
        if (plan.hasBand) {
            Spacer(GlanceModifier.height(plan.bandGapDp.dp))
            SparklineImage(
                model = model,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                widthDp = innerWidth,
                heightDp = plan.bandHeightDp,
            )
            Spacer(GlanceModifier.height(plan.bandGapDp.dp))
        } else {
            Spacer(GlanceModifier.defaultWeight())
        }
        PriceLine(model, plan)
        MetaLine(model, plan)
    }
}

/**
 * `BTC/USDT [BN]` — the pair, then the exchange badge. The pair leads so that it and the price
 * below it share one left edge; the badge is the monogram the watchlist tiles draw
 * ([ExchangeBadgeRenderer]) and says what `BINANCE` said in a third of the width, which is what
 * keeps a long pair off the ellipsis.
 */
@Composable
private fun HeaderLine(model: WidgetRenderModel, plan: WidgetPlan, monogram: String?) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(model.pair, style = Styles.pair(plan.pairSp), maxLines = 1)
        if (plan.showBadge && monogram != null) {
            Spacer(GlanceModifier.width(plan.headerGapDp.dp))
            ExchangeBadge(monogram, model.exchange, plan.badgeDp)
        }
        Spacer(GlanceModifier.defaultWeight())
        // Room for the floating "↻" so a long pair never runs under it.
        if (plan.showMeta) Spacer(GlanceModifier.width(REFRESH_RESERVE_DP.dp))
    }
}

/** The two-letter monogram in its hairline box, rasterised because RemoteViews has no border. */
@Composable
private fun ExchangeBadge(monogram: String, exchange: String, sideDp: Float) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val bitmap = ExchangeBadgeCache.bitmap(
        monogram = monogram,
        sidePx = (sideDp * density + 0.5f).toInt(),
        argb = TGColors.TEXT_TERTIARY.toInt(),
        strokePx = badgeStrokePx(density),
    ) ?: return
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = exchange,
        modifier = GlanceModifier.size(sideDp.dp),
    )
}

/**
 * `price … ±%` on one baseline, the change pushed to the far edge. The price is `wrap_content` and
 * comes first, so when the width estimate in [widgetPlan] is off by a hair it is the percentage
 * that loses a pixel, never the price.
 */
@Composable
private fun PriceLine(model: WidgetRenderModel, plan: WidgetPlan) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.Bottom,
    ) {
        Text(model.price, style = Styles.price(plan.priceSp, model.stale), maxLines = 1)
        Spacer(GlanceModifier.width(plan.priceGapDp.dp))
        Spacer(GlanceModifier.defaultWeight())
        Text(
            model.change,
            style = Styles.change(plan.changeSp, model.changeUp, model.hasChange, model.stale),
            maxLines = 1,
        )
    }
}

/** "Updated hh:mm:ss" — the freshness line, on widgets tall enough to carry it. */
@Composable
private fun MetaLine(model: WidgetRenderModel, plan: WidgetPlan) {
    if (!plan.showMeta || model.updatedLabel.isEmpty()) return
    val context = LocalContext.current
    Text(
        text = context.getString(R.string.widget_updated_at, model.updatedLabel),
        style = Styles.meta(plan.metaSp),
        maxLines = 1,
        modifier = GlanceModifier.fillMaxWidth(),
    )
}

/**
 * The manual refresh. The glyph is pinned to the top-right corner so it lines up with the header
 * line, while the tappable view stays a [REFRESH_TOUCH_DP] dp square reaching down and inwards —
 * the old inline `Text` was a 15 × 11 dp target (F5-5).
 */
@Composable
private fun RefreshButton(padH: Float) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .size(REFRESH_TOUCH_DP.dp)
            .clickable(actionRunCallback<RefreshAction>()),
        contentAlignment = Alignment.TopEnd,
    ) {
        Text(
            text = context.getString(R.string.widget_refresh_glyph),
            style = Styles.refresh,
            maxLines = 1,
            modifier = GlanceModifier.padding(top = 4.dp, end = padH.dp),
        )
    }
}

/**
 * [widthDp]/[heightDp] are the bitmap's resolution **and** its slot: they are derived from the size
 * the launcher reported, so `ContentScale.FillBounds` has nothing left to stretch. The raster is
 * cached per widget id ([SparklineCache]) because Glance recomposes from scratch on every tick.
 */
@Composable
private fun SparklineImage(
    model: WidgetRenderModel,
    modifier: GlanceModifier,
    widthDp: Float,
    heightDp: Float,
) {
    if (!model.hasSparkline) return
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val widthPx = (widthDp.coerceAtLeast(MIN_SPARK_DP) * density + 0.5f).toInt()
    val heightPx = (heightDp.coerceAtLeast(MIN_SPARK_DP) * density + 0.5f).toInt()
    val lineArgb = (if (model.changeUp) TGColors.UP else TGColors.DOWN).toInt()

    val bitmap = SparklineCache.bitmap(
        widgetId = currentWidgetId(),
        values = model.spark.toFloatArray(),
        widthPx = widthPx,
        heightPx = heightPx,
        lineArgb = lineArgb,
        backgroundArgb = model.backgroundArgb,
        strokePx = sparkStrokePx(density),
    ) ?: return

    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = modifier,
    )
}

/** The host's `appWidgetId`, or 0 when the composition is not backed by a real widget. */
@Composable
private fun currentWidgetId(): Int {
    val context = LocalContext.current
    val glanceId = LocalGlanceId.current
    return runCatching { GlanceAppWidgetManager(context).getAppWidgetId(glanceId) }.getOrDefault(0)
}

/** There is a sparkline to draw: the user asked for one and the candle cache had something. */
private val WidgetRenderModel.hasSparkline: Boolean
    get() = showSparkline && spark.size >= 2

/**
 * Glance's `cornerRadius` is API 31+; below that a widget background is square, which is what
 * pre-Android-12 launchers draw anyway.
 */
private fun GlanceModifier.roundedCorners(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        cornerRadius(TGDimens.WIDGET_CORNER_DP.dp)
    } else {
        this
    }

/**
 * `tabgreater://chart/binance%3ABTC%2FEUR` — the chart deep link, restricted to this package so no
 * other app can be trampolined into.
 */
private fun chartIntent(context: Context, key: String): Intent =
    Intent(Intent.ACTION_VIEW, "$CHART_URI${Uri.encode(key)}".toUri())
        .setPackage(context.packageName)

/**
 * Reconfiguring an unconfigured widget. The data URI makes the `PendingIntent` unique per widget,
 * otherwise the launcher would reuse one intent (and one extras bundle) for every instance.
 */
private fun configureIntent(context: Context, appWidgetId: Int): Intent =
    Intent(context, WidgetConfigActivity::class.java)
        .setData("$CONFIG_URI$appWidgetId".toUri())
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun decodeModel(raw: String): WidgetRenderModel? = try {
    WidgetJson.format.decodeFromString<WidgetRenderModel>(raw)
} catch (_: IllegalArgumentException) {
    null
}

private const val CHART_URI = "tabgreater://chart/"
private const val CONFIG_URI = "tabgreater://widget/config/"

/** Square tap target of the "↻" glyph; the glyph itself stays at [Styles.refresh]'s size. */
private const val REFRESH_TOUCH_DP = 36f

/** Widget typography mirrors the tile's type scale; Glance has no `includeFontPadding` knob. */
private object Styles {
    private val tertiary = ColorProvider(Color(TGColors.TEXT_TERTIARY.toInt()))
    private val primary = ColorProvider(Color(TGColors.TEXT_PRIMARY.toInt()))
    private val secondary = ColorProvider(Color(TGColors.TEXT_SECONDARY.toInt()))
    private val dimmed = ColorProvider(Color(TGColors.TEXT_PRIMARY.toInt()).copy(alpha = 0.5f))

    val refresh = TextStyle(color = tertiary, fontSize = 13.sp, textAlign = TextAlign.Center)
    val placeholder = TextStyle(color = secondary, fontSize = 13.sp, textAlign = TextAlign.Center)

    fun pair(sp: Float) = TextStyle(color = primary, fontSize = sp.sp)

    fun meta(sp: Float) = TextStyle(color = tertiary, fontSize = sp.sp, textAlign = TextAlign.End)

    fun price(sp: Float, stale: Boolean) =
        TextStyle(color = if (stale) dimmed else primary, fontSize = sp.sp)

    /** An unknown change (the em dash) is neutral — like the chart header, not a green gain. */
    fun change(sp: Float, up: Boolean, known: Boolean, stale: Boolean): TextStyle {
        val base = Color((if (!known) TGColors.TEXT_PRIMARY else if (up) TGColors.UP else TGColors.DOWN).toInt())
        return TextStyle(
            color = ColorProvider(if (stale) base.copy(alpha = 0.5f) else base),
            fontSize = sp.sp,
        )
    }
}
