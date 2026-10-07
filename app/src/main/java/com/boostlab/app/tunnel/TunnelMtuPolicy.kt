package com.boostlab.app.tunnel

object TunnelMtuPolicy {
    fun choose(
        linkMtu: Int?,
        networkTransport: String?,
    ): Int {
        val measured = linkMtu
            ?.takeIf { it in MIN_LINK_MTU..MAX_REASONABLE_LINK_MTU }
            ?.let { (it - WIREGUARD_OVERHEAD_BUDGET).coerceIn(MIN_TUNNEL_MTU, MAX_TUNNEL_MTU) }

        if (measured != null) return measured

        return when (networkTransport?.trim()?.lowercase()) {
            "wi-fi", "wifi", "ethernet" -> MAX_TUNNEL_MTU
            "mobile", "cellular" -> MOBILE_FALLBACK_MTU
            else -> DEFAULT_FALLBACK_MTU
        }
    }

    private const val WIREGUARD_OVERHEAD_BUDGET = 80
    private const val MIN_LINK_MTU = 1280
    private const val MAX_REASONABLE_LINK_MTU = 9000
    private const val MIN_TUNNEL_MTU = 1280
    private const val MAX_TUNNEL_MTU = 1420
    private const val MOBILE_FALLBACK_MTU = 1380
    private const val DEFAULT_FALLBACK_MTU = 1380
}
