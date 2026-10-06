package com.boostlab.app.tunnel

import org.junit.Assert.assertEquals
import org.junit.Test

class WireGuardConfigFactoryTest {
    @Test
    fun formatsIpv4Endpoint() {
        assertEquals(
            "203.0.113.10:51820",
            WireGuardConfigFactory.formatEndpoint("203.0.113.10", 51820),
        )
    }

    @Test
    fun formatsIpv6Endpoint() {
        assertEquals(
            "[2001:db8::10]:51820",
            WireGuardConfigFactory.formatEndpoint("2001:db8::10", 51820),
        )
    }
}
