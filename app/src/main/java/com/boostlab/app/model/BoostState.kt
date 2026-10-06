package com.boostlab.app.model

data class BoostState(
    val selectedApp: BoostApp? = null,
    val isBoosting: Boolean = false,
    val isTunnelConnecting: Boolean = false,
    val tunnelError: String? = null,

    val clientPublicKey: String? = null,
    val identityError: String? = null,

    val wireGuardServerPublicKey: String = "",
    val wireGuardPort: Int = 51820,
    val tunnelAddress: String = "10.77.0.2/32",
    val dnsServer: String = "1.1.1.1",

    val controlPlaneUrl: String = "",
    val isAutoSelecting: Boolean = false,
    val discoveredNodes: Int = 0,
    val selectedGatewayId: String? = null,
    val selectedGatewayRegion: String? = null,

    val gatewayHost: String = "",
    val gatewayPort: Int = 51821,
    val isProbing: Boolean = false,
    val probeError: String? = null,
    val serverLabel: String = "Сервер ещё не задан",

    val pingMs: Int? = null,
    val jitterMs: Int? = null,
    val packetLossPct: Double? = null,
)
