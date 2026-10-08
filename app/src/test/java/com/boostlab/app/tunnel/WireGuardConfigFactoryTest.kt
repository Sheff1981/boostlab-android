package com.boostlab.app.tunnel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun routesOnlySelectedApplication() {
        val text = WireGuardConfigFactory.render(
            TunnelProfile(
                privateKey = "private",
                serverPublicKey = "server",
                endpointHost = "203.0.113.10",
                endpointPort = 51820,
                addressCidr = "10.77.0.22/32",
                dnsServer = "1.1.1.1",
                selectedPackage = "com.example.game",
                mtu = 1380,
            ),
        )

        assertTrue(text.contains("IncludedApplications = com.example.game"))
        assertFalse(text.contains("ExcludedApplications"))
        assertEquals(1, "IncludedApplications =".toRegex().findAll(text).count())
        assertTrue(text.contains("AllowedIPs = 0.0.0.0/0"))
    }

    @Test
    fun formatsIpv6Endpoint() {
        assertEquals(
            "[2001:db8::10]:51820",
            WireGuardConfigFactory.formatEndpoint("2001:db8::10", 51820),
        )
    }
}
