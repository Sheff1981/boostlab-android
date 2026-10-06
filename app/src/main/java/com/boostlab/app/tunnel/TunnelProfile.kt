package com.boostlab.app.tunnel

data class TunnelProfile(
    val privateKey: String,
    val serverPublicKey: String,
    val endpointHost: String,
    val endpointPort: Int,
    val addressCidr: String,
    val dnsServer: String,
    val selectedPackage: String,
    val mtu: Int = 1380,
    val persistentKeepaliveSeconds: Int = 25,
)
