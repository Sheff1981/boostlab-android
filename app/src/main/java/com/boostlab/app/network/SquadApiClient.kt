package com.boostlab.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RemoteSquadEvent(
    val id: Long,
    val sender: String,
    val type: String,
    val text: String,
    val payload: String,
    val createdAt: String,
)

data class RemoteSquadPresence(
    val userId: String,
    val seenAt: String,
)

data class RemoteIceServer(
    val urls: List<String>,
    val username: String,
    val credential: String,
)

class SquadApiClient {
    suspend fun sendChat(baseUrl: String, code: String, sender: String, text: String): RemoteSquadEvent =
        sendEvent(baseUrl, code, sender, "chat", text, "")


    suspend fun fetchVoiceIce(baseUrl: String): List<RemoteIceServer> = withContext(Dispatchers.IO) {
        val connection = open("${normalizeBaseUrl(baseUrl)}/v1/voice/ice", "GET")
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Voice ICE HTTP ${connection.responseCode}"
            }
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val array = root.optJSONArray("ice_servers") ?: JSONArray()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val rawUrls = item.optJSONArray("urls") ?: JSONArray()
                    val urls = buildList {
                        for (urlIndex in 0 until rawUrls.length()) {
                            rawUrls.optString(urlIndex).trim()
                                .takeIf { it.startsWith("stun:") || it.startsWith("turn:") || it.startsWith("turns:") }
                                ?.let(::add)
                        }
                    }.distinct()
                    if (urls.isNotEmpty()) {
                        add(
                            RemoteIceServer(
                                urls = urls,
                                username = item.optString("username"),
                                credential = item.optString("credential"),
                            ),
                        )
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun sendSignal(
        baseUrl: String,
        code: String,
        sender: String,
        type: String,
        payload: String,
    ): RemoteSquadEvent = sendEvent(baseUrl, code, sender, type, "", payload)

    suspend fun fetchEvents(baseUrl: String, code: String, after: Long): List<RemoteSquadEvent> =
        withContext(Dispatchers.IO) {
            val url = "${normalizeBaseUrl(baseUrl)}/v1/squads/${code.trim()}/events?after=$after"
            val connection = open(url, "GET")
            try {
                require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                    "Squad events HTTP ${connection.responseCode}"
                }
                parseEvents(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        }

    suspend fun touchPresence(baseUrl: String, code: String, userId: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("user_id", userId).toString()
        val connection = open("${normalizeBaseUrl(baseUrl)}/v1/squads/${code.trim()}/presence", "POST")
        try {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            require(connection.responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                "Squad presence HTTP ${connection.responseCode}"
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun fetchPresence(baseUrl: String, code: String): List<RemoteSquadPresence> =
        withContext(Dispatchers.IO) {
            val connection = open("${normalizeBaseUrl(baseUrl)}/v1/squads/${code.trim()}/presence", "GET")
            try {
                require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                    "Squad presence HTTP ${connection.responseCode}"
                }
                val array = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        add(
                            RemoteSquadPresence(
                                userId = item.optString("user_id"),
                                seenAt = item.optString("seen_at"),
                            ),
                        )
                    }
                }.filter { it.userId.isNotBlank() }
            } finally {
                connection.disconnect()
            }
        }

    private suspend fun sendEvent(
        baseUrl: String,
        code: String,
        sender: String,
        type: String,
        text: String,
        payload: String,
    ): RemoteSquadEvent = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("sender", sender)
            .put("type", type)
            .put("text", text)
            .put("payload", payload)
            .toString()

        val connection = open("${normalizeBaseUrl(baseUrl)}/v1/squads/${code.trim()}/events", "POST")
        try {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            require(connection.responseCode == HttpURLConnection.HTTP_CREATED) {
                "Squad event HTTP ${connection.responseCode}"
            }
            parseEvent(JSONObject(connection.inputStream.bufferedReader().use { it.readText() }))
        } finally {
            connection.disconnect()
        }
    }

    private fun parseEvents(raw: String): List<RemoteSquadEvent> {
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                add(parseEvent(array.getJSONObject(index)))
            }
        }
    }

    private fun parseEvent(item: JSONObject): RemoteSquadEvent = RemoteSquadEvent(
        id = item.optLong("id"),
        sender = item.optString("sender"),
        type = item.optString("type"),
        text = item.optString("text"),
        payload = item.optString("payload"),
        createdAt = item.optString("created_at"),
    )

    private fun normalizeBaseUrl(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        require(normalized.startsWith("https://")) { "Control API must use HTTPS" }
        return normalized
    }

    private fun open(url: String, method: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 4_000
            readTimeout = 4_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
        }
    }
}
