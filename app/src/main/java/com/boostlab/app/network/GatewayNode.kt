package com.boostlab.app.network

data class GatewayNode(
    val id: String,
    val region: String,
    val host: String,
    val udpPort: Int,
    val wireGuardPublicKey: String?,
    val wireGuardPort: Int?,
    val healthy: Boolean,
)

data class GatewayMeasurement(
    val node: GatewayNode,
    val metrics: RouteMetrics,
    val score: Double,
)
