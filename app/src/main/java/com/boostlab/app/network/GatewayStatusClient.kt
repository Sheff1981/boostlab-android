package com.boostlab.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GatewayRuntimeStatus(
    val cpuCount: Int,
    val goroutines: Int,
    val heapAllocMb: Double,
    val load1: Double?,
    val dataPlaneConfigured: Boolean = true,
    val dataPlaneReady: Boolean = true,
)

class GatewayStatusClient {
    suspend fun fetch(routeApiUrl: String): GatewayRuntimeStatus = withContext(Dispatchers.IO) {
        val base = routeApiUrl.trim().trimEnd('/')
        require(base.startsWith("https://")) { "Gateway status API must use HTTPS" }

        val connection = (URL("$base/v1/status").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 1_500
            readTimeout = 2_500
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
        }

        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Gateway status returned HTTP ${connection.responseCode}"
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            GatewayRuntimeStatus(
                cpuCount = json.optInt("cpu_count", 0).coerceAtLeast(0),
                goroutines = json.optInt("goroutines", 0).coerceAtLeast(0),
                heapAllocMb = json.optDouble("heap_alloc_mb", 0.0).coerceAtLeast(0.0),
                load1 = if (json.has("load1") && !json.isNull("load1")) {
                    json.optDouble("load1").takeIf { it.isFinite() && it >= 0.0 }
                } else {
                    null
                },
                dataPlaneConfigured = json.optBoolean("data_plane_configured", true),
                dataPlaneReady = json.optBoolean("data_plane_ready", true),
            )
        } finally {
            connection.disconnect()
        }
    }
}
