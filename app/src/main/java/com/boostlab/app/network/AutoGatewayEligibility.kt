package com.boostlab.app.network

object AutoGatewayEligibility {
    fun isEligible(measurement: GatewayMeasurement): Boolean {
        return measurement.metrics.received > 0 &&
            measurement.score.isFinite() &&
            measurement.metrics.packetLossPct < MAX_PACKET_LOSS_PCT &&
            !measurement.node.wireGuardPublicKey.isNullOrBlank() &&
            measurement.node.wireGuardPort != null
    }

    const val MAX_PACKET_LOSS_PCT = 25.0
}
