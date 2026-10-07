package com.boostlab.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GatewayPeerRegistration(
    val deviceId: String,
    val tunnelAddress: String,
)

class GatewayProvisionClient {
    suspend fun registerPeer(
        gatewayUrl: String,
        ticket: String,
    ): GatewayPeerRegistration = withContext(Dispatchers.IO) {
        val base = gatewayUrl.trim().trimEnd('/')
        require(base.startsWith("https://")) { "Gateway provisioning API must use HTTPS" }

        val connection = (URL("$base/v1/peers/register").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 4_000
            readTimeout = 5_000
            instanceFollowRedirects = false
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${ticket.trim()}")
            outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
        }

        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Gateway peer registration HTTP ${connection.responseCode}"
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            GatewayPeerRegistration(
                deviceId = json.getString("device_id"),
                tunnelAddress = json.getString("tunnel_address"),
            )
        } finally {
            connection.disconnect()
        }
    }
}
