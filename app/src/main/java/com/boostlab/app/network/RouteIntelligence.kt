package com.boostlab.app.network

import kotlin.math.max
import kotlin.math.roundToInt

data class IntelligentRouteCandidate(
    val node: GatewayNode,
    val target: GameRouteTarget,
    val directMetrics: RouteMetrics,
    val phoneToGatewayMetrics: RouteMetrics,
    val gatewayToGameMetrics: RouteMetrics,
    val boostedMetrics: RouteMetrics,
    val directScore: Double,
    val boostedScore: Double,
) {
    val gainMs: Int?
        get() {
            val direct = directMetrics.medianRttMs ?: return null
            val boosted = boostedMetrics.medianRttMs ?: return null
            return direct - boosted
        }
}

data class AutoRouteSelection(
    val gateway: GatewayMeasurement?,
    val recommendation: String,
    val target: GameRouteTarget? = null,
    val directMetrics: RouteMetrics? = null,
    val boostedMetrics: RouteMetrics? = null,
    val gainMs: Int? = null,
    val candidatesTested: Int = 0,
)

object RouteIntelligence {
    fun buildCandidate(
        node: GatewayNode,
        target: GameRouteTarget,
        directByTarget: Map<String, RouteMetrics>,
        phoneToGatewayMetrics: RouteMetrics,
        gatewayToGameMetrics: RouteMetrics,
        scorer: (RouteMetrics) -> Double = RouteScorer::score,
    ): IntelligentRouteCandidate? {
        val directMetrics = directByTarget[target.id] ?: return null
        val boostedMetrics = combine(phoneToGatewayMetrics, gatewayToGameMetrics)
        val directScore = scorer(directMetrics)
        val boostedScore = scorer(boostedMetrics)
        if (!directScore.isFinite() || !boostedScore.isFinite()) return null

        return IntelligentRouteCandidate(
            node = node,
            target = target,
            directMetrics = directMetrics,
            phoneToGatewayMetrics = phoneToGatewayMetrics,
            gatewayToGameMetrics = gatewayToGameMetrics,
            boostedMetrics = boostedMetrics,
            directScore = directScore,
            boostedScore = boostedScore,
        )
    }

    fun combine(first: RouteMetrics, second: RouteMetrics): RouteMetrics {
        val median = sumNullable(first.medianRttMs, second.medianRttMs)
        val p95 = sumNullable(
            first.p95RttMs ?: first.medianRttMs,
            second.p95RttMs ?: second.medianRttMs,
        )
        val jitter = sumNullable(first.jitterMs, second.jitterMs)
        val loss = 100.0 * (
            1.0 -
                ((1.0 - first.packetLossPct.coerceIn(0.0, 100.0) / 100.0) *
                    (1.0 - second.packetLossPct.coerceIn(0.0, 100.0) / 100.0))
            )

        return RouteMetrics(
            medianRttMs = median,
            jitterMs = jitter,
            packetLossPct = loss.coerceIn(0.0, 100.0),
            sent = minOf(first.sent, second.sent),
            received = minOf(first.received, second.received),
            p95RttMs = p95,
        )
    }

    fun shouldUseBoost(candidate: IntelligentRouteCandidate, mode: String): Boolean {
        val directMedian = candidate.directMetrics.medianRttMs ?: return true
        val boostedMedian = candidate.boostedMetrics.medianRttMs ?: return false

        val gainMs = directMedian - boostedMedian
        val minimumMeaningfulGainMs = max(
            6,
            (directMedian * MIN_GAIN_RATIO).roundToInt(),
        )
        val scoreRatio = if (
            candidate.directScore.isFinite() &&
            candidate.directScore > 0.0 &&
            candidate.boostedScore.isFinite()
        ) {
            candidate.boostedScore / candidate.directScore
        } else {
            Double.POSITIVE_INFINITY
        }

        return when (mode) {
            "LOW_PING" -> gainMs >= minimumMeaningfulGainMs
            "STABLE" -> {
                scoreRatio <= 0.85 &&
                    candidate.boostedMetrics.packetLossPct <=
                    candidate.directMetrics.packetLossPct + 0.5
            }
            else -> {
                (gainMs >= minimumMeaningfulGainMs && scoreRatio <= 0.98) ||
                    (
                        scoreRatio <= 0.80 &&
                            boostedMedian <= directMedian + 4
                        )
            }
        }
    }

    private fun sumNullable(a: Int?, b: Int?): Int? {
        if (a == null || b == null) return null
        return a + b
    }

    private const val MIN_GAIN_RATIO = 0.10
}
