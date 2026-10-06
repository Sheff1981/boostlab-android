package com.boostlab.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteIntelligenceTest {
    private fun metrics(
        median: Int,
        p95: Int = median,
        jitter: Int = 2,
        loss: Double = 0.0,
    ) = RouteMetrics(
        medianRttMs = median,
        jitterMs = jitter,
        packetLossPct = loss,
        sent = 7,
        received = 7,
        p95RttMs = p95,
    )

    private val node = GatewayNode(
        id = "eu-1",
        region = "eu",
        host = "203.0.113.10",
        udpPort = 51821,
        routeApiUrl = "https://eu-1.example.com",
        wireGuardPublicKey = null,
        wireGuardPort = null,
        healthy = true,
    )

    private val target = GameRouteTarget(
        id = "game-eu",
        host = "game.example.com",
        tcpPort = 443,
    )

    @Test
    fun combinesBothRouteLegs() {
        val combined = RouteIntelligence.combine(
            metrics(median = 12, p95 = 18, jitter = 2, loss = 1.0),
            metrics(median = 30, p95 = 36, jitter = 3, loss = 2.0),
        )

        assertEquals(42, combined.medianRttMs)
        assertEquals(54, combined.p95RttMs)
        assertEquals(5, combined.jitterMs)
        assertEquals(2.98, combined.packetLossPct, 0.01)
    }

    @Test
    fun lowPingRequiresMeaningfulGain() {
        val direct = metrics(80)
        val boosted = metrics(68)
        val candidate = IntelligentRouteCandidate(
            node = node,
            target = target,
            directMetrics = direct,
            phoneToGatewayMetrics = metrics(20),
            gatewayToGameMetrics = metrics(48),
            boostedMetrics = boosted,
            directScore = RouteScorer.score(direct),
            boostedScore = RouteScorer.score(boosted),
        )

        assertTrue(RouteIntelligence.shouldUseBoost(candidate, "LOW_PING"))
    }

    @Test
    fun lowPingKeepsDirectForTinyGain() {
        val direct = metrics(50)
        val boosted = metrics(47)
        val candidate = IntelligentRouteCandidate(
            node = node,
            target = target,
            directMetrics = direct,
            phoneToGatewayMetrics = metrics(15),
            gatewayToGameMetrics = metrics(32),
            boostedMetrics = boosted,
            directScore = RouteScorer.score(direct),
            boostedScore = RouteScorer.score(boosted),
        )

        assertFalse(RouteIntelligence.shouldUseBoost(candidate, "LOW_PING"))
    }
}
