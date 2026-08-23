package com.neatcode.tabgreater.core.live

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Everything the Settings screen needs to answer "why is my widget not updating?".
 *
 * Timestamps are wall-clock epoch millis (`0` = never); [nextHeartbeatInMs] is a duration because
 * the underlying alarm is scheduled on the elapsed-realtime clock.
 */
data class LiveDiagnosticsState(
    val serviceRunning: Boolean = false,
    /** `null` while the service is not running. */
    val mode: TickerMode? = null,
    val transport: Transport = Transport.NONE,
    val screenInteractive: Boolean = true,
    val charging: Boolean = false,
    val dataSaver: Boolean = false,
    val powerSave: Boolean = false,
    /** Aggregate WebSocket state across the exchanges the widgets need. */
    val streamStatus: LiveStatus = LiveStatus.OFFLINE,
    val widgetCount: Int = 0,
    val lastWidgetRefreshAt: Long = 0L,
    val lastRestRoundAt: Long = 0L,
    val lastError: String? = null,
    /**
     * Which operation produced [lastError] — [WHAT_REST_ROUND], [WHAT_WIDGET_REFRESH] or another
     * label. Only that same operation succeeding again clears the message: a widget render says
     * nothing about the REST round that failed a millisecond earlier.
     */
    val lastErrorWhat: String? = null,
    val ignoringBatteryOptimizations: Boolean = false,
    val canScheduleExactAlarms: Boolean = false,
    /** Millis until the watchdog alarm fires, or `null` when none is armed. */
    val nextHeartbeatInMs: Long? = null,
)

/**
 * Process-wide diagnostics of the live layer. [LiveTickerService] and [WidgetRefreshWorker] push
 * into it; the Settings screen collects [state].
 *
 * The mutators are `internal` on purpose — `:core:live` owns the truth, `:app` only reads it.
 */
class LiveDiagnostics(private val context: Context) {

    private val _state = MutableStateFlow(LiveDiagnosticsState())
    val state: StateFlow<LiveDiagnosticsState> = _state.asStateFlow()

    /** True when the user granted the battery allowlist (lifts Doze's while-idle alarm quota). */
    val isIgnoringBatteryOptimizations: Boolean
        get() = context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

    internal fun onServiceRunning(running: Boolean) = _state.update {
        it.copy(
            serviceRunning = running,
            mode = if (running) it.mode else null,
            ignoringBatteryOptimizations = isIgnoringBatteryOptimizations,
        )
    }

    internal fun onEnvironment(
        mode: TickerMode?,
        transport: Transport,
        screenInteractive: Boolean,
        charging: Boolean,
        dataSaver: Boolean,
        powerSave: Boolean,
        widgetCount: Int,
        canScheduleExactAlarms: Boolean,
    ) = _state.update {
        it.copy(
            mode = mode,
            transport = transport,
            screenInteractive = screenInteractive,
            charging = charging,
            dataSaver = dataSaver,
            powerSave = powerSave,
            widgetCount = widgetCount,
            canScheduleExactAlarms = canScheduleExactAlarms,
            ignoringBatteryOptimizations = isIgnoringBatteryOptimizations,
        )
    }

    internal fun onStreamStatus(status: LiveStatus) = _state.update { it.copy(streamStatus = status) }

    internal fun onWidgetRefresh(atEpochMs: Long, painted: Int) =
        _state.update { it.withWidgetRefresh(atEpochMs, painted) }

    internal fun onRestRound(atEpochMs: Long) = _state.update { it.withRestRound(atEpochMs) }

    internal fun onHeartbeatArmed(inMs: Long?) = _state.update { it.copy(nextHeartbeatInMs = inMs) }

    internal fun onError(what: String, error: Throwable) = _state.update { it.withError(what, error) }
}

/** Label of the REST round, shared by the failure message and the recovery that clears it. */
const val WHAT_REST_ROUND: String = "REST round"

/** Label of a widget render pass. */
const val WHAT_WIDGET_REFRESH: String = "widget refresh"

/**
 * One completed widget render pass, [painted] widgets of which actually reached the launcher.
 *
 * The timestamp is recorded for every pass, not only when something changed: an unchanged model is
 * deliberately not re-parcelled to the launcher, so gating on [painted] would stall "Last update"
 * for as long as the market is flat. [painted] only decides whether a previous *widget* failure
 * counts as recovered.
 */
internal fun LiveDiagnosticsState.withWidgetRefresh(atEpochMs: Long, painted: Int): LiveDiagnosticsState =
    copy(lastWidgetRefreshAt = atEpochMs)
        .clearErrorOf(if (painted > 0) WHAT_WIDGET_REFRESH else null)

/** One completed REST round; it clears a previous REST failure and nothing else. */
internal fun LiveDiagnosticsState.withRestRound(atEpochMs: Long): LiveDiagnosticsState =
    copy(lastRestRoundAt = atEpochMs).clearErrorOf(WHAT_REST_ROUND)

internal fun LiveDiagnosticsState.withError(what: String, error: Throwable): LiveDiagnosticsState =
    copy(
        lastError = "$what: ${error.javaClass.simpleName} ${error.message.orEmpty()}".trim(),
        lastErrorWhat = what,
    )

/**
 * Drops [LiveDiagnosticsState.lastError] only when it came from [what].
 *
 * A widget render succeeding says nothing about the REST round that failed a millisecond earlier,
 * and in LIVE mode a render runs every 2 s — clearing the message from there made a failing REST
 * round invisible on the Settings screen.
 */
private fun LiveDiagnosticsState.clearErrorOf(what: String?): LiveDiagnosticsState =
    if (what != null && lastErrorWhat == what) copy(lastError = null, lastErrorWhat = null) else this

/**
 * Deep links the Settings screen offers for the two OS-level knobs the live service depends on
 *. Both return `null` when the setting is already in the desired state or
 * the device has no such screen.
 */
object LiveIntents {

    /**
     * One-tap "allow TabGreater to run in the background" dialog. Play forbids this intent;
     * a sideloaded personal app is exactly the case the permission exists for.
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context): Intent? {
        val power = context.getSystemService(PowerManager::class.java) ?: return null
        if (power.isIgnoringBatteryOptimizations(context.packageName)) return null
        return Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.fromParts("package", context.packageName, null),
        )
    }

    /** Fallback list screen, for OEMs that suppress the one-tap dialog. */
    fun batteryOptimizationSettings(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** "Alarms & reminders" screen; `null` below Android 12 or when access is already granted. */
    fun requestExactAlarmAccess(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val alarms = context.getSystemService(android.app.AlarmManager::class.java) ?: return null
        if (alarms.canScheduleExactAlarms()) return null
        return Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.fromParts("package", context.packageName, null),
        )
    }
}
