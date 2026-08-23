package com.neatcode.tabgreater

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.neatcode.tabgreater.core.live.LiveTickerLauncher
import com.neatcode.tabgreater.core.model.TGColors
import com.neatcode.tabgreater.ui.TabGreaterRoot
import com.neatcode.tabgreater.ui.theme.TabGreaterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(TGColors.BACKGROUND.toInt()),
            navigationBarStyle = SystemBarStyle.dark(TGColors.NAV_SURFACE.toInt()),
        )
        setContent {
            TabGreaterTheme {
                TabGreaterRoot()
            }
        }
    }

    /**
     * Brings the resident live service back whenever the app becomes visible.
     *
     * This is the only place that starts it: a visible activity is the exemption Android 12+
     * requires for `startForegroundService()`, whereas `Application.onCreate` runs while the
     * process is still background — the start is refused there and the widget stays frozen until
     * something else happens to touch it. It also covers the widget tap, whose chart deep link
     * lands in this activity. Idempotent: starting a running service only re-evaluates its mode.
     */
    override fun onStart() {
        super.onStart()
        LiveTickerLauncher.ensureRunning(this)
    }
}
