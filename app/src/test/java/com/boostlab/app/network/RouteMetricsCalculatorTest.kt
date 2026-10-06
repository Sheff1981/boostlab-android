package com.boostlab.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteMetricsCalculatorTest {
    @Test
    fun calculatesMedianJitterAndLoss() {
        val metrics = RouteMetricsCalculator.calculate(
            listOf(20L, 22L, null, 24L, 30L),
        )

        assertEquals(23, metrics.medianRttMs)
        assertEquals(3, metrics.jitterMs)
        assertEquals(20.0, metrics.packetLossPct, 0.001)
        assertEquals(5, metrics.sent)
        assertEquals(4, metrics.received)
    }

    @Test
    fun allLostHasNoLatencyValues() {
        val metrics = RouteMetricsCalculator.calculate(
            listOf(null, null, null),
        )

        assertNull(metrics.medianRttMs)
        assertNull(metrics.jitterMs)
        assertEquals(100.0, metrics.packetLossPct, 0.001)
    }
}
