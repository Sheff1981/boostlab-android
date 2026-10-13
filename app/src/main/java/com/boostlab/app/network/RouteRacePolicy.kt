package com.boostlab.app.network

object RouteRacePolicy {
    fun shortlist(
        quickMeasurements: List<GatewayMeasurement>,
        rememberedGatewayId: String?,
        limit: Int = 3,
    ): List<GatewayNode> {
        require(limit >= 1) { "limit must be >= 1" }

        val eligible = quickMeasurements
            .filter {
                it.metrics.received > 0 &&
                    it.score.isFinite() &&
                    !it.node.wireGuardPublicKey.isNullOrBlank() &&
                    it.node.wireGuardPort != null
            }
            .sortedBy { it.score }

        if (eligible.isEmpty()) return emptyList()

        val udp = eligible.filter {
            it.probeTransport == GatewayAccessProbePolicy.UDP
        }
        val fallback = eligible.filter {
            it.probeTransport != GatewayAccessProbePolicy.UDP
        }

        val result = LinkedHashMap<String, GatewayNode>(limit + 1)

        val remembered = rememberedGatewayId
            ?.let { id -> eligible.firstOrNull { it.node.id == id } }

        if (
            remembered != null &&
            (
                remembered.probeTransport == GatewayAccessProbePolicy.UDP ||
                    udp.size < limit
                )
        ) {
            result[remembered.node.id] = remembered.node
        }

        udp.forEach { measurement ->
            if (result.size >= limit) return@forEach
            result.putIfAbsent(measurement.node.id, measurement.node)
        }

        fallback.forEach { measurement ->
            if (result.size >= limit) return@forEach
            result.putIfAbsent(measurement.node.id, measurement.node)
        }

        return result.values.toList()
    }
}
