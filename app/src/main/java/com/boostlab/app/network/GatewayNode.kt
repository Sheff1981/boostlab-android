package com.boostlab.app.network

data class GatewayNode(
    val id: String,
    val region: String,
    val countryCode: String? = null,
    val city: String? = null,
    val displayName: String? = null,
    val host: String,
    val udpPort: Int,
    val routeApiUrl: String? = null,
    val wireGuardPublicKey: String?,
    val wireGuardPort: Int?,
    val healthy: Boolean,
)

data class GatewayMeasurement(
    val node: GatewayNode,
    val metrics: RouteMetrics,
    val score: Double,
    val capacityPenalty: Double = 0.0,
)
