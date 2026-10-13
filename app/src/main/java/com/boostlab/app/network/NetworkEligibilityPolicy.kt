package com.boostlab.app.network

data class NetworkEligibility(
    val allowed: Boolean,
    val reason: String? = null,
)

object NetworkEligibilityPolicy {
    fun evaluate(
        networkValidated: Boolean?,
        networkTransport: String?,
        vpnActive: Boolean,
    ): NetworkEligibility {
        if (vpnActive) {
            return NetworkEligibility(
                allowed = false,
                reason = "Отключи другой VPN перед запуском BOOSTLAB",
            )
        }

        if (networkValidated == false) {
            return NetworkEligibility(
                allowed = false,
                reason = "Нет подтверждённого интернет-соединения",
            )
        }

        // Wi-Fi, Mobile, Ethernet and other validated transports are equally eligible.
        // BOOSTLAB chooses routes by measured quality, never by transport preference.
        return NetworkEligibility(
            allowed = true,
            reason = null,
        )
    }
}
