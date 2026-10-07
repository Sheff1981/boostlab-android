package com.boostlab.app.network

data class GameRouteTarget(
    val id: String,
    val host: String,
    val tcpPort: Int,
)

data class GatewayRouteMetrics(
    val targetId: String,
    val targetHost: String,
    val tcpPort: Int,
    val metrics: RouteMetrics,
)
