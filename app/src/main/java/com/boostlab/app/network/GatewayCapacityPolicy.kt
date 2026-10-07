package com.boostlab.app.network

object GatewayCapacityPolicy {
    fun penalty(status: GatewayRuntimeStatus?): Double {
        if (status == null || status.cpuCount <= 0) return 0.0
        val load1 = status.load1 ?: return 0.0
        val normalized = load1 / status.cpuCount.toDouble()

        return when {
            normalized <= 0.70 -> 0.0
            normalized <= 1.00 -> ((normalized - 0.70) / 0.30) * 8.0
            else -> (8.0 + ((normalized - 1.0) * 30.0)).coerceAtMost(50.0)
        }
    }

    fun overloaded(status: GatewayRuntimeStatus?): Boolean {
        if (status == null || status.cpuCount <= 0) return false
        val load1 = status.load1 ?: return false
        return (load1 / status.cpuCount.toDouble()) >= 1.50
    }
}
