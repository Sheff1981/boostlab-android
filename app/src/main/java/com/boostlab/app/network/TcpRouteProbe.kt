package com.boostlab.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class TcpRouteProbe {
    suspend fun measure(
        host: String,
        port: Int,
        samples: Int = DEFAULT_SAMPLES,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
        gapMs: Long = DEFAULT_GAP_MS,
    ): RouteMetrics = withContext(Dispatchers.IO) {
        require(host.isNotBlank()) { "Route target host is empty" }
        require(port in 1..65535) { "Invalid route target port" }
        require(samples in 1..20) { "Invalid sample count" }

        val results = MutableList<Long?>(samples) { null }
        repeat(samples) { index ->
            val started = System.nanoTime()
            runCatching {
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(host.trim(), port), timeoutMs)
                }
            }.onSuccess {
                results[index] = ((System.nanoTime() - started) / 1_000_000L)
                    .coerceAtLeast(1L)
            }

            if (index != samples - 1) {
                delay(gapMs)
            }
        }

        RouteMetricsCalculator.calculate(results)
    }

    companion object {
        const val DEFAULT_SAMPLES = 7
        const val DEFAULT_TIMEOUT_MS = 900
        const val DEFAULT_GAP_MS = 90L
    }
}
