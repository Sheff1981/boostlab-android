package com.boostlab.app.network

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteDecisionPolicyTest {
    private fun measurement(id: String, score: Double): GatewayMeasurement {
        return GatewayMeasurement(
            node = GatewayNode(
                id = id,
                region = "test",
                host = "127.0.0.1",
                udpPort = 51821,
                wireGuardPublicKey = null,
                wireGuardPort = null,
                healthy = true,
            ),
            metrics = RouteMetrics(
                medianRttMs = 40,
                jitterMs = 2,
                packetLossPct = 0.0,
                sent = 6,
                received = 6,
            ),
            score = score,
        )
    }

    @Test
    fun doesNotFlapForSmallImprovement() {
        val current = measurement("a", 100.0)
        val candidate = measurement("b", 90.0)

        assertEquals("a", RouteDecisionPolicy.choose(current, candidate).node.id)
    }

    @Test
    fun switchesForMaterialImprovement() {
        val current = measurement("a", 100.0)
        val candidate = measurement("b", 80.0)

        assertEquals("b", RouteDecisionPolicy.choose(current, candidate).node.id)
    }

    @Test
    fun switchesWhenCurrentIsMissing() {
        val candidate = measurement("b", 90.0)

        assertEquals("b", RouteDecisionPolicy.choose(null, candidate).node.id)
    }
}
