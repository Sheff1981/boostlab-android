package com.boostlab.app.network

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteRacePolicyTest {
    private fun measurement(
        id: String,
        score: Double,
        median: Int,
        received: Int = 3,
    ): GatewayMeasurement {
        return GatewayMeasurement(
            node = GatewayNode(
                id = id,
                region = "europe",
                host = "203.0.113.10",
                udpPort = 51821,
                wireGuardPublicKey = "key-$id",
                wireGuardPort = 51820,
                healthy = true,
            ),
            metrics = RouteMetrics(
                medianRttMs = median,
                jitterMs = 2,
                packetLossPct = if (received == 3) 0.0 else 100.0,
                sent = 3,
                received = received,
                p95RttMs = median + 3,
            ),
            score = score,
        )
    }

    @Test
    fun keepsRememberedGatewayInFinalists() {
        val result = RouteRacePolicy.shortlist(
            quickMeasurements = listOf(
                measurement("a", 20.0, 20),
                measurement("b", 21.0, 21),
                measurement("c", 22.0, 22),
                measurement("remembered", 40.0, 40),
            ),
            rememberedGatewayId = "remembered",
            limit = 3,
        )

        assertEquals(listOf("remembered", "a", "b"), result.map { it.id })
    }

    @Test
    fun picksFastestWhenNothingRemembered() {
        val result = RouteRacePolicy.shortlist(
            quickMeasurements = listOf(
                measurement("c", 30.0, 30),
                measurement("a", 10.0, 10),
                measurement("b", 20.0, 20),
            ),
            rememberedGatewayId = null,
            limit = 2,
        )

        assertEquals(listOf("a", "b"), result.map { it.id })
    }

    @Test
    fun ignoresUnreachableRememberedGateway() {
        val result = RouteRacePolicy.shortlist(
            quickMeasurements = listOf(
                measurement("remembered", Double.POSITIVE_INFINITY, 999, received = 0),
                measurement("a", 10.0, 10),
                measurement("b", 20.0, 20),
            ),
            rememberedGatewayId = "remembered",
            limit = 2,
        )

        assertEquals(listOf("a", "b"), result.map { it.id })
    }
}
