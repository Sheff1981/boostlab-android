package com.boostlab.app.tunnel

import org.junit.Assert.assertEquals
import org.junit.Test

class TunnelMtuPolicyTest {
    @Test
    fun usesMeasuredLinkMtuWithWireGuardHeadroom() {
        assertEquals(1420, TunnelMtuPolicy.choose(1500, "Wi-Fi"))
        assertEquals(1320, TunnelMtuPolicy.choose(1400, "Mobile"))
    }

    @Test
    fun clampsSmallMeasuredMtuSafely() {
        assertEquals(1280, TunnelMtuPolicy.choose(1320, "Mobile"))
    }

    @Test
    fun usesTransportFallbackWhenLinkMtuUnknown() {
        assertEquals(1420, TunnelMtuPolicy.choose(null, "Wi-Fi"))
        assertEquals(1420, TunnelMtuPolicy.choose(null, "Ethernet"))
        assertEquals(1380, TunnelMtuPolicy.choose(null, "Mobile"))
        assertEquals(1380, TunnelMtuPolicy.choose(null, null))
    }

    @Test
    fun ignoresUnreasonableReportedMtu() {
        assertEquals(1380, TunnelMtuPolicy.choose(999, "Mobile"))
        assertEquals(1420, TunnelMtuPolicy.choose(20_000, "Wi-Fi"))
    }
}
