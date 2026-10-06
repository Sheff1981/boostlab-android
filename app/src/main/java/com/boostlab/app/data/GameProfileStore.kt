package com.boostlab.app.data

import android.content.Context
import com.boostlab.app.model.GameLaunchMode

class GameProfileStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(packageName: String): GameLaunchMode {
        val stored = preferences.getString(keyFor(packageName), null)
        return GameLaunchMode.entries.firstOrNull { it.name == stored }
            ?: GameLaunchMode.SMART
    }

    fun save(packageName: String, mode: GameLaunchMode) {
        preferences.edit()
            .putString(keyFor(packageName), mode.name)
            .apply()
    }

    private fun keyFor(packageName: String): String = "launch_mode:$packageName"

    companion object {
        private const val PREFS_NAME = "boostlab_game_profiles"
    }
}
