package com.neatcode.tabgreater.core.live

import com.neatcode.tabgreater.core.model.MarketKey
import kotlinx.coroutines.flow.Flow

/**
 * What the live layer needs from the home-screen widgets (implemented in `:widget`, bound in
 * Koin as `single<WidgetRefresher>`). `:core:live` cannot depend on `:widget`, so the service
 * talks to the widgets only through this seam.
 */
interface WidgetRefresher {
    /** Market keys of every placed widget; empty when there are no widgets (the service then idles/stops). */
    fun observeWidgetKeys(): Flow<Set<MarketKey>>

    /**
     * Re-renders every widget from the newest data available — whichever of the in-memory
     * WebSocket map and the Room snapshot carries the newer timestamp. Sparkline bitmaps are
     * rebuilt only when [includeSparklines] is true; they barely change and cost Binder traffic
     * on every Glance update.
     *
     * @return how many widgets were actually repainted. `0` is the normal outcome of a flat
     *   market (a widget whose model did not change is not re-parcelled to the launcher), so it
     *   does **not** mean the pass failed — it only tells the caller that nothing reached the
     *   home screen this round.
     */
    suspend fun refreshAll(includeSparklines: Boolean): Int
}

/** A [WidgetRefresher] for builds or tests without widgets. */
object NoWidgets : WidgetRefresher {
    override fun observeWidgetKeys(): Flow<Set<MarketKey>> = kotlinx.coroutines.flow.flowOf(emptySet())
    override suspend fun refreshAll(includeSparklines: Boolean): Int = 0
}
