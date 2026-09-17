package com.neatcode.tabgreater.ui.navigation

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The launch the shell still has to act on, or `null` when there is none.
 *
 * A `singleTask` activity receives an intent two ways: the one `onCreate` runs with, and
 * `onNewIntent` whenever the task already exists. The second can arrive before the first frame —
 * when the process died while the task stayed in Recents, the system recreates the activity with the
 * intent the task was *started* with and delivers the new one during resume, before any composition
 * exists to listen for it. A listener registered from the composition therefore misses exactly the
 * widget tap that follows a long lock-screen sleep, and the user lands on the grid. Queued in Compose
 * state instead, the intent waits until the shell composes and consumes it from a `LaunchedEffect`,
 * so the order of delivery and composition no longer matters.
 *
 * Lives and dies with its activity instance. That is half of what keeps an activity recreated from
 * Recents on its restored back stack; the other half is `MainActivity.onCreate`, which offers the
 * activity's own intent only when there is no saved state, because with saved state that intent was
 * acted on long ago. An intent still unconsumed when a configuration change recreates the activity
 * is lost with the instance — a window of one frame.
 */
class PendingLaunch {
    var intent: Intent? by mutableStateOf(null)
        private set

    /** Queues [intent]; a later one replaces an earlier one the shell has not consumed yet. */
    fun offer(intent: Intent) {
        this.intent = intent
    }

    /** Clears [intent] once acted on — only that one, so an intent offered meanwhile survives. */
    fun consume(intent: Intent) {
        if (this.intent === intent) this.intent = null
    }
}
