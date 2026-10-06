package com.boostlab.app.network

import org.junit.Assert.assertTrue
import org.junit.Test

class RouteScorerTest {
    @Test
    fun lowerLossCanBeatSlightlyLowerPing() {
        val stable = RouteMetrics(
            medianRttMs = 42,
            jitterMs = 3,
            packetLossPct = 0.0,
            sent = 8,
            received = 8,
        )
        val lossy = RouteMetrics(
            medianRttMs = 35,
            jitterMs = 4,
            packetLossPct = 5.0,
            sent = 8,
            received = 7,
        )

        assertTrue(RouteScorer.score(stable) < RouteScorer.score(lossy))
    }

    @Test
    fun unreachableRouteAlwaysLoses() {
        val unreachable = RouteMetrics(
            medianRttMs = null,
            jitterMs = null,
            packetLossPct = 100.0,
            sent = 8,
            received = 0,
        )

        assertTrue(RouteScorer.score(unreachable).isInfinite())
    }

    @Test
    fun tailLatencyPenalizesUnstableRoute() {
        val stable = RouteMetrics(
            medianRttMs = 40,
            jitterMs = 3,
            packetLossPct = 0.0,
            sent = 8,
            received = 8,
            p95RttMs = 46,
        )
        val spiky = RouteMetrics(
            medianRttMs = 40,
            jitterMs = 3,
            packetLossPct = 0.0,
            sent = 8,
            received = 8,
            p95RttMs = 95,
        )

        assertTrue(RouteScorer.score(stable) < RouteScorer.score(spiky))
    }
}
