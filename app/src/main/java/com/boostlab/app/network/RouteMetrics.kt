package com.boostlab.app.network

import kotlin.math.abs
import kotlin.math.roundToInt

data class RouteMetrics(
    val medianRttMs: Int?,
    val jitterMs: Int?,
    val packetLossPct: Double,
    val sent: Int,
    val received: Int,
    val p95RttMs: Int? = null,
)

object RouteMetricsCalculator {
    fun calculate(samplesMs: List<Long?>): RouteMetrics {
        if (samplesMs.isEmpty()) {
            return RouteMetrics(
                medianRttMs = null,
                jitterMs = null,
                packetLossPct = 100.0,
                sent = 0,
                received = 0,
                p95RttMs = null,
            )
        }

        val received = samplesMs.filterNotNull()
        val median = received
            .sorted()
            .let { sorted ->
                when {
                    sorted.isEmpty() -> null
                    sorted.size % 2 == 1 -> sorted[sorted.size / 2].toInt()
                    else -> ((sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0)
                        .roundToInt()
                }
            }

        val p95 = received
            .sorted()
            .takeIf { it.isNotEmpty() }
            ?.let { sorted ->
                val index = kotlin.math.ceil(sorted.size * 0.95).toInt()
                    .coerceIn(1, sorted.size) - 1
                sorted[index].toInt()
            }

        val jitter = if (received.size < 2) {
            null
        } else {
            received.zipWithNext { a, b -> abs(b - a).toDouble() }
                .average()
                .roundToInt()
        }

        val loss = ((samplesMs.size - received.size).toDouble() / samplesMs.size.toDouble()) * 100.0

        return RouteMetrics(
            medianRttMs = median,
            jitterMs = jitter,
            packetLossPct = loss,
            sent = samplesMs.size,
            received = received.size,
            p95RttMs = p95,
        )
    }
}
