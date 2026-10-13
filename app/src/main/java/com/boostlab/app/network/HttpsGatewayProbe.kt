package com.boostlab.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

class HttpsGatewayProbe {
    suspend fun measure(
        routeApiUrl: String,
        samples: Int = DEFAULT_SAMPLES,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
        gapMs: Long = DEFAULT_GAP_MS,
    ): RouteMetrics = withContext(Dispatchers.IO) {
        val base = routeApiUrl.trim().trimEnd('/')
        require(base.startsWith("https://")) { "Gateway fallback probe requires HTTPS" }
        require(samples in 1..10) { "Invalid HTTPS probe sample count" }

        val endpoint = "$base/readyz"
        val results = MutableList<Long?>(samples) { null }

        repeat(samples) { index ->
            val started = System.nanoTime()
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = false
                setRequestProperty("Accept", "text/plain")
                setRequestProperty("Cache-Control", "no-cache")
            }

            try {
                if (connection.responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                    results[index] = max(
                        1L,
                        (System.nanoTime() - started) / 1_000_000L,
                    )
                }
            } catch (_: Exception) {
                results[index] = null
            } finally {
                connection.disconnect()
            }

            if (index != samples - 1) {
                delay(gapMs)
            }
        }

        RouteMetricsCalculator.calculate(results)
    }

    companion object {
        private const val DEFAULT_SAMPLES = 3
        private const val DEFAULT_TIMEOUT_MS = 1_500
        private const val DEFAULT_GAP_MS = 80L
    }
}
