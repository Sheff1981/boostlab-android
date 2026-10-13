package com.boostlab.app.network

import org.junit.Assert.assertEquals
import org.junit.Test

class GatewayAccessProbePolicyTest {
    private fun metrics(
        median: Int?,
        received: Int,
        sent: Int = 3,
    ) = RouteMetrics(
        medianRttMs = median,
        jitterMs = if (received > 1) 2 else null,
        packetLossPct = if (sent == 0) 100.0 else ((sent - received).toDouble() / sent) * 100.0,
        sent = sent,
        received = received,
        p95RttMs = median,
    )

    @Test
    fun udpAlwaysWinsWhenItResponds() {
        val result = GatewayAccessProbePolicy.choose(
            udp = metrics(30, 3),
            httpsFallback = metrics(70, 3),
        )

        assertEquals(GatewayAccessProbePolicy.UDP, result.transport)
        assertEquals(30, result.metrics.medianRttMs)
    }

    @Test
    fun httpsFallbackIsUsedOnlyWhenUdpIsUnreachable() {
        val result = GatewayAccessProbePolicy.choose(
            udp = metrics(null, 0),
            httpsFallback = metrics(75, 3),
        )

        assertEquals(GatewayAccessProbePolicy.HTTPS_FALLBACK, result.transport)
        assertEquals(75, result.metrics.medianRttMs)
        assertEquals(20.0, GatewayAccessProbePolicy.scorePenalty(result.transport), 0.001)
    }

    @Test
    fun failedUdpIsPreservedWhenBothTransportsFail() {
        val result = GatewayAccessProbePolicy.choose(
            udp = metrics(null, 0),
            httpsFallback = metrics(null, 0),
        )

        assertEquals(GatewayAccessProbePolicy.UDP, result.transport)
        assertEquals(0, result.metrics.received)
    }
}
