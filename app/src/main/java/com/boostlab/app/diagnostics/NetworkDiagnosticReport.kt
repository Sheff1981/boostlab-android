package com.boostlab.app.diagnostics

import com.boostlab.app.model.BoostState
import java.util.Locale

object NetworkDiagnosticReport {
    fun build(state: BoostState): String = buildString {
        appendLine("BOOSTLAB network diagnostic")
        appendLine("game=${state.selectedApp?.label ?: "-"}")
        appendLine("package=${state.selectedApp?.packageName ?: "-"}")
        appendLine("boost_active=${state.isBoosting}")
        appendLine("tunnel_connecting=${state.isTunnelConnecting}")
        appendLine("route_health=${state.routeHealth}")
        appendLine("route_recommendation=${state.routeRecommendation}")
        appendLine("probe_mode=${state.liveProbeMode}")
        appendLine("boost_mode=${state.boostMode}")
        appendLine("gateway_id=${state.selectedGatewayId ?: "-"}")
        appendLine("gateway_region=${state.selectedGatewayRegion ?: "-"}")
        appendLine("route_target=${state.routeTargetId ?: "-"}")
        appendLine("network_transport=${state.networkTransport ?: "-"}")
        appendLine("network_validated=${state.networkValidated ?: "-"}")
        appendLine("external_vpn=${state.externalVpnDetected}")
        appendLine("link_mtu=${state.networkMtu ?: "-"}")
        appendLine("wireguard_mtu=${state.tunnelMtu}")
        appendLine("power_save=${state.powerSaveMode ?: "-"}")
        appendLine("thermal_status=${state.thermalStatus ?: "-"}")
        appendLine("game_traffic_verified=${state.gameTrafficVerified}")
        appendLine("vpn_rx_bytes=${state.tunnelRxBytes}")
        appendLine("vpn_tx_bytes=${state.tunnelTxBytes}")
        appendLine("route_rtt_ms=${state.pingMs ?: "-"}")
        appendLine("route_p95_ms=${state.p95PingMs ?: "-"}")
        appendLine("route_jitter_ms=${state.jitterMs ?: "-"}")
        appendLine(
            "route_loss_pct=" +
                (state.packetLossPct?.let { String.format(Locale.US, "%.2f", it) } ?: "-"),
        )
        appendLine("direct_route_ms=${state.directPingMs ?: "-"}")
        appendLine("boost_route_ms=${state.boostedEstimatedPingMs ?: "-"}")
        appendLine("route_gain_ms=${state.routeGainMs ?: "-"}")
        appendLine("route_candidates=${state.routeCandidatesTested}")
        appendLine("route_probe_failures=${state.routeProbeFailures}")
        appendLine("directory_nodes=${state.gatewayDirectory.size}")
        appendLine("directory_error=${safe(state.gatewayDirectoryError)}")
        appendLine("probe_error=${safe(state.probeError)}")
        appendLine("peer_provision_error=${safe(state.peerProvisionError)}")
        appendLine("tunnel_error=${safe(state.tunnelError)}")
        appendLine("identity_error=${safe(state.identityError)}")
    }

    private fun safe(value: String?): String {
        return value
            ?.replace('\n', ' ')
            ?.replace('\r', ' ')
            ?.take(240)
            ?: "-"
    }
}
