package com.boostlab.app.network

import org.junit.Assert.assertEquals
import org.junit.Test

class LanGatewayDiscoveryContractTest {
    @Test
    fun discoveryUsesSameProbeToken() {
        assertEquals("BOOSTLAB/PROBE/1", UdpRouteProbe.MAGIC)
    }

    @Test
    fun discoveryUsesGatewayProbePort() {
        assertEquals(51821, UdpRouteProbe.DEFAULT_PORT)
    }
}
