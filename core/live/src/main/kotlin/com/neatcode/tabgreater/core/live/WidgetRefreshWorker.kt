package com.neatcode.tabgreater.core.live

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.neatcode.tabgreater.core.data.repo.SparklineRepository
import com.neatcode.tabgreater.core.exchange.canRetryAutomatically
import com.neatcode.tabgreater.core.exchange.exchangeFailureKind
import com.neatcode.tabgreater.core.exchange.ExchangeFailureKind
import com.neatcode.tabgreater.core.model.SparkPeriod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * The safety floor: one REST round plus a full widget render
 * every 15 minutes, **whether or not** [LiveTickerService] is alive and whether or not live mode
 * is enabled. It is what keeps the widget from going stale after an OEM kills the service.
 *
 * This is the only WorkManager job in the app — the service itself never enqueues work while it
 * runs, because Android 16 applies job runtime quotas even to jobs running alongside an FGS.
 */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
    private val marketData: MarketDataRepository,
    private val sparklines: SparklineRepository,
    private val diagnostics: LiveDiagnostics,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val widgets = resolveWidgetRefresher()
        return try {
            val keys = widgets.observeWidgetKeys().first()
            var transientFailure = false
            if (keys.isNotEmpty()) {
                val result = marketData.refreshResult(keys)
                if (result.complete) diagnostics.onRestRound(System.currentTimeMillis())
                else {
                    val error = result.failures.values.firstOrNull() ?: IllegalStateException("Some markets returned no price")
                    diagnostics.onError(WHAT_REST_ROUND, error)
                    transientFailure = result.failures.values.any { it.exchangeFailureKind() == ExchangeFailureKind.TRANSIENT }
                }
                // Without this the 24 h candle window of a widget-only pair stays whatever the
                // configuration screen fetched once, and the sparkline freezes for good.
                try { sparklines.refresh(keys, SparkPeriod.HOURS_24) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    diagnostics.onError("sparkline refresh", e)
                    transientFailure = transientFailure || e.exchangeFailureKind() == ExchangeFailureKind.TRANSIENT
                }
            }
            val painted = widgets.refreshAll(true)
            diagnostics.onWidgetRefresh(System.currentTimeMillis(), painted)
            if (transientFailure) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "periodic widget refresh failed", e)
            diagnostics.onError("periodic refresh", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "WidgetRefreshWork"

        /** Unique name; the request is kept across app launches so its schedule is not reset. */
        const val UNIQUE_NAME = "widget-refresh"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
                REPEAT_MINUTES, TimeUnit.MINUTES,
                FLEX_MINUTES, TimeUnit.MINUTES,
            )
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** WorkManager's minimum period. */
        private const val REPEAT_MINUTES = 15L

        /** WorkManager's minimum flex. */
        private const val FLEX_MINUTES = 5L
    }
}
