package com.boostlab.app.network

import com.boostlab.app.model.PlanTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ControlPlaneClient {
    suspend fun fetchNodes(baseUrl: String): List<GatewayNode> = withContext(Dispatchers.IO) {
        val normalized = normalizeBaseUrl(baseUrl)
        val connection = openGet("$normalized/v1/nodes")

        try {
            val code = connection.responseCode
            require(code == HttpURLConnection.HTTP_OK) {
                "Control API returned HTTP $code"
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONArray(body)

            buildList {
                for (index in 0 until json.length()) {
                    val item = json.getJSONObject(index)
                    val wireGuardPublicKey = item.optString("wireguard_public_key")
                        .takeIf { it.isNotBlank() }
                    val wireGuardPort = if (item.has("wireguard_port")) {
                        item.optInt("wireguard_port").takeIf { it in 1..65535 }
                    } else {
                        null
                    }

                    val node = GatewayNode(
                        id = item.getString("id"),
                        region = item.getString("region"),
                        host = item.getString("host"),
                        udpPort = item.getInt("udp_port"),
                        wireGuardPublicKey = wireGuardPublicKey,
                        wireGuardPort = wireGuardPort,
                        healthy = item.optBoolean("healthy", true),
                    )

                    if (
                        node.id.isNotBlank() &&
                        node.region.isNotBlank() &&
                        node.host.isNotBlank() &&
                        node.udpPort in 1..65535 &&
                        node.healthy
                    ) {
                        add(node)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun fetchClientPolicy(baseUrl: String): ClientPolicy = withContext(Dispatchers.IO) {
        val normalized = normalizeBaseUrl(baseUrl)
        val connection = openGet("$normalized/v1/client-policy")

        try {
            val code = connection.responseCode
            require(code == HttpURLConnection.HTTP_OK) {
                "Control API returned HTTP $code"
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            ClientPolicy(
                defaultTier = when (json.optString("default_tier").lowercase()) {
                    "premium" -> PlanTier.PREMIUM
                    else -> PlanTier.FREE
                },
                free = parseTierPolicy(json.getJSONObject("free")),
                premium = parseTierPolicy(json.getJSONObject("premium")),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun parseTierPolicy(json: JSONObject): TierPolicy {
        return TierPolicy(
            adsEnabled = json.optBoolean("ads_enabled", false),
            maxAutoCandidates = json.optInt("max_auto_candidates", 1)
                .coerceIn(1, MAX_POLICY_CANDIDATES),
            priorityRouting = json.optBoolean("priority_routing", false),
        )
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        require(normalized.startsWith("https://")) {
            "Control API must use HTTPS"
        }
        return normalized
    }

    private fun openGet(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3_000
            readTimeout = 3_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
        }
    }

    companion object {
        private const val MAX_POLICY_CANDIDATES = 32
    }
}
