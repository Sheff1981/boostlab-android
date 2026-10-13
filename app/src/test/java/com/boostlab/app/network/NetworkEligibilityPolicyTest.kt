package com.boostlab.app.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkEligibilityPolicyTest {
    @Test
    fun wifiIsAllowedWhenInternetIsValidated() {
        assertTrue(
            NetworkEligibilityPolicy.evaluate(
                networkValidated = true,
                networkTransport = "Wi-Fi",
                vpnActive = false,
            ).allowed,
        )
    }

    @Test
    fun mobileIsAllowedWhenInternetIsValidated() {
        assertTrue(
            NetworkEligibilityPolicy.evaluate(
                networkValidated = true,
                networkTransport = "Mobile",
                vpnActive = false,
            ).allowed,
        )
    }

    @Test
    fun ethernetIsAllowedWhenInternetIsValidated() {
        assertTrue(
            NetworkEligibilityPolicy.evaluate(
                networkValidated = true,
                networkTransport = "Ethernet",
                vpnActive = false,
            ).allowed,
        )
    }

    @Test
    fun unvalidatedInternetIsRejected() {
        assertFalse(
            NetworkEligibilityPolicy.evaluate(
                networkValidated = false,
                networkTransport = "Wi-Fi",
                vpnActive = false,
            ).allowed,
        )
    }

    @Test
    fun anotherVpnIsRejectedOnAnyTransport() {
        assertFalse(
            NetworkEligibilityPolicy.evaluate(
                networkValidated = true,
                networkTransport = "Mobile",
                vpnActive = true,
            ).allowed,
        )
    }
}
