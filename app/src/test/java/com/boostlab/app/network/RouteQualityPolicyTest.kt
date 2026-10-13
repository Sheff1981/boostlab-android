package com.boostlab.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteQualityPolicyTest {
    private fun metrics(
        median: Int = 50,
        p95: Int = 60,
        jitter: Int = 5,
        loss: Double = 0.0,
        received: Int = 5,
    ) = RouteMetrics(
        medianRttMs = if (received > 0) median else null,
        jitterMs = if (received > 0) jitter else null,
        packetLossPct = loss,
        sent = 5,
        received = received,
        p95RttMs = if (received > 0) p95 else null,
    )

    @Test
    fun goodRouteStaysGood() {
        val result = RouteQualityPolicy.assess(metrics())
        assertEquals("GOOD", result.level)
        assertFalse(RouteQualityPolicy.isBad(result.level))
    }

    @Test
    fun packetLossMarksRouteDegraded() {
        val result = RouteQualityPolicy.assess(metrics(loss = 2.5))
        assertEquals("DEGRADED", result.level)
        assertTrue(result.reason!!.contains("Потери"))
    }

    @Test
    fun highJitterMarksRouteSevere() {
        val result = RouteQualityPolicy.assess(metrics(jitter = 45))
        assertEquals("SEVERE", result.level)
        assertTrue(RouteQualityPolicy.isBad(result.level))
    }

    @Test
    fun p95TailDetectsSpikesEvenWhenMedianLooksFine() {
        val result = RouteQualityPolicy.assess(
            metrics(median = 40, p95 = 110, jitter = 8),
        )
        assertEquals("DEGRADED", result.level)
        assertTrue(result.reason!!.contains("Пики"))
    }

    @Test
    fun unreachableRouteIsBad() {
        val result = RouteQualityPolicy.assess(metrics(received = 0, loss = 100.0))
        assertEquals("UNREACHABLE", result.level)
        assertTrue(RouteQualityPolicy.isBad(result.level))
    }
}
