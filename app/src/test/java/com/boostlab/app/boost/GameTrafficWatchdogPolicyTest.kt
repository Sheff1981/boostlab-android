package com.boostlab.app.boost

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTrafficWatchdogPolicyTest {
    @Test
    fun waitsBeforeDeclaringMissingGameTraffic() {
        val now = 1_000_000L
        assertFalse(
            GameTrafficWatchdogPolicy.noGameTraffic(
                gameLaunchedAtEpochMs = now - 10_000L,
                nowEpochMs = now,
                trafficVerified = false,
            ),
        )
    }

    @Test
    fun flagsConnectedTunnelWithoutGameTrafficAfterGracePeriod() {
        val now = 1_000_000L
        assertTrue(
            GameTrafficWatchdogPolicy.noGameTraffic(
                gameLaunchedAtEpochMs = now - 30_000L,
                nowEpochMs = now,
                trafficVerified = false,
            ),
        )
    }

    @Test
    fun verifiedTrafficAlwaysClearsWarning() {
        val now = 1_000_000L
        assertFalse(
            GameTrafficWatchdogPolicy.noGameTraffic(
                gameLaunchedAtEpochMs = now - 30_000L,
                nowEpochMs = now,
                trafficVerified = true,
            ),
        )
    }

    @Test
    fun staleLaunchTimestampDoesNotCreatePermanentWarning() {
        val now = 1_000_000L
        assertFalse(
            GameTrafficWatchdogPolicy.noGameTraffic(
                gameLaunchedAtEpochMs = now - GameTrafficWatchdogPolicy.TRACK_WINDOW_MS - 1L,
                nowEpochMs = now,
                trafficVerified = false,
            ),
        )
    }
}
