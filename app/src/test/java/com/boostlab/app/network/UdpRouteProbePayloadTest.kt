package com.boostlab.app.network

import org.junit.Assert.assertEquals
import org.junit.Test

class UdpRouteProbePayloadTest {
    @Test
    fun buildsCorrelatedPayload() {
        val probe = UdpRouteProbe()

        assertEquals(
            "BOOSTLAB/PROBE/2/0123456789abcdef/7",
            probe.probePayload("0123456789abcdef", 7),
        )
    }
}
