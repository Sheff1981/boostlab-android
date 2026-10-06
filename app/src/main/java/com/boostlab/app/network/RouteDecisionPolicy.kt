package com.boostlab.app.network

object RouteDecisionPolicy {
    private const val SWITCH_SCORE_IMPROVEMENT_RATIO = 0.20
    private const val SWITCH_MEDIAN_GAIN_MS = 12

    fun choose(
        current: GatewayMeasurement?,
        bestCandidate: GatewayMeasurement,
    ): GatewayMeasurement {
        if (current == null) return bestCandidate
        if (!current.score.isFinite()) return bestCandidate
        if (bestCandidate.node.id == current.node.id) return current
        if (!bestCandidate.score.isFinite()) return current

        val currentMedian = current.metrics.medianRttMs
        val candidateMedian = bestCandidate.metrics.medianRttMs
        val medianGain = if (currentMedian != null && candidateMedian != null) {
            currentMedian - candidateMedian
        } else {
            Int.MIN_VALUE
        }

        val scoreImprovementRatio = if (current.score > 0.0) {
            (current.score - bestCandidate.score) / current.score
        } else {
            0.0
        }

        return if (
            medianGain >= SWITCH_MEDIAN_GAIN_MS ||
            scoreImprovementRatio >= SWITCH_SCORE_IMPROVEMENT_RATIO
        ) {
            bestCandidate
        } else {
            current
        }
    }
}
