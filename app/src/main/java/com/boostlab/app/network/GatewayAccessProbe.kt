package com.boostlab.app.network

data class GatewayAccessProbeResult(
    val metrics: RouteMetrics,
    val transport: String,
)

object GatewayAccessProbePolicy {
    const val UDP = "UDP"
    const val HTTPS_FALLBACK = "HTTPS_FALLBACK"

    fun choose(
        udp: RouteMetrics,
        httpsFallback: RouteMetrics?,
    ): GatewayAccessProbeResult {
        if (udp.received > 0 && udp.medianRttMs != null) {
            return GatewayAccessProbeResult(
                metrics = udp,
                transport = UDP,
            )
        }

        if (
            httpsFallback != null &&
            httpsFallback.received > 0 &&
            httpsFallback.medianRttMs != null
        ) {
            return GatewayAccessProbeResult(
                metrics = httpsFallback,
                transport = HTTPS_FALLBACK,
            )
        }

        return GatewayAccessProbeResult(
            metrics = udp,
            transport = UDP,
        )
    }

    fun scorePenalty(transport: String): Double {
        return if (transport == HTTPS_FALLBACK) HTTPS_FALLBACK_SCORE_PENALTY else 0.0
    }

    private const val HTTPS_FALLBACK_SCORE_PENALTY = 20.0
}
