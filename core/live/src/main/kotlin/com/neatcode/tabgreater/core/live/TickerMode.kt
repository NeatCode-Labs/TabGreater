package com.neatcode.tabgreater.core.live

import com.neatcode.tabgreater.core.model.MarketKey

/**
 * Transport of the current default network, as reported by `ConnectivityManager`.
 *
 * Transport is **not** the metering source: a phone hotspot and a Wi-Fi the user marked "Metered"
 * are both [WIFI] yet cost exactly what cellular costs. Metering is carried separately in
 * [LiveConditions.unmetered], derived from `NET_CAPABILITY_NOT_METERED`.
 */
enum class Transport {
    NONE,
    CELLULAR,
    WIFI,
    ETHERNET,
}

/**
 * The four cadences of [LiveTickerService].
 *
 * [TICK] is the whole of a timed [WidgetRefresh]; the other three are the states the
 * [WidgetRefresh.LIVE] setting moves between as the screen, the link and the power state change.
 */
enum class TickerMode {
    /** Live setting, screen on, link the user is willing to pay for: sockets open, 2 s re-render. */
    LIVE,

    /** Live setting, screen on, metered link with "Live only on Wi-Fi": REST every 15 s instead. */
    NEAR,

    /**
     * Live setting, but nobody is looking (screen off) or the OS says stop (Data Saver, battery
     * saver, no network): sockets released, one REST tick every [LIVE_SCREEN_OFF_TICK_MS].
     */
    SLEEP,

    /** A timed [WidgetRefresh]: no sockets ever, one REST tick per `widgetRefresh.intervalMs`. */
    TICK,
}

/** Everything the mode decision depends on. Pure data so the truth table can be unit tested. */
data class LiveConditions(
    /** At least one home-screen widget is placed. */
    val hasWidgets: Boolean,
    /** Screen on **and** the keyguard not showing — a locked screen is treated as screen off. */
    val screenInteractive: Boolean,
    val transport: Transport,
    val charging: Boolean,
    /** `RESTRICT_BACKGROUND_STATUS_ENABLED`: metered background traffic is forbidden. */
    val dataSaver: Boolean,
    val powerSave: Boolean,
    /**
     * `NET_CAPABILITY_NOT_METERED` on the active network — the only input that decides whether the
     * link is free. Defaults to `false` so an unknown network is treated as expensive.
     */
    val unmetered: Boolean = false,
    /** The user's widget cadence; anything but [WidgetRefresh.LIVE] means [TickerMode.TICK]. */
    val widgetRefresh: WidgetRefresh = DEFAULT_WIDGET_REFRESH,
    /**
     * The user's "Live only on Wi-Fi" switch. It governs the *link*, so charging cannot buy its
     * way past it: a charger pays for the radio, never for the data plan.
     */
    val wifiOnly: Boolean = DEFAULT_WIFI_ONLY,
)

/** What the service actually runs in a given mode. `0` disables a loop. */
data class Cadence(
    /** Collect [MarketDataRepository.observeTickers], i.e. hold the exchange WebSockets. */
    val useSockets: Boolean,
    /** Spacing of `WidgetRefresher.refreshAll(false)`. */
    val widgetRefreshMs: Long,
    /** Spacing of the more expensive `refreshAll(true)` that rebuilds sparkline bitmaps. */
    val sparklineRefreshMs: Long,
    /** Spacing of an explicit `MarketDataRepository.refresh(keys)` round when no socket is held. */
    val restRoundMs: Long,
    /** Arm the `setExactAndAllowWhileIdle` tick instead of running an in-process loop. */
    val sleepTick: Boolean,
    /** Requested spacing of that alarm; `0` when [sleepTick] is `false`. */
    val tickIntervalMs: Long,
)

/**
 * The mode state machine, as a pure function of [LiveConditions] plus the cadence table.
 *
 * Ordering matters and is deliberate:
 * 1. no widget to update → the service stops itself (nothing else in the app depends on it);
 * 2. a timed [WidgetRefresh] → [TickerMode.TICK], whatever the screen and the link are doing —
 *    a short REST round is too cheap to be worth gating on Data Saver or battery saver, and the
 *    alarm path is the same one the OS already stretches under Doze;
 * 3. no usable network → [TickerMode.SLEEP] (the alarm tick retries cheaply);
 * 4. Data Saver on a metered link, or battery saver while unplugged → [TickerMode.SLEEP];
 * 5. screen off → [TickerMode.SLEEP]: nobody looks at a widget behind a locked screen, and a
 *    5-minute tick means the first glance after unlocking is never stale by more than that;
 * 6. screen on → [TickerMode.LIVE] on a link the user is willing to pay for, else
 *    [TickerMode.NEAR].
 *
 * "Willing to pay for" is [LiveConditions.unmetered] or [LiveConditions.wifiOnly] being off.
 * Charging deliberately does **not** enter into it: a charger pays for the radio, never for the
 * data plan.
 */
object TickerModeCalculator {

    /** The mode to run in, or `null` when [LiveTickerService] should stop itself. */
    fun mode(c: LiveConditions): TickerMode? {
        if (!c.hasWidgets) return null
        if (c.widgetRefresh != WidgetRefresh.LIVE) return TickerMode.TICK
        if (c.transport == Transport.NONE) return TickerMode.SLEEP
        if (c.dataSaver && !c.unmetered) return TickerMode.SLEEP
        if (c.powerSave && !c.charging) return TickerMode.SLEEP
        if (!c.screenInteractive) return TickerMode.SLEEP
        return if (c.unmetered || !c.wifiOnly) TickerMode.LIVE else TickerMode.NEAR
    }

    /** Loop intervals for [mode] under the current [settings]. */
    fun cadence(mode: TickerMode, settings: LiveSettingsValues): Cadence = when (mode) {
        TickerMode.LIVE -> Cadence(
            useSockets = true,
            widgetRefreshMs = WidgetRefresh.LIVE.intervalMs,
            sparklineRefreshMs = SPARKLINE_REFRESH_MS,
            restRoundMs = 0L,
            sleepTick = false,
            tickIntervalMs = 0L,
        )
        // On a metered link the sockets are the expensive part, not the requests: with `wifiOnly`
        // on we poll REST every 15 s and still re-render the widget every 10 s from the cache.
        TickerMode.NEAR -> Cadence(
            useSockets = false,
            widgetRefreshMs = NEAR_WIDGET_REFRESH_MS,
            sparklineRefreshMs = SPARKLINE_REFRESH_MS,
            restRoundMs = NEAR_REST_ROUND_MS,
            sleepTick = false,
            tickIntervalMs = 0L,
        )
        // Screen off (or the OS asking us to stop): the socket goes, the alarm takes over, and a
        // SCREEN_ON re-runs the session, which reconnects within a couple of seconds.
        TickerMode.SLEEP -> Cadence(
            useSockets = false,
            widgetRefreshMs = 0L,
            sparklineRefreshMs = 0L,
            restRoundMs = 0L,
            sleepTick = true,
            tickIntervalMs = LIVE_SCREEN_OFF_TICK_MS,
        )
        TickerMode.TICK -> Cadence(
            useSockets = false,
            widgetRefreshMs = 0L,
            sparklineRefreshMs = 0L,
            restRoundMs = 0L,
            sleepTick = true,
            tickIntervalMs = settings.widgetRefresh.intervalMs,
        )
    }

    /** Spacing of the alarm tick in [mode], or `0` when that mode runs in-process loops instead. */
    fun tickIntervalMs(mode: TickerMode, settings: LiveSettingsValues): Long =
        cadence(mode, settings).tickIntervalMs

    /**
     * Whether [mode] runs off the exact alarm rather than in-process loops.
     *
     * In those modes the alarm is also the watchdog and no heartbeat may be armed beside it — both
     * are `…AndAllowWhileIdle` and share one per-app Doze budget ([MIN_SLEEP_TICK_MS]). Settings
     * cannot change the answer, which is why this is readable without them; `TickerModeCalculatorTest`
     * holds it to [Cadence.sleepTick].
     */
    fun usesTickAlarm(mode: TickerMode): Boolean = when (mode) {
        TickerMode.SLEEP, TickerMode.TICK -> true
        TickerMode.LIVE, TickerMode.NEAR -> false
    }

    /**
     * The markets whose exchange sockets have to be held right now — [keys] in a socket mode,
     * empty otherwise.
     *
     * [LiveTickerService] drives its socket collection off this value alone, `distinctUntilChanged`.
     * A stream is reference counted, so cancelling the collector drops the refcount to zero and the
     * next collector pays for a fresh TLS handshake (plus KuCoin's token round-trip) — which is why
     * every input that does *not* change this set must leave the collection running.
     */
    fun socketKeys(
        keys: Set<MarketKey>,
        conditions: LiveConditions,
        settings: LiveSettingsValues,
    ): Set<MarketKey> {
        val mode = mode(conditions) ?: return emptySet()
        return if (cadence(mode, settings).useSockets) keys else emptySet()
    }
}

/**
 * The REST tick that keeps [WidgetRefresh.LIVE] useful while the screen is off.
 *
 * Five minutes, so the first glance after an unlock shows a price at most that old while
 * `SCREEN_ON` is already reconnecting the sockets behind it.
 */
const val LIVE_SCREEN_OFF_TICK_MS: Long = 300_000L

/** A [TickerMode.TICK] older than this is refreshed immediately when the screen comes on. */
const val SCREEN_ON_TICK_MAX_AGE_MS: Long = 60_000L

/** Sparkline shape barely changes; rebuilding the bitmaps this often is plenty. */
const val SPARKLINE_REFRESH_MS: Long = 60_000L

/** Widget re-render cadence in [TickerMode.NEAR]. */
const val NEAR_WIDGET_REFRESH_MS: Long = 10_000L

/** REST round cadence in [TickerMode.NEAR] — the metered-link stand-in for a socket. */
const val NEAR_REST_ROUND_MS: Long = 15_000L
