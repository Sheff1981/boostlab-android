package com.boostlab.app.diagnostics

import com.boostlab.app.model.BoostState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkDiagnosticReportTest {
    @Test
    fun reportContainsUsefulNetworkStateButNotEnrollmentSecret() {
        val state = BoostState(
            isBoosting = true,
            routeHealth = "GAME_ROUTE",
            routeRecommendation = "BOOST",
            selectedGatewayId = "fra-1",
            selectedGatewayRegion = "europe",
            routeTargetId = "game-eu",
            networkTransport = "Wi-Fi",
            networkValidated = true,
            networkMtu = 1500,
            tunnelMtu = 1420,
            pingMs = 42,
            p95PingMs = 51,
            jitterMs = 3,
            packetLossPct = 0.0,
            provisioningEnrollmentCode = "TOP-SECRET-ENROLLMENT",
            controlPlaneUrl = "https://private-control.example",
        )

        val report = NetworkDiagnosticReport.build(state)

        assertTrue(report.contains("route_health=GAME_ROUTE"))
        assertTrue(report.contains("gateway_id=fra-1"))
        assertTrue(report.contains("route_rtt_ms=42"))
        assertFalse(report.contains("TOP-SECRET-ENROLLMENT"))
        assertFalse(report.contains("private-control.example"))
        assertFalse(report.contains("enrollment_code"))
        assertFalse(report.contains("access_token"))
        assertFalse(report.contains("private_key"))
        assertFalse(report.contains("provisioning_ticket"))
    }
}
