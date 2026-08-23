package com.neatcode.tabgreater.core.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Brings the live layer back after a reboot or an `adb install -r` / Play-style app update.
 *
 * `BOOT_COMPLETED` is one of the documented exemptions that allow starting a foreground service
 * from the background, and Android 15's boot-time FGS blacklist does not include `specialUse`
 *.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                Log.i(TAG, "restarting live layer after ${intent.action}")
                LiveTickerLauncher.ensureRunning(context)
            }
        }
    }

    private companion object {
        const val TAG = "LiveBoot"
    }
}

/**
 * Receives both alarms armed by [LiveAlarmScheduler].
 *
 * * [ACTION_HEARTBEAT] — the watchdog. Starting the service is a no-op when it is alive and a
 *   restart when an OEM killed it; a dispatched exact alarm is itself an FGS-from-background
 *   exemption, so the start is allowed.
 * * [ACTION_SLEEP_TICK] — the [TickerMode.SLEEP] tick. The receiver only forwards it: the work
 *   (one REST round + a widget render + re-arming) happens on the service's scope, where it can
 *   outlive the ~10 s receiver window.
 */
class TickAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_HEARTBEAT && action != ACTION_SLEEP_TICK) return
        Log.i(TAG, "alarm $action")
        try {
            ContextCompat.startForegroundService(context, LiveTickerService.intent(context, action))
        } catch (e: IllegalStateException) {
            Log.w(TAG, "foreground start not allowed for $action", e)
        }
    }

    private companion object {
        const val TAG = "LiveAlarmRx"
    }
}
