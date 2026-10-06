package com.boostlab.app.network

import com.boostlab.app.model.CatalogGame
import com.boostlab.app.model.GameCatalogTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
                        routeApiUrl = item.optString("route_api_url").takeIf { it.startsWith("https://") },
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

    suspend fun fetchRouteTargets(
        baseUrl: String,
        packageName: String,
    ): List<GameRouteTarget> = withContext(Dispatchers.IO) {
        val normalized = normalizeBaseUrl(baseUrl)
        val encodedPackage = java.net.URLEncoder.encode(
            packageName.trim(),
            Charsets.UTF_8.name(),
        )
        val connection = openGet("$normalized/v1/route-targets?package_name=$encodedPackage")
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Control API returned HTTP ${connection.responseCode}"
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONArray(body)
            buildList {
                for (index in 0 until json.length()) {
                    val item = json.getJSONObject(index)
                    val id = item.optString("id").trim()
                    val host = item.optString("host").trim()
                    val port = item.optInt("tcp_port")
                    if (
                        id.isNotBlank() &&
                        host.isNotBlank() &&
                        port in 1..65535
                    ) {
                        add(GameRouteTarget(id = id, host = host, tcpPort = port))
                    }
                }
            }.distinctBy { it.id.lowercase() }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun fetchGames(baseUrl: String): List<CatalogGame> = withContext(Dispatchers.IO) {
        val normalized = normalizeBaseUrl(baseUrl)
        val connection = openGet("$normalized/v1/games")

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
                    val title = item.optString("title").trim()
                    if (title.isBlank()) continue
                    val tags = buildSet {
                        val rawTags = item.optJSONArray("tags") ?: JSONArray()
                        for (tagIndex in 0 until rawTags.length()) {
                            when (rawTags.optString(tagIndex).uppercase()) {
                                "HOT" -> add(GameCatalogTag.HOT)
                                "NEW" -> add(GameCatalogTag.NEW)
                            }
                        }
                    }
                    add(CatalogGame(title = title, tags = tags))
                }
            }.distinctBy { it.title.lowercase() }
        } finally {
            connection.disconnect()
        }
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
}
