package com.neatcode.tabgreater.core.live

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.neatcode.tabgreater.core.data.repo.SparklineRepository
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.SparkPeriod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.android.ext.android.inject

/** Start (or re-evaluate) the service. */
internal const val ACTION_START = "com.neatcode.tabgreater.live.START"

/** Stop the service: the last widget was removed. */
internal const val ACTION_STOP = "com.neatcode.tabgreater.live.STOP"

/** Watchdog alarm: restarts the service if the OEM killed it, no-op when it is alive. */
internal const val ACTION_HEARTBEAT = "com.neatcode.tabgreater.live.HEARTBEAT"

/** [TickerMode.SLEEP] / [TickerMode.TICK] alarm: one REST round plus a re-render, then re-arm. */
internal const val ACTION_SLEEP_TICK = "com.neatcode.tabgreater.live.SLEEP_TICK"

/**
 * The resident `specialUse` foreground service that keeps the home-screen widgets live
 *.
 *
 * It is deliberately the *only* long-lived component: it guarantees a live process that can
 * receive `ACTION_SCREEN_ON`/`OFF` (context-registered broadcasts are not delivered to a dead
 * process), and it exempts the app from Doze's network suspension while it holds sockets.
 *
 * What it does at any moment is decided by [TickerModeCalculator] from four inputs — widgets,
 * settings, screen/keyguard, and network/power — and applied by [runSession]. Every input is a
 * flow, so a mode change is just the next emission and `collectLatest` tears the old mode down.
 *
 * Notifications: the channel is `IMPORTANCE_MIN` and `POST_NOTIFICATIONS` is never requested, so
 * the mandatory FGS notification never reaches the shade — this app posts none. The service still
 * runs; the only trace is the system's "active apps" entry.
 *
 * All network work runs on this service's coroutine scope — never through WorkManager, whose
 * runtime quotas now apply even to jobs running alongside a foreground service (Android 16).
 */
class LiveTickerService : Service() {

    private val settings: LiveSettings by inject()
    private val marketData: MarketDataRepository by inject()
    private val sparklines: SparklineRepository by inject()
    private val diagnostics: LiveDiagnostics by inject()

    /** `:widget` binds the real one; a build without widgets falls back to [NoWidgets]. */
    private val widgets: WidgetRefresher by lazy { resolveWidgetRefresher() }
    private val alarms: LiveAlarmScheduler by lazy { LiveAlarmScheduler(this) }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val environment = MutableStateFlow(Environment())

    /** Serialises widget renders so the price loop and a sleep tick never overlap. */
    private val refreshLock = Mutex()

    /** Written by the mode machine, read by the alarm-driven [sleepTick] on another coroutine. */
    @Volatile
    private var currentKeys: Set<MarketKey> = emptySet()

    @Volatile
    private var currentMode: TickerMode? = null

    /** Elapsed-realtime of the last REST top-up of the widgets' 24 h candle window; `0` = never. */
    @Volatile
    private var lastSparklineRestAt: Long = 0L

    /**
     * Elapsed-realtime of the last alarm-driven tick; `0` = never.
     *
     * Read by [runSession] so that turning the screen on in [TickerMode.TICK] shows a fresh price
     * instead of whatever the alarm last managed — without paying for a round on every unrelated
     * environment change (a session re-runs on charger, network and settings changes too).
     */
    @Volatile
    private var lastTickAt: Long = 0L

    /**
     * True while a [tick] is between its first request and its last render.
     *
     * [lastTickAt] is only stamped once a round has actually finished, so a round that is cancelled
     * half-way (the session it runs in is torn down by the next environment change) leaves no trace
     * and the next session runs the tick the user is waiting for. This flag is what still keeps two
     * rounds from overlapping while one is genuinely in flight.
     */
    @Volatile
    private var tickInFlight: Boolean = false

    private var receiversRegistered = false
    private var networkCallbackRegistered = false

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "system broadcast ${intent.action}")
            readEnvironment()
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            environment.update {
                it.copy(
                    transport = transportOf(networkCapabilities),
                    unmetered = unmeteredOf(networkCapabilities),
                )
            }
        }

        override fun onLost(network: Network) {
            environment.update { it.copy(transport = Transport.NONE, unmetered = false) }
        }
    }

    /**
     * The single source of every decision: widget keys × settings × environment. Built once so the
     * mode machine and the socket machine collect the same stream instead of two combines.
     */
    private val sessions: Flow<Session> by lazy {
        combine(
            widgets.observeWidgetKeys(),
            settings.values,
            environment,
        ) { keys, values, env -> Session(keys, values, env) }
            .distinctUntilChanged()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // First statement of the whole lifecycle: a startForegroundService() that is not answered
        // by startForeground() within 5 s kills the process, and on a cold start onCreate and
        // onStartCommand queue behind the app's own main-thread work (emulator: 33 s).
        promoteToForeground()
        registerSystemReceivers()
        registerNetworkCallback()
        readEnvironment()
        diagnostics.onServiceRunning(true)
        serviceScope.launch { runModeMachine() }
        serviceScope.launch { runSocketMachine() }
        serviceScope.launch { marketData.status.collect { diagnostics.onStreamStatus(it) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Repeated for every further startForegroundService(), including the START_STICKY restart
        // where `intent` is null; the call is idempotent once onCreate has promoted us.
        promoteToForeground()
        // Not unconditional: a tick alarm is its own watchdog, and a heartbeat armed here would sit
        // in front of it and spend the one while-idle dispatch Doze grants this uid (LiveAlarms.kt).
        if (rearmsHeartbeat(intent?.action, currentMode)) armHeartbeat(force = true)

        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "stop requested")
                stopNow()
                return START_NOT_STICKY
            }
            ACTION_SLEEP_TICK -> serviceScope.launch { sleepTick() }
        }
        // A heartbeat (or a plain start) may arrive after hours in Doze: re-read everything.
        readEnvironment()
        return START_STICKY
    }

    override fun onDestroy() {
        if (receiversRegistered) {
            unregisterReceiver(systemReceiver)
            receiversRegistered = false
        }
        if (networkCallbackRegistered) {
            getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(networkCallback)
            networkCallbackRegistered = false
        }
        serviceScope.cancel()
        diagnostics.onServiceRunning(false)
        super.onDestroy()
    }

    // ---------------------------------------------------------------- mode machine

    private suspend fun runModeMachine() {
        sessions.collectLatest { session -> runSession(session) }
    }

    /**
     * Holds the exchange sockets — and the widgets' 24 h kline subscriptions — across mode changes.
     *
     * Deliberately *not* part of [runSession]: `collectLatest` there cancels on every environment
     * change, and cancelling the ticker collection drops the reference count to zero, so plugging
     * in a charger or turning the screen off used to close and re-handshake every socket even
     * though LIVE and IDLE both want exactly the same ones. [TickerModeCalculator.socketKeys]
     * collapses all of that to the only thing the sockets care about.
     */
    private suspend fun runSocketMachine() {
        sessions
            .map { TickerModeCalculator.socketKeys(it.keys, it.conditions(), it.settings) }
            .distinctUntilChanged()
            .collectLatest { keys ->
                if (keys.isEmpty()) return@collectLatest
                coroutineScope {
                    // Collecting observeTickers is what holds the WebSockets open (ref-counted in
                    // LiveMarketDataRepository); the values themselves are read from `latest` by
                    // the widget refresher, so this collector only has to stay subscribed.
                    launch { marketData.observeTickers(keys).collect { } }
                    // Same trick for the candles: collecting a sparkline extends its cached 24 h
                    // window from the exchange kline stream, which is the only thing that keeps a
                    // widget-only pair from freezing at the price it had when it was configured.
                    for (key in keys) {
                        launch { sparklines.observeSparkline(key, SparkPeriod.HOURS_24).collect { } }
                    }
                }
            }
    }

    /**
     * Runs one mode until the inputs change (`collectLatest` cancels this call, which cancels every
     * loop below it). The sockets are not part of it — see [runSocketMachine].
     */
    private suspend fun runSession(session: Session) {
        val mode = TickerModeCalculator.mode(session.conditions())
        currentKeys = session.keys
        currentMode = mode
        publishDiagnostics(mode, session)

        if (mode == null) {
            Log.i(TAG, "no widgets — stopping")
            stopNow()
            return
        }

        val cadence = TickerModeCalculator.cadence(mode, session.settings)
        Log.i(
            TAG,
            "mode=$mode refresh=${session.settings.widgetRefresh.id} keys=${session.keys.size} " +
                "transport=${session.env.transport} metered=${!session.env.unmetered} " +
                "wifiOnly=${session.settings.wifiOnly} screen=${session.env.screenInteractive} " +
                "charging=${session.env.charging} sockets=${cadence.useSockets} " +
                "render=${cadence.widgetRefreshMs}ms spark=${cadence.sparklineRefreshMs}ms " +
                "rest=${cadence.restRoundMs}ms tick=${cadence.tickIntervalMs}ms",
        )

        if (cadence.sleepTick) {
            // Not forced: any environment change re-runs this session, and forcing here would
            // push the pending tick out by a full interval every time (observed on the emulator).
            alarms.armSleepTick(cadence.tickIntervalMs)
            // Doze grants this uid roughly one while-idle dispatch per 9 minutes. The tick proves
            // the service is alive all by itself, so a second pending alarm in front of it would
            // only spend that dispatch on a no-op environment read.
            cancelHeartbeat()
        } else {
            alarms.cancelSleepTick()
            armHeartbeat(force = false)
        }

        coroutineScope {
            if (needsScreenOnTick(mode, session)) {
                launch { tick() }
            }
            if (cadence.restRoundMs > 0L && session.keys.isNotEmpty()) {
                launch { restLoop(session.keys, cadence.restRoundMs) }
            }
            if (cadence.widgetRefreshMs > 0L) {
                launch { widgetLoop(cadence) }
            }
        }
    }

    /** This session's answer to the top-level [needsScreenOnTick]. */
    private fun needsScreenOnTick(mode: TickerMode, session: Session): Boolean = needsScreenOnTick(
        mode = mode,
        screenInteractive = session.env.screenInteractive,
        hasKeys = session.keys.isNotEmpty(),
        tickInFlight = tickInFlight,
        lastTickAtElapsed = lastTickAt,
        nowElapsed = SystemClock.elapsedRealtime(),
    )

    private suspend fun widgetLoop(cadence: Cadence) {
        var lastSparklineAt = 0L
        while (currentCoroutineContext().isActive) {
            val now = SystemClock.elapsedRealtime()
            val withSparklines = cadence.sparklineRefreshMs > 0L &&
                (lastSparklineAt == 0L || now - lastSparklineAt >= cadence.sparklineRefreshMs)
            if (withSparklines) lastSparklineAt = now
            refreshWidgets(withSparklines)
            delay(cadence.widgetRefreshMs)
        }
    }

    private suspend fun restLoop(keys: Set<MarketKey>, intervalMs: Long) {
        while (currentCoroutineContext().isActive) {
            restRound(keys)
            // New candles only land on the rounds where the top-up actually ran; repainting the
            // sparkline band on the other rounds would re-read the cache for nothing.
            val newCandles = refreshSparklinesIfDue(keys)
            refreshWidgets(newCandles)
            delay(intervalMs)
        }
    }

    /** One alarm-driven tick while the sockets are closed ([TickerMode.SLEEP], [TickerMode.TICK]). */
    private suspend fun sleepTick() {
        tick()
        val mode = currentMode
        val values = runCatchingSuspend("settings") { settings.values.first() }
        val interval = if (mode != null && values != null) {
            TickerModeCalculator.tickIntervalMs(mode, values)
        } else {
            0L
        }
        if (interval > 0L) {
            // In a tick mode this alarm is also the watchdog, so no heartbeat is armed alongside
            // it — and any heartbeat left over from an earlier mode goes now, exactly as runSession
            // does when it enters a tick mode. Both alarms share one Doze quota (LiveAlarms.kt).
            alarms.armSleepTick(interval, force = true)
            cancelHeartbeat()
        } else {
            armHeartbeat(force = true)
        }
    }

    /** One REST round plus a widget render for the markets the widgets show. */
    private suspend fun tick() {
        // The mode machine fills `currentKeys` only after two DataStore reads, and onStartCommand
        // launches this immediately: on a service the alarm just restarted the cache is still
        // empty, and the dispatch Doze paid for would otherwise be thrown away.
        val keys = runCatchingSuspend("widget keys") { sleepTickKeys(currentKeys, widgets) }.orEmpty()
        if (keys.isEmpty()) return
        tickInFlight = true
        try {
            restRound(keys)
            // In TICK mode this is the only thing that ever moves the widgets' sparkline band:
            // `widgetLoop` — the sole caller that passes `true` — does not run in a tick mode.
            val newCandles = refreshSparklinesIfDue(keys)
            refreshWidgets(newCandles)
            // Stamped last: a round cancelled half-way must not book the 60 s suppression window,
            // or the session that cancelled it would decide it has nothing left to do.
            lastTickAt = SystemClock.elapsedRealtime()
        } finally {
            tickInFlight = false
        }
    }

    private suspend fun restRound(keys: Set<MarketKey>) {
        runCatchingSuspend(WHAT_REST_ROUND) { marketData.refresh(keys) }
            ?: return
        diagnostics.onRestRound(System.currentTimeMillis())
    }

    /**
     * REST top-up of the widgets' 24 h candle window while no kline socket is held (NEAR, SLEEP,
     * TICK). Rate limited to the repository's own refresh interval so a 15 s NEAR round stays cheap.
     *
     * @return `true` when fresh candles reached the cache, i.e. when the next widget render has a
     *   reason to rebuild the sparkline band.
     */
    private suspend fun refreshSparklinesIfDue(keys: Set<MarketKey>): Boolean {
        if (keys.isEmpty()) return false
        val now = SystemClock.elapsedRealtime()
        if (lastSparklineRestAt != 0L && now - lastSparklineRestAt < SparklineRepository.REFRESH_INTERVAL_MS) {
            return false
        }
        lastSparklineRestAt = now
        return runCatchingSuspend("sparkline refresh") {
            sparklines.refresh(keys, SparkPeriod.HOURS_24)
        } != null
    }

    private suspend fun refreshWidgets(includeSparklines: Boolean) {
        refreshLock.withLock {
            val painted = runCatchingSuspend(WHAT_WIDGET_REFRESH) { widgets.refreshAll(includeSparklines) }
                ?: return
            diagnostics.onWidgetRefresh(System.currentTimeMillis(), painted)
        }
    }

    /** Logs and records a failure instead of taking the whole service down; `null` means "failed". */
    private suspend fun <T> runCatchingSuspend(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$what failed", e)
        diagnostics.onError(what, e)
        null
    }

    // ---------------------------------------------------------------- environment

    private fun registerSystemReceivers() {
        if (receiversRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED)
        }
        // SCREEN_ON/OFF are only delivered to context-registered receivers — that is the whole
        // reason this service is resident.
        ContextCompat.registerReceiver(this, systemReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiversRegistered = true
    }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return
        try {
            connectivity.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        } catch (e: SecurityException) {
            Log.w(TAG, "network callback refused", e)
        }
    }

    private fun readEnvironment() {
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        val battery = getSystemService(BatteryManager::class.java)
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity?.let { it.getNetworkCapabilities(it.activeNetwork) }
        environment.update {
            it.copy(
                // A locked screen counts as "off": nobody can see the widget behind the keyguard.
                screenInteractive = power?.isInteractive == true && keyguard?.isKeyguardLocked != true,
                transport = transportOf(capabilities),
                // Both sources agree by construction; requiring both keeps a null capabilities
                // read (no active network yet) from being mistaken for a free link.
                unmetered = unmeteredOf(capabilities) && connectivity?.isActiveNetworkMetered != true,
                charging = battery?.isCharging == true,
                dataSaver = connectivity?.restrictBackgroundStatus ==
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED,
                powerSave = power?.isPowerSaveMode == true,
            )
        }
    }

    private fun publishDiagnostics(mode: TickerMode?, session: Session) {
        diagnostics.onEnvironment(
            mode = mode,
            transport = session.env.transport,
            screenInteractive = session.env.screenInteractive,
            charging = session.env.charging,
            dataSaver = session.env.dataSaver,
            powerSave = session.env.powerSave,
            widgetCount = session.keys.size,
            canScheduleExactAlarms = alarms.canScheduleExact,
        )
    }

    // ---------------------------------------------------------------- foreground plumbing

    private fun promoteToForeground() {
        ensureChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            // `specialUse` does not exist before Android 14; an untyped FGS is the equivalent there.
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        } catch (e: Exception) {
            Log.w(TAG, "startForeground refused", e)
            diagnostics.onError("startForeground", e)
            stopSelf()
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_MIN)
            .setName(getString(R.string.live_service_channel_name))
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_live_ticker)
        .setContentTitle(getString(R.string.live_service_title))
        .setContentText(getString(R.string.live_service_text))
        .setOngoing(true)
        .setSilent(true)
        .setShowWhen(false)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
        .build()

    private fun armHeartbeat(force: Boolean) {
        alarms.armHeartbeat(force)
        val next = alarms.nextHeartbeatAtElapsed
        diagnostics.onHeartbeatArmed(next?.let { it - SystemClock.elapsedRealtime() })
    }

    private fun cancelHeartbeat() {
        alarms.cancelHeartbeat()
        diagnostics.onHeartbeatArmed(null)
    }

    private fun stopNow() {
        alarms.cancelAll()
        diagnostics.onHeartbeatArmed(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------------------------------------------------------------- value types

    private data class Environment(
        val screenInteractive: Boolean = true,
        val transport: Transport = Transport.NONE,
        /** `NET_CAPABILITY_NOT_METERED`; `false` until the first capabilities read lands. */
        val unmetered: Boolean = false,
        val charging: Boolean = false,
        val dataSaver: Boolean = false,
        val powerSave: Boolean = false,
    )

    private data class Session(
        val keys: Set<MarketKey>,
        val settings: LiveSettingsValues,
        val env: Environment,
    ) {
        fun conditions() = LiveConditions(
            hasWidgets = keys.isNotEmpty(),
            screenInteractive = env.screenInteractive,
            transport = env.transport,
            charging = env.charging,
            dataSaver = env.dataSaver,
            powerSave = env.powerSave,
            unmetered = env.unmetered,
            widgetRefresh = settings.widgetRefresh,
            wifiOnly = settings.wifiOnly,
        )
    }

    internal companion object {
        const val TAG = "LiveTicker"
        private const val CHANNEL_ID = "live_ticker"
        private const val NOTIFICATION_ID = 4711

        /** Intent that starts or re-evaluates the service. */
        fun intent(context: Context, action: String): Intent =
            Intent(context, LiveTickerService::class.java).setAction(action)
    }
}

/**
 * Maps the default network's capabilities onto [Transport], which is only used for the label the
 * Settings screen shows. Whether the link is free is [unmeteredOf]'s answer, not this one.
 */
internal fun transportOf(capabilities: NetworkCapabilities?): Transport {
    val caps = capabilities ?: return Transport.NONE
    if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return Transport.NONE
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Transport.ETHERNET
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.CELLULAR
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> Transport.WIFI
        else -> Transport.CELLULAR
    }
}

/**
 * Whether the link is free, straight from `NET_CAPABILITY_NOT_METERED`.
 *
 * A tethered hotspot and a Wi-Fi the user marked "Metered" both report `TRANSPORT_WIFI` without
 * this capability, so judging metering by transport silently disabled both Data Saver and the
 * "Only on Wi-Fi" gate on exactly the links the design calls expensive.
 */
internal fun unmeteredOf(capabilities: NetworkCapabilities?): Boolean =
    capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true

/**
 * Whether a `startService` carrying [action] should (re-)arm the watchdog heartbeat, given the mode
 * the service is running in ([mode] `null` = the mode machine has not decided yet).
 *
 * Doze grants this uid roughly one while-idle dispatch per nine minutes, and the heartbeat and the
 * tick draw on that same budget (see [MIN_SLEEP_TICK_MS]). Nothing may therefore arm a heartbeat
 * while the tick alarm carries the mode: the tick proves the service is alive by itself and re-arms
 * itself in [LiveTickerService]'s `sleepTick`, whereas a heartbeat pending in front of it spends the
 * granted dispatch on a no-op and pushes the cadence the user chose out by a full cycle.
 *
 * That covers two ways in: the tick dispatch itself, and the `ACTION_START` the widget layer sends
 * whenever a widget is added or refreshed — those arrive long after the session that would have
 * cancelled the heartbeat, so nothing else would take it back down. A heartbeat dispatch in a
 * socket mode does re-arm; there it is the only thing keeping the watchdog chain going.
 */
internal fun rearmsHeartbeat(action: String?, mode: TickerMode?): Boolean {
    if (action == ACTION_SLEEP_TICK || action == ACTION_STOP) return false
    return mode == null || !TickerModeCalculator.usesTickAlarm(mode)
}

/**
 * Whether the screen just came on in a timed mode with nothing fresh to show.
 *
 * In [TickerMode.TICK] the alarm may be up to fifteen minutes away, and the one moment the price
 * actually matters is the moment the user looks at the home screen. A tick younger than
 * [SCREEN_ON_TICK_MAX_AGE_MS] is fresh enough, which is what keeps this from firing again on the
 * charger/network/settings re-runs that follow an unlock — but only a *completed* tick counts, so
 * a round the next session cancelled does not talk that session out of running one.
 *
 * @param tickInFlight a round is running right now; the session that started it is still alive.
 * @param lastTickAtElapsed elapsed-realtime of the last completed round, `0` when there is none.
 */
internal fun needsScreenOnTick(
    mode: TickerMode,
    screenInteractive: Boolean,
    hasKeys: Boolean,
    tickInFlight: Boolean,
    lastTickAtElapsed: Long,
    nowElapsed: Long,
): Boolean {
    if (mode != TickerMode.TICK || !screenInteractive || !hasKeys) return false
    if (tickInFlight) return false
    return lastTickAtElapsed == 0L || nowElapsed - lastTickAtElapsed >= SCREEN_ON_TICK_MAX_AGE_MS
}

/**
 * Keys for one alarm tick: the mode machine's cache when it is warm, otherwise a single read of
 * the widget store. [LiveTickerService.sleepTick] can run before the mode machine's combined flow
 * has emitted (the alarm restarts a dead process), and an empty set there wastes the dispatch.
 */
internal suspend fun sleepTickKeys(cached: Set<MarketKey>, widgets: WidgetRefresher): Set<MarketKey> =
    cached.ifEmpty { widgets.observeWidgetKeys().first() }
