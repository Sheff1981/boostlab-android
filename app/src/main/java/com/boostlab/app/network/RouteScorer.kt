package com.boostlab.app.network

object RouteScorer {
    fun score(metrics: RouteMetrics): Double {
        val rtt = metrics.medianRttMs ?: return Double.POSITIVE_INFINITY
        val jitter = metrics.jitterMs ?: 50

        return rtt.toDouble() +
            (jitter.toDouble() * 2.0) +
            (metrics.packetLossPct * 12.0)
    }
}
