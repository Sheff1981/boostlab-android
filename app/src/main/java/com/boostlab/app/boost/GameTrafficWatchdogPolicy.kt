package com.boostlab.app.boost

object GameTrafficWatchdogPolicy {
    fun noGameTraffic(
        gameLaunchedAtEpochMs: Long?,
        nowEpochMs: Long,
        trafficVerified: Boolean,
    ): Boolean {
        if (trafficVerified || gameLaunchedAtEpochMs == null) return false

        val ageMs = (nowEpochMs - gameLaunchedAtEpochMs).coerceAtLeast(0L)
        return ageMs in GRACE_MS..TRACK_WINDOW_MS
    }

    const val GRACE_MS = 20_000L
    const val TRACK_WINDOW_MS = 10L * 60L * 1000L
}
