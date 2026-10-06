package com.boostlab.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GatewayRouteQualityClient {
    suspend fun fetch(
        routeApiUrl: String,
        targetId: String,
    ): GatewayRouteMetrics = withContext(Dispatchers.IO) {
        val base = routeApiUrl.trim().trimEnd('/')
        require(base.startsWith("https://")) { "Gateway route API must use HTTPS" }
        val encoded = URLEncoder.encode(targetId.trim(), Charsets.UTF_8.name())
        val connection = (URL("$base/v1/route-quality/$encoded").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 2_500
            readTimeout = 5_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
        }

        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Gateway route API returned HTTP ${connection.responseCode}"
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val median = json.optInt("median_rtt_ms", -1).takeIf { it >= 0 }
            val p95 = json.optInt("p95_rtt_ms", -1).takeIf { it >= 0 }
            val jitter = json.optInt("jitter_ms", -1).takeIf { it >= 0 }
            val sent = json.optInt("sent", 0)
            val received = json.optInt("received", 0)

            GatewayRouteMetrics(
                targetId = json.optString("target_id").ifBlank { targetId },
                metrics = RouteMetrics(
                    medianRttMs = median,
                    jitterMs = jitter,
                    packetLossPct = json.optDouble("packet_loss_pct", 100.0),
                    sent = sent,
                    received = received,
                    p95RttMs = p95,
                ),
            )
        } finally {
            connection.disconnect()
        }
    }
}
