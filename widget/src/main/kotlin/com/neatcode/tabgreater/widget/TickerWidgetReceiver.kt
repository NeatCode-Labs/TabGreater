package com.neatcode.tabgreater.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import com.neatcode.tabgreater.core.data.APP_SCOPE
import com.neatcode.tabgreater.core.data.repo.SparklineRepository
import com.neatcode.tabgreater.core.live.LiveTickerLauncher
import com.neatcode.tabgreater.core.live.MarketDataRepository
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.SparkPeriod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * The `AppWidgetProvider` behind the ticker widget.
 *
 * Glance already routes composition asynchronously, so this class only adds the lifecycle wiring
 * the live layer needs: make sure `LiveTickerService` is running while widgets exist, repaint on
 * every `APPWIDGET_UPDATE`, and keep the stored configurations in step with the widgets the host
 * really owns. Broadcast receivers get ~10 s before an ANR, so all of it is handed to the
 * process-wide `APP_SCOPE` instead of being done inline.
 */
class TickerWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = TickerWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        LiveTickerLauncher.ensureRunning(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        LiveTickerLauncher.ensureRunning(context)
        // The binding may have changed since the last emission (a widget removed while the app was
        // force-stopped, a restore), so re-check the bound ids before repainting.
        glanceRefresher()?.notifyWidgetsChanged()
        widgetScope()?.launch {
            runCatching { glanceRefresher()?.refreshAll(includeSparklines = true) }
                .onFailure { Log.w(TAG, "onUpdate refresh failed", it) }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { SparklineCache.forget(it) }
        val scope = widgetScope()
        val store = configStore()
        if (scope == null || store == null) return
        scope.launch {
            runCatching { appWidgetIds.forEach { store.remove(it) } }
                .onFailure { Log.w(TAG, "config cleanup failed", it) }
            glanceRefresher()?.notifyWidgetsChanged()
            // Retarget or stop the live service now that the key set has shrunk.
            LiveTickerLauncher.onWidgetsChanged(context)
        }
    }

    /**
     * Auto-backup restores `widget_configs` under the *old* `appWidgetId`s, which no host owns.
     * Re-keying them to the ids the restore assigned keeps the user's pairs and colours; anything
     * that cannot be mapped is dropped by the bound-id reconciliation on the next refresh
     * (finding 13).
     */
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        val scope = widgetScope()
        val store = configStore()
        if (scope == null || store == null) return
        scope.launch {
            runCatching {
                for (i in oldWidgetIds.indices) {
                    val old = oldWidgetIds[i]
                    val new = newWidgetIds.getOrNull(i) ?: continue
                    if (old == new) continue
                    val config = store.get(old) ?: continue
                    store.put(new, config)
                    store.remove(old)
                }
            }.onFailure { Log.w(TAG, "config restore failed", it) }
            glanceRefresher()?.notifyWidgetsChanged()
            LiveTickerLauncher.onWidgetsChanged(context)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        glanceRefresher()?.notifyWidgetsChanged()
        LiveTickerLauncher.onWidgetsChanged(context)
    }

    private companion object {
        const val TAG = "TickerWidget"
    }
}

/**
 * The "↻" corner tap: a real REST round for the widget pairs, then a forced repaint.
 *
 * The refresher itself is deliberately network-free, so re-reading its caches could not produce a
 * newer price — with live updates off the button used to be a complete no-op (finding 14 / F5-2).
 * `ActionCallback` runs inside the Glance broadcast's `goAsync`, which has the ~10 s broadcast
 * budget: a one- or two-market REST round fits comfortably, and each leg is guarded on its own so
 * one failing exchange still lets the other land.
 */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val refresher = glanceRefresher()
        val keys: Set<MarketKey> =
            runCatching { refresher?.observeWidgetKeys()?.first() }.getOrNull().orEmpty()
        if (keys.isNotEmpty()) {
            runCatching { marketData()?.refresh(keys) }
                .onFailure { Log.w(TAG, "manual ticker round failed", it) }
            runCatching { sparklines()?.refresh(keys, SparkPeriod.HOURS_24) }
                .onFailure { Log.w(TAG, "manual sparkline round failed", it) }
        }
        val painted = runCatching { refresher?.refreshAll(includeSparklines = true, force = true) }
            .onFailure { Log.w(TAG, "manual refresh failed", it) }
            .getOrNull() ?: 0
        Log.i(TAG, "manual refresh: ${keys.size} key(s), $painted widget(s) repainted")
        LiveTickerLauncher.ensureRunning(context)
    }

    private companion object {
        const val TAG = "TickerWidget"
    }
}

/**
 * Koin is started in `TabGreaterApp.onCreate`, which always runs before a broadcast reaches this
 * process — but a null-safe lookup keeps a widget update from crashing the launcher if it ever
 * does not.
 */
internal fun glanceRefresher(): GlanceWidgetRefresher? =
    GlobalContext.getOrNull()?.getOrNull<GlanceWidgetRefresher>()

internal fun widgetScope(): CoroutineScope? =
    GlobalContext.getOrNull()?.getOrNull<CoroutineScope>(APP_SCOPE)

internal fun configStore(): WidgetConfigStore? =
    GlobalContext.getOrNull()?.getOrNull<WidgetConfigStore>()

internal fun marketData(): MarketDataRepository? =
    GlobalContext.getOrNull()?.getOrNull<MarketDataRepository>()

internal fun sparklines(): SparklineRepository? =
    GlobalContext.getOrNull()?.getOrNull<SparklineRepository>()
