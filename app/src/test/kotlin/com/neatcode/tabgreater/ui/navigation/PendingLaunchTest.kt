package com.neatcode.tabgreater.ui.navigation

import android.content.Intent
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PendingLaunchTest {

    @Test
    fun `an offered intent waits until it is consumed`() {
        val launch = PendingLaunch()
        val tap = Intent()
        launch.offer(tap)
        assertSame(tap, launch.intent)
        launch.consume(tap)
        assertNull(launch.intent)
    }

    @Test
    fun `a tap that arrives while the shell is acting on the previous one survives`() {
        val launch = PendingLaunch()
        val first = Intent()
        val second = Intent()
        launch.offer(first)
        launch.offer(second) // the shell has not consumed `first` yet
        launch.consume(first) // the effect for `first` finishes
        assertSame(second, launch.intent)
    }

    @Test
    fun `consuming an intent that was never queued changes nothing`() {
        val launch = PendingLaunch()
        val queued = Intent()
        launch.offer(queued)
        launch.consume(Intent())
        assertSame(queued, launch.intent)
    }
}
