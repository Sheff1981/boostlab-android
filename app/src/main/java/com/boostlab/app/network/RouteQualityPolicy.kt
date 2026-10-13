package com.boostlab.app.network

data class RouteQualityAssessment(
    val level: String,
    val reason: String? = null,
)

object RouteQualityPolicy {
    fun assess(metrics: RouteMetrics): RouteQualityAssessment {
        val median = metrics.medianRttMs
            ?: return RouteQualityAssessment(
                level = "UNREACHABLE",
                reason = "Нет ответа от маршрута",
            )
        if (metrics.received <= 0) {
            return RouteQualityAssessment(
                level = "UNREACHABLE",
                reason = "Маршрут не отвечает",
            )
        }

        val jitter = metrics.jitterMs ?: 0
        val p95 = metrics.p95RttMs ?: median
        val tail = (p95 - median).coerceAtLeast(0)
        val loss = metrics.packetLossPct

        return when {
            loss >= SEVERE_LOSS_PCT ->
                RouteQualityAssessment("SEVERE", "Потери пакетов ${formatPct(loss)}")
            jitter >= SEVERE_JITTER_MS ->
                RouteQualityAssessment("SEVERE", "Jitter ${jitter} ms")
            tail >= SEVERE_TAIL_MS ->
                RouteQualityAssessment("SEVERE", "Пики задержки +${tail} ms")
            loss >= DEGRADED_LOSS_PCT ->
                RouteQualityAssessment("DEGRADED", "Потери пакетов ${formatPct(loss)}")
            jitter >= DEGRADED_JITTER_MS ->
                RouteQualityAssessment("DEGRADED", "Jitter ${jitter} ms")
            tail >= DEGRADED_TAIL_MS ->
                RouteQualityAssessment("DEGRADED", "Пики задержки +${tail} ms")
            else -> RouteQualityAssessment("GOOD")
        }
    }

    fun isBad(level: String): Boolean {
        return level == "DEGRADED" || level == "SEVERE" || level == "UNREACHABLE"
    }

    private fun formatPct(value: Double): String =
        "%.1f%%".format(java.util.Locale.US, value)

    private const val DEGRADED_LOSS_PCT = 2.0
    private const val SEVERE_LOSS_PCT = 5.0
    private const val DEGRADED_JITTER_MS = 20
    private const val SEVERE_JITTER_MS = 40
    private const val DEGRADED_TAIL_MS = 50
    private const val SEVERE_TAIL_MS = 100
}
