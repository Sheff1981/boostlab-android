package com.boostlab.app.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoGatewayEligibilityTest {
    private fun measurement(
        loss: Double,
        received: Int = 7,
        score: Double = 50.0,
    ) = GatewayMeasurement(
        node = GatewayNode(
            id = "node",
            region = "europe",
            host = "203.0.113.10",
            udpPort = 51821,
            wireGuardPublicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            wireGuardPort = 51820,
            healthy = true,
        ),
        metrics = RouteMetrics(
            medianRttMs = if (received > 0) 40 else null,
            jitterMs = if (received > 0) 5 else null,
            packetLossPct = loss,
            sent = 7,
            received = received,
            p95RttMs = if (received > 0) 55 else null,
        ),
        score = score,
    )

    @Test
    fun healthyGatewayIsEligible() {
        assertTrue(AutoGatewayEligibility.isEligible(measurement(loss = 0.0)))
    }

    @Test
    fun twoLostPacketsOutOfSevenAreTooLossyForAuto() {
        assertFalse(
            AutoGatewayEligibility.isEligible(
                measurement(loss = 28.6, received = 5),
            ),
        )
    }

    @Test
    fun unreachableGatewayIsRejected() {
        assertFalse(
            AutoGatewayEligibility.isEligible(
                measurement(loss = 100.0, received = 0),
            ),
        )
    }

    @Test
    fun nonFiniteScoreIsRejected() {
        assertFalse(
            AutoGatewayEligibility.isEligible(
                measurement(loss = 0.0, score = Double.POSITIVE_INFINITY),
            ),
        )
    }
}
