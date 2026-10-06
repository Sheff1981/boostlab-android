package com.boostlab.app.boost

import com.boostlab.app.model.GameLaunchMode
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GameLaunchPolicyTest {
    @Test
    fun smartBlocksSevereThermalStatus() {
        assertNotNull(
            GameLaunchPolicy.blockReason(
                GameLaunchMode.SMART,
                snapshot(thermalStatus = 4),
            ),
        )
    }

    @Test
    fun smartAllowsMissingValidatedNetwork() {
        assertNull(
            GameLaunchPolicy.blockReason(
                GameLaunchMode.SMART,
                snapshot(networkValidated = false),
            ),
        )
    }

    @Test
    fun onlineBlocksMissingValidatedNetwork() {
        assertNotNull(
            GameLaunchPolicy.blockReason(
                GameLaunchMode.ONLINE,
                snapshot(networkValidated = false),
            ),
        )
    }

    @Test
    fun alwaysAllowsSevereThermalAndMissingNetwork() {
        assertNull(
            GameLaunchPolicy.blockReason(
                GameLaunchMode.ALWAYS,
                snapshot(thermalStatus = 5, networkValidated = false),
            ),
        )
    }

    @Test
    fun unavailableDiagnosticsFailOpen() {
        assertNull(GameLaunchPolicy.blockReason(GameLaunchMode.ONLINE, null))
    }

    private fun snapshot(
        thermalStatus: Int? = 0,
        networkValidated: Boolean? = true,
    ) = GameReadinessSnapshot(
        availableMemoryMb = 2048,
        totalMemoryMb = 4096,
        availableMemoryPercent = 50,
        lowMemory = false,
        lowRamDevice = false,
        powerSaveMode = false,
        thermalStatus = thermalStatus,
        networkValidated = networkValidated,
        networkTransport = "Wi-Fi",
    )
}
