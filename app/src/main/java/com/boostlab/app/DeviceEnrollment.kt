package com.boostlab.app

import com.wireguard.config.Config
import com.wireguard.config.InetEndpoint
import com.wireguard.config.InetNetwork
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.KeyPair
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * First-run enrollment. Private key is generated on-device and never transmitted.
 * Only call with an HTTPS control-plane URL and a server-issued one-time token.
 * Production must store the private key using Android Keystore-backed encryption.
 */
internal object DeviceEnrollment {
    data class Result(val config: Config, val deviceId: String)

    fun enroll(apiUrl: String, token: String): Result {
        require(apiUrl.startsWith("https://")) { "HTTPS is required" }
        require(token.length >= 32) { "Invalid enrollment token" }
        val identity = KeyPair()
        val connection = (URL(apiUrl.trimEnd('/') + "/v1/devices/enroll").openConnection() as HttpURLConnection)
        connection.requestMethod = "POST"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.outputStream.use { stream ->
            stream.write(JSONObject().put("enrollment_token", token)
                .put("public_key", identity.publicKey.toBase64()).toString().toByteArray(Charsets.UTF_8))
        }
        try {
            require(connection.responseCode == 201) { "Enrollment HTTP ${connection.responseCode}" }
            val response = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val interfaceConfig = Interface.Builder()
                .setKeyPair(identity)
                .addAddress(InetNetwork.parse(response.getString("address")))
                .addDnsServer(java.net.InetAddress.getByName("1.1.1.1"))
                .build()
            val peer = Peer.Builder()
                .parsePublicKey(response.getString("gateway_public_key"))
                .setEndpoint(InetEndpoint.parse(response.getString("gateway_endpoint")))
                .addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
                .setPersistentKeepalive(25)
                .build()
            return Result(Config.Builder().setInterface(interfaceConfig).addPeer(peer).build(),
                response.getString("device_id"))
        } finally {
            connection.disconnect()
        }
    }
}
