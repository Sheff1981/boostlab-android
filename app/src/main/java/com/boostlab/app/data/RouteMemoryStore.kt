package com.boostlab.app.data

import android.content.Context

data class RouteMemory(
    val gatewayId: String,
    val updatedAtEpochMs: Long,
    val gainMs: Int?,
)

class RouteMemoryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(
        packageName: String,
        boostMode: String,
        networkTransport: String?,
    ): RouteMemory? {
        val prefix = keyPrefix(packageName, boostMode, networkTransport)
        val gatewayId = preferences.getString("$prefix:gateway", null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val updatedAt = preferences.getLong("$prefix:updated", 0L)
        if (updatedAt <= 0L) return null

        val ageMs = System.currentTimeMillis() - updatedAt
        if (ageMs < 0L || ageMs > MAX_AGE_MS) {
            clear(packageName, boostMode, networkTransport)
            return null
        }

        val gain = preferences.getInt("$prefix:gain", Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE }

        return RouteMemory(
            gatewayId = gatewayId,
            updatedAtEpochMs = updatedAt,
            gainMs = gain,
        )
    }

    fun save(
        packageName: String,
        boostMode: String,
        networkTransport: String?,
        gatewayId: String,
        gainMs: Int?,
    ) {
        if (packageName.isBlank() || gatewayId.isBlank()) return
        val prefix = keyPrefix(packageName, boostMode, networkTransport)
        preferences.edit()
            .putString("$prefix:gateway", gatewayId)
            .putLong("$prefix:updated", System.currentTimeMillis())
            .putInt("$prefix:gain", gainMs ?: Int.MIN_VALUE)
            .apply()
    }

    fun clear(
        packageName: String,
        boostMode: String,
        networkTransport: String?,
    ) {
        val prefix = keyPrefix(packageName, boostMode, networkTransport)
        preferences.edit()
            .remove("$prefix:gateway")
            .remove("$prefix:updated")
            .remove("$prefix:gain")
            .apply()
    }

    private fun keyPrefix(
        packageName: String,
        boostMode: String,
        networkTransport: String?,
    ): String {
        val transport = networkTransport
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.isNotBlank() }
            ?: "UNKNOWN"
        return "route:$packageName:${boostMode.trim().uppercase()}:$transport"
    }

    companion object {
        private const val PREFS_NAME = "boostlab_route_memory"
        private const val MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
