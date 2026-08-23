package com.neatcode.tabgreater

import android.app.Application
import com.neatcode.tabgreater.core.data.dataModule
import com.neatcode.tabgreater.core.data.maintenance.CacheMaintenance
import com.neatcode.tabgreater.core.live.LiveTickerLauncher
import com.neatcode.tabgreater.core.live.liveModule
import com.neatcode.tabgreater.feature.chart.ChartWebViewCache
import com.neatcode.tabgreater.feature.chart.chartModule
import com.neatcode.tabgreater.widget.widgetModule
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class TabGreaterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.INFO else Level.NONE)
            androidContext(this@TabGreaterApp)
            // Configures WorkManager with Koin's worker factory. `:core:live`'s manifest removes
            // the androidx.startup initializer so this is the only initialisation.
            workManagerFactory()
            modules(appModule, dataModule, liveModule, chartModule, widgetModule)
        }
        // Prunes candles/snapshots of markets nothing — no watchlist, no widget — references.
        get<CacheMaintenance>().start()

        // The 15-minute widget refresh runs whether or not live mode is on. The resident
        // LiveTickerService is deliberately *not* started from here: the process is still in the
        // background at this point, so Android 12+ refuses the foreground start (and on the
        // launches where it is allowed, the start competes with cold start for the 5 s
        // startForeground() budget and can kill the process). MainActivity.onStart does it, from
        // a foreground activity, where the exemption always applies.
        LiveTickerLauncher.scheduleSafetyNet(this)
    }

    /**
     * The chart keeps one WebView alive for the whole process. Drop it under real memory
     * pressure — but never at `TRIM_MEMORY_UI_HIDDEN`, which fires every time the app goes to the
     * background and would throw the warm start away on each return.
     *
     * The `ComponentCallbacks2.TRIM_MEMORY_*` constants are deprecated since API 34, so the two
     * levels are spelled out here rather than referenced (this project allows no deprecated API).
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE || level == TRIM_MEMORY_RUNNING_CRITICAL) {
            // RUNNING_CRITICAL is a *foreground* level, so this can fire with the chart on screen.
            // `trim()` ignores the call while a chart screen owns the WebView (finding 23) and only
            // gives the 35-60 MB back when the canvas is not visible.
            ChartWebViewCache.trim()
        }
    }

    private companion object {
        /** `ComponentCallbacks2.TRIM_MEMORY_MODERATE`. */
        const val TRIM_MEMORY_MODERATE = 60

        /** `ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL`. */
        const val TRIM_MEMORY_RUNNING_CRITICAL = 15
    }
}
