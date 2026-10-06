package com.boostlab.app.model

data class BoostState(
    val selectedApp: BoostApp? = null,
    val isBoosting: Boolean = false,
    val serverLabel: String = "Server: not configured",
    val pingMs: Int? = null,
    val jitterMs: Int? = null,
    val packetLossPct: Double? = null,
)
