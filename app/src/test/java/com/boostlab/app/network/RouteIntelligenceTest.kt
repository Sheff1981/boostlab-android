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
    fun buildCandidateUsesDirectMetricsForSameTarget() {
        val targetA = GameRouteTarget("game-a", "a.example.com", 443)
        val targetB = GameRouteTarget("game-b", "b.example.com", 443)
        val directA = metrics(80)
        val directB = metrics(35)

        val candidate = RouteIntelligence.buildCandidate(
            node = node,
            target = targetA,
            directByTarget = mapOf(
                targetA.id to directA,
                targetB.id to directB,
            ),
            phoneToGatewayMetrics = metrics(20),
            gatewayToGameMetrics = metrics(40),
        )

        assertEquals(80, candidate?.directMetrics?.medianRttMs)
        assertEquals("game-a", candidate?.target?.id)
    }

    @Test
    fun buildCandidateRejectsMissingDirectTarget() {
        val targetA = GameRouteTarget("game-a", "a.example.com", 443)
        val candidate = RouteIntelligence.buildCandidate(
            node = node,
            target = targetA,
            directByTarget = mapOf("game-b" to metrics(35)),
            phoneToGatewayMetrics = metrics(20),
            gatewayToGameMetrics = metrics(40),
        )

        assertEquals(null, candidate)
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
    fun lowPingRejectsLossyRoute() {
        val direct = metrics(median = 80, p95 = 90, loss = 0.0)
        val boosted = metrics(median = 60, p95 = 80, loss = 5.0)
        val candidate = IntelligentRouteCandidate(
            node = node,
            target = target,
            directMetrics = direct,
            phoneToGatewayMetrics = metrics(20),
            gatewayToGameMetrics = metrics(40),
            boostedMetrics = boosted,
            directScore = RouteScorer.score(direct),
            boostedScore = RouteScorer.score(boosted),
        )

        assertFalse(RouteIntelligence.shouldUseBoost(candidate, "LOW_PING"))
    }

    @Test
    fun lowPingRejectsSevereP95Spikes() {
        val direct = metrics(median = 80, p95 = 90, loss = 0.0)
        val boosted = metrics(median = 60, p95 = 140, loss = 0.0)
        val candidate = IntelligentRouteCandidate(
            node = node,
            target = target,
            directMetrics = direct,
            phoneToGatewayMetrics = metrics(20),
            gatewayToGameMetrics = metrics(40),
            boostedMetrics = boosted,
            directScore = RouteScorer.score(direct),
            boostedScore = RouteScorer.score(boosted),
        )

        assertFalse(RouteIntelligence.shouldUseBoost(candidate, "LOW_PING"))
    }

    @Test
    fun combinedReliabilityMatchesCombinedLoss() {
        val combined = RouteIntelligence.combine(
            RouteMetrics(
                medianRttMs = 10,
                jitterMs = 1,
                packetLossPct = 14.285714,
                sent = 7,
                received = 6,
                p95RttMs = 12,
            ),
            RouteMetrics(
                medianRttMs = 20,
                jitterMs = 2,
                packetLossPct = 14.285714,
                sent = 7,
                received = 6,
                p95RttMs = 24,
            ),
        )

        assertEquals(7, combined.sent)
        assertEquals(5, combined.received)
        assertEquals(26.53, combined.packetLossPct, 0.05)
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
