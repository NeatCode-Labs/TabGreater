package com.neatcode.tabgreater.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.neatcode.tabgreater.core.data.db.TickerSnapshotDao
import com.neatcode.tabgreater.core.data.repo.MarketRepository
import com.neatcode.tabgreater.core.data.repo.SparklineRepository
import com.neatcode.tabgreater.core.live.MarketDataRepository
import com.neatcode.tabgreater.core.live.WidgetRefresher
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.SparkPeriod
import com.neatcode.tabgreater.core.model.Ticker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The `:widget` side of the [WidgetRefresher] seam: it turns the newest ticker into a
 * [WidgetRenderModel], writes it into each widget's Glance state and asks Glance to repaint.
 *
 * Two rules make a 2-second cadence affordable:
 * the refresher never hits the network itself — it reads whichever of [MarketDataRepository.latest]
 * and the persisted Room snapshot carries the **newer** timestamp ([TickerResolver]) — and it only
 * re-reads the candle cache when `includeSparklines` is set, keeping the previous points otherwise.
 *
 * Everything it does is reconciled against the widgets the host actually owns ([BoundWidgetIds]),
 * so a configuration left behind by a force-stop or a device restore is dropped instead of keeping
 * the live service resident for a widget that is on no home screen.
 */
class GlanceWidgetRefresher internal constructor(
    private val context: Context,
    private val configs: WidgetConfigStore,
    marketData: MarketDataRepository,
    private val markets: MarketRepository,
    snapshots: TickerSnapshotDao,
    private val sparklines: SparklineRepository,
    private val boundIds: BoundWidgetIds = boundWidgetIds(context),
) : WidgetRefresher {

    private val tickers = TickerResolver(marketData, snapshots)

    /**
     * Bumped whenever the host says the widget set may have changed (`onUpdate`, `onDeleted`,
     * `onRestored`). [observeWidgetKeys] is derived from a DataStore flow that does not move when
     * only the *binding* changed, so this is what makes it re-check the bound ids.
     */
    private val revision = MutableStateFlow(0)

    /** Re-runs the bound-id reconciliation for every current collector of [observeWidgetKeys]. */
    fun notifyWidgetsChanged() {
        revision.value++
    }

    override fun observeWidgetKeys(): Flow<Set<MarketKey>> =
        combine(configs.observeAll(), revision) { stored, _ -> stored }
            .map { stored ->
                reconcileConfigs(stored, boundIds.current())
                    .values
                    .mapTo(LinkedHashSet()) { it.key }
            }
            .distinctUntilChanged()

    override suspend fun refreshAll(includeSparklines: Boolean): Int =
        refreshAll(includeSparklines, force = false)

    /**
     * @param force repaint even when the model is byte-for-byte the one already on screen. The
     *   manual "↻" tap uses it so the widget always reacts visibly; the 1-2 s service loop keeps
     *   the default and skips the Binder round trip when nothing moved.
     * @return how many widgets were actually repainted.
     */
    suspend fun refreshAll(includeSparklines: Boolean, force: Boolean): Int {
        val stored = configs.observeAll().first()
        if (stored.isEmpty()) return 0
        val live = reconcileConfigs(stored, boundIds.current())
        dropOrphans(stored.keys - live.keys)
        if (live.isEmpty()) return 0

        val manager = GlanceAppWidgetManager(context)
        val glanceIds = runCatching { manager.getGlanceIds(TickerWidget::class.java) }
            .getOrElse { e ->
                Log.w(TAG, "cannot enumerate widgets", e)
                return 0
            }
        var painted = 0
        for (glanceId in glanceIds) {
            val appWidgetId = manager.getAppWidgetId(glanceId)
            val config = live[appWidgetId] ?: continue
            if (writeState(glanceId, config, includeSparklines, force)) painted++
        }
        if (painted > 0) TickerWidget().updateAll(context)
        return painted
    }

    /**
     * Repaints exactly one widget, used right after the configuration screen saves — at that
     * point the widget may not be enumerable yet, so a failure here is expected and harmless
     * (the provider's `onUpdate` paints it moments later).
     */
    suspend fun refreshOne(appWidgetId: Int, includeSparklines: Boolean) {
        val config = configs.get(appWidgetId) ?: return
        val manager = GlanceAppWidgetManager(context)
        val glanceId = runCatching { manager.getGlanceIdBy(appWidgetId) }.getOrElse { e ->
            Log.w(TAG, "widget $appWidgetId not bound yet", e)
            return
        }
        if (writeState(glanceId, config, includeSparklines, force = false)) {
            TickerWidget().update(context, glanceId)
        }
    }

    /** The newest price for one market, for the configuration screen's live preview. */
    internal suspend fun tickerFor(key: MarketKey): Ticker? = tickers.resolve(key)

    /** The cached 24 h closes for one market — no network, the same read the widget itself does. */
    internal suspend fun sparkFor(key: MarketKey): List<Float> = loadSpark(key)

    /** The price precision of one market, or `null` while the instrument list is not cached yet. */
    internal suspend fun precisionFor(key: MarketKey): Int? =
        runCatching { markets.getMarket(key)?.pricePrecision }.getOrNull()

    /** Configurations whose widget no longer exists; keeping them keeps the live service resident. */
    private suspend fun dropOrphans(orphans: Set<Int>) {
        if (orphans.isEmpty()) return
        for (id in orphans) runCatching { configs.remove(id) }
        Log.i(TAG, "dropped ${orphans.size} orphaned widget config(s)")
    }

    private suspend fun writeState(
        glanceId: GlanceId,
        config: WidgetConfig,
        includeSparklines: Boolean,
        force: Boolean,
    ): Boolean {
        val previous = previousModel(glanceId)
        val ticker = tickers.resolve(config.key)
        val precision = precisionFor(config.key)
        // Read regardless of `showSparkline`: the points also back the 24 h change when the
        // ticker carries none (Kraken over REST), and the widget itself decides whether to draw.
        val spark = if (includeSparklines) loadSpark(config.key) else previous?.spark.orEmpty()
        val model = WidgetModelFactory.build(
            config = config,
            ticker = ticker,
            pricePrecision = precision,
            spark = spark,
            now = System.currentTimeMillis(),
        )
        if (model == previous && !force) return false
        return try {
            updateAppWidgetState(context, glanceId) { it[TickerWidget.MODEL] = WidgetJson.format.encodeToString(model) }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "widget state write failed", e)
            false
        }
    }

    private suspend fun loadSpark(key: MarketKey): List<Float> =
        runCatching { sparklines.cached(key, SparkPeriod.HOURS_24).points.toList() }.getOrElse { emptyList() }

    private suspend fun previousModel(glanceId: GlanceId): WidgetRenderModel? = try {
        getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[TickerWidget.MODEL]
            ?.let { WidgetJson.format.decodeFromString<WidgetRenderModel>(it) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "widget state read failed", e)
        null
    }

    private companion object {
        const val TAG = "TickerWidget"
    }
}
