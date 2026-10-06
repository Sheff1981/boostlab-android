package com.boostlab.app.data

import android.content.Context

data class PrivateServerProfile(
    val selectedPackage: String?,
    val controlPlaneUrl: String,
    val gatewayHost: String,
    val gatewayPort: Int,
    val wireGuardServerPublicKey: String,
    val wireGuardPort: Int,
    val tunnelAddress: String,
    val dnsServer: String,
)

class PrivateProfileStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): PrivateServerProfile = PrivateServerProfile(
        selectedPackage = preferences.getString(KEY_SELECTED_PACKAGE, null),
        controlPlaneUrl = preferences.getString(KEY_CONTROL_URL, "") ?: "",
        gatewayHost = preferences.getString(KEY_GATEWAY_HOST, "") ?: "",
        gatewayPort = preferences.getInt(KEY_GATEWAY_PORT, 51821),
        wireGuardServerPublicKey = preferences.getString(KEY_WG_PUBLIC_KEY, "") ?: "",
        wireGuardPort = preferences.getInt(KEY_WG_PORT, 51820),
        tunnelAddress = preferences.getString(KEY_TUNNEL_ADDRESS, "10.77.0.2/32")
            ?: "10.77.0.2/32",
        dnsServer = preferences.getString(KEY_DNS_SERVER, "1.1.1.1") ?: "1.1.1.1",
    )

    fun save(profile: PrivateServerProfile) {
        preferences.edit()
            .putString(KEY_SELECTED_PACKAGE, profile.selectedPackage)
            .putString(KEY_CONTROL_URL, profile.controlPlaneUrl)
            .putString(KEY_GATEWAY_HOST, profile.gatewayHost)
            .putInt(KEY_GATEWAY_PORT, profile.gatewayPort)
            .putString(KEY_WG_PUBLIC_KEY, profile.wireGuardServerPublicKey)
            .putInt(KEY_WG_PORT, profile.wireGuardPort)
            .putString(KEY_TUNNEL_ADDRESS, profile.tunnelAddress)
            .putString(KEY_DNS_SERVER, profile.dnsServer)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "boostlab_private_profile"
        private const val KEY_SELECTED_PACKAGE = "selected_package"
        private const val KEY_CONTROL_URL = "control_url"
        private const val KEY_GATEWAY_HOST = "gateway_host"
        private const val KEY_GATEWAY_PORT = "gateway_port"
        private const val KEY_WG_PUBLIC_KEY = "wg_public_key"
        private const val KEY_WG_PORT = "wg_port"
        private const val KEY_TUNNEL_ADDRESS = "tunnel_address"
        private const val KEY_DNS_SERVER = "dns_server"
    }
}
