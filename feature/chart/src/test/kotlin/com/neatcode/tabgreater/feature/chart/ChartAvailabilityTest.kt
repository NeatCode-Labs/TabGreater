package com.neatcode.tabgreater.feature.chart

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartAvailabilityTest {
    @Test
    fun `renderer gets one automatic recovery before manual retry is required`() {
        val policy = RendererRecoveryPolicy()

        assertEquals(RecoveryDecision.RETRY_AUTOMATICALLY, policy.onRendererFailure())
        assertEquals(RecoveryDecision.MANUAL_RETRY_REQUIRED, policy.onRendererFailure())

        policy.onManualRetry()
        assertEquals(RecoveryDecision.RETRY_AUTOMATICALLY, policy.onRendererFailure())
    }

    @Test
    fun `a healthy chart closes the renderer failure episode`() {
        val policy = RendererRecoveryPolicy()

        assertEquals(RecoveryDecision.RETRY_AUTOMATICALLY, policy.onRendererFailure())
        policy.onHealthyChart()

        assertEquals(RecoveryDecision.RETRY_AUTOMATICALLY, policy.onRendererFailure())
    }
}
