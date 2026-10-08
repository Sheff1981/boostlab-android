package com.boostlab.app.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedReconnectPolicyTest {
    @Test
    fun retriesSameGatewayOnlyWhenRouteContextIsUnchanged() {
        assertTrue(
            PinnedReconnectPolicy.canRetrySameGateway(
                physicalNetworkChanged = false,
                mtuChanged = false,
                hasSelectedApp = true,
                gatewayHost = "203.0.113.10",
                wireGuardServerPublicKey = "server-key",
            ),
        )
    }

    @Test
    fun blocksRetryAfterNetworkHandoff() {
        assertFalse(
            PinnedReconnectPolicy.canRetrySameGateway(
                physicalNetworkChanged = true,
                mtuChanged = false,
                hasSelectedApp = true,
                gatewayHost = "203.0.113.10",
                wireGuardServerPublicKey = "server-key",
            ),
        )
    }

    @Test
    fun blocksRetryAfterMtuChange() {
        assertFalse(
            PinnedReconnectPolicy.canRetrySameGateway(
                physicalNetworkChanged = false,
                mtuChanged = true,
                hasSelectedApp = true,
                gatewayHost = "203.0.113.10",
                wireGuardServerPublicKey = "server-key",
            ),
        )
    }

    @Test
    fun blocksRetryWithoutPinnedGatewayIdentity() {
        assertFalse(
            PinnedReconnectPolicy.canRetrySameGateway(
                physicalNetworkChanged = false,
                mtuChanged = false,
                hasSelectedApp = true,
                gatewayHost = "",
                wireGuardServerPublicKey = "",
            ),
        )
    }
}
