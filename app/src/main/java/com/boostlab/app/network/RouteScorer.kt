package com.boostlab.app.network

object RouteScorer {
    fun score(metrics: RouteMetrics): Double {
        val rtt = metrics.medianRttMs ?: return Double.POSITIVE_INFINITY
        val jitter = metrics.jitterMs ?: 50
        val p95 = metrics.p95RttMs ?: rtt
        val tailPenalty = (p95 - rtt).coerceAtLeast(0)

        return rtt.toDouble() +
            (jitter.toDouble() * 1.8) +
            (tailPenalty.toDouble() * 0.8) +
            (metrics.packetLossPct * 18.0)
    }
}
