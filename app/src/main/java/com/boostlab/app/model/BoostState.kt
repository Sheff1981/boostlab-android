package com.boostlab.app.model

data class BoostState(
    val selectedApp: BoostApp? = null,
    val isBoosting: Boolean = false,

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
