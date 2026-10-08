package com.boostlab.app.network

object PinnedReconnectPolicy {
    fun canRetrySameGateway(
        physicalNetworkChanged: Boolean,
        mtuChanged: Boolean,
        hasSelectedApp: Boolean,
        gatewayHost: String,
        wireGuardServerPublicKey: String,
    ): Boolean {
        return !physicalNetworkChanged &&
            !mtuChanged &&
            hasSelectedApp &&
            gatewayHost.isNotBlank() &&
            wireGuardServerPublicKey.isNotBlank()
    }
}
