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
        capacityPenalty: Double = 0.0,
        scorer: (RouteMetrics) -> Double = RouteScorer::score,
    ): IntelligentRouteCandidate? {
        val directMetrics = directByTarget[target.id] ?: return null
        val boostedMetrics = combine(phoneToGatewayMetrics, gatewayToGameMetrics)
        val directScore = scorer(directMetrics)
        val boostedScore = scorer(boostedMetrics) + capacityPenalty.coerceAtLeast(0.0)
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

        val combinedLoss = loss.coerceIn(0.0, 100.0)
        val sent = minOf(first.sent, second.sent)
        val received = if (sent == 0) {
            0
        } else {
            (sent * (1.0 - combinedLoss / 100.0))
                .roundToInt()
                .coerceIn(0, sent)
        }

        return RouteMetrics(
            medianRttMs = median,
            jitterMs = jitter,
            packetLossPct = combinedLoss,
            sent = sent,
            received = received,
            p95RttMs = p95,
        )
    }

    fun shouldUseBoost(candidate: IntelligentRouteCandidate, mode: String): Boolean {
        val directMedian = candidate.directMetrics.medianRttMs ?: return true
        val boostedMedian = candidate.boostedMetrics.medianRttMs ?: return false

        val gainMs = directMedian - boostedMedian

        val maxAllowedLoss = max(
            2.0,
            candidate.directMetrics.packetLossPct + 1.0,
        )
        if (candidate.boostedMetrics.packetLossPct > maxAllowedLoss) {
            return false
        }

        val directP95 = candidate.directMetrics.p95RttMs
        val boostedP95 = candidate.boostedMetrics.p95RttMs
        if (directP95 != null && boostedP95 != null) {
            val p95Budget = max(20, (directP95 * 0.25).roundToInt())
            if (boostedP95 > directP95 + p95Budget) {
                return false
            }
        }

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
