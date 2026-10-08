package com.boostlab.app.network

object WarmRoutePolicy {
    fun canFastVerify(
        updatedAtEpochMs: Long,
        previousGainMs: Int?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val ageMs = nowEpochMs - updatedAtEpochMs
        return ageMs in 0L..MAX_WARM_AGE_MS &&
            previousGainMs != null &&
            previousGainMs >= MIN_PREVIOUS_GAIN_MS
    }

    const val MIN_PREVIOUS_GAIN_MS = 6
    const val MAX_WARM_AGE_MS = 30L * 60L * 1000L
}
