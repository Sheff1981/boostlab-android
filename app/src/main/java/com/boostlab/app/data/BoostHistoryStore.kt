package com.boostlab.app.data

import android.content.Context

data class BoostHistory(
    val sessionCount: Int = 0,
    val totalBoostSeconds: Long = 0,
    val lastBoostSeconds: Long = 0,
    val lastPingMs: Int? = null,
    val lastJitterMs: Int? = null,
    val lastPacketLossPct: Double? = null,
    val lastRouteGainMs: Int? = null,
    val lastGameTrafficVerified: Boolean = false,
    val lastRouteHealth: String? = null,
    val lastRouteRecommendation: String? = null,
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
        lastRouteGainMs = preferences.getInt(KEY_LAST_ROUTE_GAIN, Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE },
        lastGameTrafficVerified = preferences.getBoolean(KEY_LAST_TRAFFIC_VERIFIED, false),
        lastRouteHealth = preferences.getString(KEY_LAST_ROUTE_HEALTH, null),
        lastRouteRecommendation = preferences.getString(KEY_LAST_ROUTE_RECOMMENDATION, null),
    )

    fun record(
        durationSeconds: Long,
        pingMs: Int?,
        jitterMs: Int?,
        packetLossPct: Double?,
        routeGainMs: Int?,
        gameTrafficVerified: Boolean,
        routeHealth: String?,
        routeRecommendation: String?,
    ): BoostHistory {
        val current = load()
        val updated = current.copy(
            sessionCount = current.sessionCount + 1,
            totalBoostSeconds = current.totalBoostSeconds + durationSeconds.coerceAtLeast(0),
            lastBoostSeconds = durationSeconds.coerceAtLeast(0),
            lastPingMs = pingMs,
            lastJitterMs = jitterMs,
            lastPacketLossPct = packetLossPct,
            lastRouteGainMs = routeGainMs,
            lastGameTrafficVerified = gameTrafficVerified,
            lastRouteHealth = routeHealth,
            lastRouteRecommendation = routeRecommendation,
        )
        preferences.edit()
            .putInt(KEY_SESSIONS, updated.sessionCount)
            .putLong(KEY_TOTAL_SECONDS, updated.totalBoostSeconds)
            .putLong(KEY_LAST_SECONDS, updated.lastBoostSeconds)
            .putInt(KEY_LAST_PING, updated.lastPingMs ?: -1)
            .putInt(KEY_LAST_JITTER, updated.lastJitterMs ?: -1)
            .putString(KEY_LAST_LOSS, updated.lastPacketLossPct?.toString())
            .putInt(KEY_LAST_ROUTE_GAIN, updated.lastRouteGainMs ?: Int.MIN_VALUE)
            .putBoolean(KEY_LAST_TRAFFIC_VERIFIED, updated.lastGameTrafficVerified)
            .putString(KEY_LAST_ROUTE_HEALTH, updated.lastRouteHealth)
            .putString(KEY_LAST_ROUTE_RECOMMENDATION, updated.lastRouteRecommendation)
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
        private const val KEY_LAST_ROUTE_GAIN = "last_route_gain"
        private const val KEY_LAST_TRAFFIC_VERIFIED = "last_traffic_verified"
        private const val KEY_LAST_ROUTE_HEALTH = "last_route_health"
        private const val KEY_LAST_ROUTE_RECOMMENDATION = "last_route_recommendation"
    }
}
