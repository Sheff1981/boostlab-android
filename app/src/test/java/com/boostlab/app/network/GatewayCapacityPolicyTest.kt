package com.boostlab.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayCapacityPolicyTest {
    @Test
    fun noMetricsMeansNoPenalty() {
        assertEquals(0.0, GatewayCapacityPolicy.penalty(null), 0.001)
    }

    @Test
    fun lightLoadHasNoPenalty() {
        val status = GatewayRuntimeStatus(
            cpuCount = 2,
            goroutines = 20,
            heapAllocMb = 32.0,
            load1 = 1.0,
        )

        assertEquals(0.0, GatewayCapacityPolicy.penalty(status), 0.001)
        assertFalse(GatewayCapacityPolicy.overloaded(status))
    }

    @Test
    fun saturatedGatewayGetsPenalty() {
        val status = GatewayRuntimeStatus(
            cpuCount = 1,
            goroutines = 100,
            heapAllocMb = 64.0,
            load1 = 1.8,
        )

        assertTrue(GatewayCapacityPolicy.penalty(status) >= 30.0)
        assertTrue(GatewayCapacityPolicy.overloaded(status))
    }
}
