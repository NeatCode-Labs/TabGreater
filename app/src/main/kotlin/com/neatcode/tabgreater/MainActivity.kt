package com.neatcode.tabgreater

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.neatcode.tabgreater.core.live.LiveTickerLauncher
import com.neatcode.tabgreater.core.model.TGColors
import com.neatcode.tabgreater.ui.TabGreaterRoot
import com.neatcode.tabgreater.ui.navigation.PendingLaunch
import com.neatcode.tabgreater.ui.theme.TabGreaterTheme

class MainActivity : ComponentActivity() {
    /** The intent the shell still has to act on; see [PendingLaunch] for why it is queued. */
    private val launch = PendingLaunch()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(TGColors.BACKGROUND.toInt()),
            navigationBarStyle = SystemBarStyle.dark(TGColors.NAV_SURFACE.toInt()),
        )
        // With saved state the activity is being recreated: the restored back stack says what is
        // showing, and its intent — the task's original one after process death, or the one
        // `setIntent` stored — was acted on already. Only a fresh activity acts on its intent.
        if (savedInstanceState == null) launch.offer(intent)
        setContent {
            TabGreaterTheme {
                TabGreaterRoot(launch)
            }
        }
    }

    /**
     * `singleTask`: a launch while the task exists lands here — including, when the process had
     * died under that task, before the first frame is drawn. Queued rather than handled, so the
     * shell acts on it once it composes.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launch.offer(intent)
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
