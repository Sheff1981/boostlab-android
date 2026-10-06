package com.boostlab.app.data

import android.content.Context

data class UserSettings(
    val autoLaunchAfterNetworkBoost: Boolean = false,
    val confirmStop: Boolean = true,
    val debugLogging: Boolean = false,
    val showPing: Boolean = true,
    val autoSelectBestNode: Boolean = true,
    val preferredRegion: String = "AUTO",
    val boostMode: String = "SMART",
    val customDnsEnabled: Boolean = false,
    val customDnsServers: List<String> = emptyList(),
)

class UserSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): UserSettings = UserSettings(
        autoLaunchAfterNetworkBoost = preferences.getBoolean(KEY_AUTO_LAUNCH, false),
        confirmStop = preferences.getBoolean(KEY_CONFIRM_STOP, true),
        debugLogging = preferences.getBoolean(KEY_DEBUG_LOG, false),
        showPing = preferences.getBoolean(KEY_SHOW_PING, true),
        autoSelectBestNode = preferences.getBoolean(KEY_AUTO_NODE, true),
        preferredRegion = preferences.getString(KEY_REGION, "AUTO").orEmpty().ifBlank { "AUTO" },
        boostMode = preferences.getString(KEY_BOOST_MODE, "SMART").orEmpty().ifBlank { "SMART" },
        customDnsEnabled = preferences.getBoolean(KEY_CUSTOM_DNS_ENABLED, false),
        customDnsServers = preferences.getString(KEY_CUSTOM_DNS, "")
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(4),
    )

    fun save(settings: UserSettings) {
        preferences.edit()
            .putBoolean(KEY_AUTO_LAUNCH, settings.autoLaunchAfterNetworkBoost)
            .putBoolean(KEY_CONFIRM_STOP, settings.confirmStop)
            .putBoolean(KEY_DEBUG_LOG, settings.debugLogging)
            .putBoolean(KEY_SHOW_PING, settings.showPing)
            .putBoolean(KEY_AUTO_NODE, settings.autoSelectBestNode)
            .putString(KEY_REGION, settings.preferredRegion)
            .putString(KEY_BOOST_MODE, settings.boostMode)
            .putBoolean(KEY_CUSTOM_DNS_ENABLED, settings.customDnsEnabled)
            .putString(KEY_CUSTOM_DNS, settings.customDnsServers.take(4).joinToString(","))
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "boostlab_user_settings"
        private const val KEY_AUTO_LAUNCH = "auto_launch_after_network_boost"
        private const val KEY_CONFIRM_STOP = "confirm_stop"
        private const val KEY_DEBUG_LOG = "debug_log"
        private const val KEY_SHOW_PING = "show_ping"
        private const val KEY_AUTO_NODE = "auto_select_best_node"
        private const val KEY_REGION = "preferred_region"
        private const val KEY_BOOST_MODE = "boost_mode"
        private const val KEY_CUSTOM_DNS_ENABLED = "custom_dns_enabled"
        private const val KEY_CUSTOM_DNS = "custom_dns_servers"
    }
}
