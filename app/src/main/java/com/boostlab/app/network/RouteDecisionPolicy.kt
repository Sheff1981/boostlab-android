package com.boostlab.app.network

object RouteDecisionPolicy {
    private const val SWITCH_IMPROVEMENT_RATIO = 0.15

    fun choose(
        current: GatewayMeasurement?,
        bestCandidate: GatewayMeasurement,
    ): GatewayMeasurement {
        if (current == null) return bestCandidate
        if (!current.score.isFinite()) return bestCandidate
        if (bestCandidate.node.id == current.node.id) return current

        val threshold = current.score * (1.0 - SWITCH_IMPROVEMENT_RATIO)
        return if (bestCandidate.score < threshold) bestCandidate else current
    }
}
