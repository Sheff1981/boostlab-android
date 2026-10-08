package com.boostlab.app.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WarmRoutePolicyTest {
    private val now = 1_800_000_000_000L

    @Test
    fun acceptsFreshPreviouslyUsefulRoute() {
        assertTrue(
            WarmRoutePolicy.canFastVerify(
                updatedAtEpochMs = now - 10 * 60_000L,
                previousGainMs = 12,
                nowEpochMs = now,
            ),
        )
    }

    @Test
    fun rejectsOldRoute() {
        assertFalse(
            WarmRoutePolicy.canFastVerify(
                updatedAtEpochMs = now - 31 * 60_000L,
                previousGainMs = 20,
                nowEpochMs = now,
            ),
        )
    }

    @Test
    fun rejectsPreviouslyMarginalRoute() {
        assertFalse(
            WarmRoutePolicy.canFastVerify(
                updatedAtEpochMs = now - 5 * 60_000L,
                previousGainMs = 5,
                nowEpochMs = now,
            ),
        )
    }

    @Test
    fun rejectsClockRollback() {
        assertFalse(
            WarmRoutePolicy.canFastVerify(
                updatedAtEpochMs = now + 1_000L,
                previousGainMs = 15,
                nowEpochMs = now,
            ),
        )
    }
}
