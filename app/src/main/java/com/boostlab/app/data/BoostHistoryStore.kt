package com.boostlab.app.data

import android.content.Context

data class BoostHistory(
    val sessionCount: Int = 0,
    val totalBoostSeconds: Long = 0,
    val lastBoostSeconds: Long = 0,
    val lastPingMs: Int? = null,
    val lastJitterMs: Int? = null,
    val lastPacketLossPct: Double? = null,
)

class BoostHistoryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): BoostHistory = BoostHistory(
        sessionCount = preferences.getInt(KEY_SESSIONS, 0),
        totalBoostSeconds = preferences.getLong(KEY_TOTAL_SECONDS, 0L),
        lastBoostSeconds = preferences.getLong(KEY_LAST_SECONDS, 0L),
        lastPingMs = preferences.getInt(KEY_LAST_PING, -1).takeIf { it >= 0 },
        lastJitterMs = preferences.getInt(KEY_LAST_JITTER, -1).takeIf { it >= 0 },
        lastPacketLossPct = preferences.getString(KEY_LAST_LOSS, null)?.toDoubleOrNull(),
    )

    fun record(
        durationSeconds: Long,
        pingMs: Int?,
        jitterMs: Int?,
        packetLossPct: Double?,
    ): BoostHistory {
        val current = load()
        val updated = current.copy(
            sessionCount = current.sessionCount + 1,
            totalBoostSeconds = current.totalBoostSeconds + durationSeconds.coerceAtLeast(0),
            lastBoostSeconds = durationSeconds.coerceAtLeast(0),
            lastPingMs = pingMs,
            lastJitterMs = jitterMs,
            lastPacketLossPct = packetLossPct,
        )
        preferences.edit()
            .putInt(KEY_SESSIONS, updated.sessionCount)
            .putLong(KEY_TOTAL_SECONDS, updated.totalBoostSeconds)
            .putLong(KEY_LAST_SECONDS, updated.lastBoostSeconds)
            .putInt(KEY_LAST_PING, updated.lastPingMs ?: -1)
            .putInt(KEY_LAST_JITTER, updated.lastJitterMs ?: -1)
            .putString(KEY_LAST_LOSS, updated.lastPacketLossPct?.toString())
            .apply()
        return updated
    }

    companion object {
        private const val PREFS_NAME = "boostlab_boost_history"
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_TOTAL_SECONDS = "total_seconds"
        private const val KEY_LAST_SECONDS = "last_seconds"
        private const val KEY_LAST_PING = "last_ping"
        private const val KEY_LAST_JITTER = "last_jitter"
        private const val KEY_LAST_LOSS = "last_loss"
    }
}
