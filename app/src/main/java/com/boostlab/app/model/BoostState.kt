package com.boostlab.app.model

data class BoostState(
    val selectedApp: BoostApp? = null,
    val isBoosting: Boolean = false,
    val gatewayHost: String = "",
    val gatewayPort: Int = 51821,
    val isProbing: Boolean = false,
    val probeError: String? = null,
    val serverLabel: String = "Сервер ещё не задан",
    val pingMs: Int? = null,
    val jitterMs: Int? = null,
    val packetLossPct: Double? = null,
)
