package com.boostlab.app.data

import android.content.Context
import com.boostlab.app.BuildConfig

data class PrivateServerProfile(
    val selectedPackage: String?,
    val controlPlaneUrl: String,
    val gatewayHost: String,
    val gatewayPort: Int,
    val wireGuardServerPublicKey: String,
    val wireGuardPort: Int,
    val tunnelAddress: String,
    val dnsServer: String,
    val provisioningEnrollmentCode: String,
)

class PrivateProfileStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): PrivateServerProfile = PrivateServerProfile(
        selectedPackage = preferences.getString(KEY_SELECTED_PACKAGE, null),
        controlPlaneUrl = preferences.getString(
            KEY_CONTROL_URL,
            BuildConfig.BOOSTLAB_DEFAULT_CONTROL_URL,
        ) ?: BuildConfig.BOOSTLAB_DEFAULT_CONTROL_URL,
        gatewayHost = preferences.getString(
            KEY_GATEWAY_HOST,
            BuildConfig.BOOSTLAB_DEFAULT_GATEWAY_HOST,
        ) ?: BuildConfig.BOOSTLAB_DEFAULT_GATEWAY_HOST,
        gatewayPort = if (preferences.contains(KEY_GATEWAY_PORT)) {
            preferences.getInt(KEY_GATEWAY_PORT, BuildConfig.BOOSTLAB_DEFAULT_GATEWAY_PORT)
        } else {
            BuildConfig.BOOSTLAB_DEFAULT_GATEWAY_PORT
        },
        wireGuardServerPublicKey = preferences.getString(
            KEY_WG_PUBLIC_KEY,
            BuildConfig.BOOSTLAB_DEFAULT_WG_PUBLIC_KEY,
        ) ?: BuildConfig.BOOSTLAB_DEFAULT_WG_PUBLIC_KEY,
        wireGuardPort = if (preferences.contains(KEY_WG_PORT)) {
            preferences.getInt(KEY_WG_PORT, BuildConfig.BOOSTLAB_DEFAULT_WG_PORT)
        } else {
            BuildConfig.BOOSTLAB_DEFAULT_WG_PORT
        },
        tunnelAddress = preferences.getString(
            KEY_TUNNEL_ADDRESS,
            BuildConfig.BOOSTLAB_DEFAULT_TUNNEL_ADDRESS,
        ) ?: BuildConfig.BOOSTLAB_DEFAULT_TUNNEL_ADDRESS,
        dnsServer = preferences.getString(
            KEY_DNS_SERVER,
            BuildConfig.BOOSTLAB_DEFAULT_DNS_SERVER,
        ) ?: BuildConfig.BOOSTLAB_DEFAULT_DNS_SERVER,
        provisioningEnrollmentCode = preferences.getString(
            KEY_ENROLLMENT_CODE,
            "",
        ).orEmpty(),
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
            .putString(KEY_ENROLLMENT_CODE, profile.provisioningEnrollmentCode)
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
        private const val KEY_ENROLLMENT_CODE = "provisioning_enrollment_code"
    }
}
