package com.neatcode.tabgreater.core.live

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.neatcode.tabgreater.core.data.APP_SCOPE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.core.context.GlobalContext

/**
 * Entry points other modules use to (re)start [LiveTickerService] without knowing its class:
 * app launch, widget placement/removal, boot and package-replaced receivers.
 *
 * Everything is resolved through the global Koin container because the callers are Android
 * components with no injection point of their own.
 */
object LiveTickerLauncher {

    private const val TAG = "LiveLauncher"

    /**
     * Starts the service when at least one widget is placed; otherwise only the 15-minute safety
     * net is scheduled.
     *
     * Safe to call from anywhere and as often as you like — the widget check is async and starting
     * an already running service just makes it re-evaluate its mode.
     */
    fun ensureRunning(context: Context) {
        val app = context.applicationContext
        // Layer 1 of : the floor runs whether or not the service does.
        scheduleSafetyNet(app)
        val koin = koinOrNull() ?: return
        koin.get<CoroutineScope>(APP_SCOPE).launch {
            if (shouldRun(koin)) start(app, ACTION_START)
        }
    }

    /** Called when widgets are added, removed or reconfigured: starts, re-targets or stops the service. */
    fun onWidgetsChanged(context: Context) {
        val app = context.applicationContext
        scheduleSafetyNet(app)
        val koin = koinOrNull() ?: return
        koin.get<CoroutineScope>(APP_SCOPE).launch {
            start(app, if (shouldRun(koin)) ACTION_START else ACTION_STOP)
        }
    }

    /**
     * Enqueues the periodic [WidgetRefreshWorker]. Idempotent (`KEEP` under a unique name), so
     * `TabGreaterApp` and every [ensureRunning] caller may call it freely.
     */
    fun scheduleSafetyNet(context: Context) {
        WidgetRefreshWorker.enqueue(context.applicationContext)
    }

    /**
     * The service runs whenever a widget is placed — both cadences need it: [WidgetRefresh.LIVE]
     * for the sockets, the timed ones for the alarm loop and the `SCREEN_ON` catch-up.
     */
    private suspend fun shouldRun(koin: Koin): Boolean = try {
        koin.widgetRefresher().observeWidgetKeys().first().isNotEmpty()
    } catch (e: Exception) {
        Log.w(TAG, "could not decide whether to run", e)
        false
    }

    private fun start(context: Context, action: String) {
        try {
            ContextCompat.startForegroundService(context, LiveTickerService.intent(context, action))
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) extends IllegalStateException:
            // the process was in the background without an exemption. The heartbeat alarm, the
            // next widget tap or the next app launch will get us there instead.
            Log.w(TAG, "foreground start not allowed", e)
        }
    }

    private fun koinOrNull(): Koin? = GlobalContext.getKoinApplicationOrNull()?.koin
}

/**
 * The widget seam, resolved lazily: `:widget` binds a [WidgetRefresher], builds and tests without
 * widgets fall back to [NoWidgets]. Resolving it optionally keeps `:core:live` self-contained.
 */
internal fun Koin.widgetRefresher(): WidgetRefresher = getOrNull<WidgetRefresher>() ?: NoWidgets

/** Same, for callers that only have an Android component (the service, the worker). */
internal fun resolveWidgetRefresher(): WidgetRefresher =
    GlobalContext.getKoinApplicationOrNull()?.koin?.widgetRefresher() ?: NoWidgets
