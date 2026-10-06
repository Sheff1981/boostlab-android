package com.boostlab.app.data

import android.content.Context

data class UserSettings(
    val autoLaunchAfterNetworkBoost: Boolean = false,
    val confirmStop: Boolean = true,
    val debugLogging: Boolean = false,
)

class UserSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): UserSettings = UserSettings(
        autoLaunchAfterNetworkBoost = preferences.getBoolean(KEY_AUTO_LAUNCH, false),
        confirmStop = preferences.getBoolean(KEY_CONFIRM_STOP, true),
        debugLogging = preferences.getBoolean(KEY_DEBUG_LOG, false),
    )

    fun save(settings: UserSettings) {
        preferences.edit()
            .putBoolean(KEY_AUTO_LAUNCH, settings.autoLaunchAfterNetworkBoost)
            .putBoolean(KEY_CONFIRM_STOP, settings.confirmStop)
            .putBoolean(KEY_DEBUG_LOG, settings.debugLogging)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "boostlab_user_settings"
        private const val KEY_AUTO_LAUNCH = "auto_launch_after_network_boost"
        private const val KEY_CONFIRM_STOP = "confirm_stop"
        private const val KEY_DEBUG_LOG = "debug_log"
    }
}
