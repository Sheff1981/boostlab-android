package com.boostlab.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class ControlPlaneClient {
    suspend fun fetchNodes(baseUrl: String): List<GatewayNode> = withContext(Dispatchers.IO) {
        val normalized = baseUrl.trim().trimEnd('/')
        require(normalized.startsWith("https://")) {
            "Control API must use HTTPS"
        }

        val connection = (URL("$normalized/v1/nodes").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3_000
            readTimeout = 3_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
        }

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
}
