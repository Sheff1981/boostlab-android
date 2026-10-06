package com.boostlab.app.tunnel

import com.wireguard.config.Config
import java.io.ByteArrayInputStream

object WireGuardConfigFactory {
    fun build(profile: TunnelProfile): Config {
        require(profile.privateKey.isNotBlank()) { "Client private key is required" }
        require(profile.serverPublicKey.isNotBlank()) { "Server public key is required" }
        require(profile.endpointHost.isNotBlank()) { "Gateway endpoint is required" }
        require(profile.endpointPort in 1..65535) { "Invalid gateway port" }
        require(profile.addressCidr.isNotBlank()) { "Tunnel address is required" }
        require(profile.dnsServer.isNotBlank()) { "DNS server is required" }
        require(profile.selectedPackage.isNotBlank()) { "Selected application is required" }
        require(profile.mtu in 1280..1500) { "Unsupported MTU" }
        require(profile.persistentKeepaliveSeconds in 0..65535) { "Invalid keepalive" }

        val text = buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = ${profile.privateKey}")
            appendLine("Address = ${profile.addressCidr}")
            appendLine("DNS = ${profile.dnsServer}")
            appendLine("MTU = ${profile.mtu}")
            appendLine("IncludedApplications = ${profile.selectedPackage}")
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = ${profile.serverPublicKey}")
            appendLine("AllowedIPs = 0.0.0.0/0, ::/0")
            appendLine("Endpoint = ${profile.endpointHost}:${profile.endpointPort}")
            if (profile.persistentKeepaliveSeconds > 0) {
                appendLine("PersistentKeepalive = ${profile.persistentKeepaliveSeconds}")
            }
        }

        return ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)).use(Config::parse)
    }
}
