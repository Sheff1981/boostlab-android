package com.boostlab.app.data

import android.content.Context

class PinnedAppsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): Set<String> = preferences.getStringSet(KEY_PACKAGES, emptySet())?.toSet().orEmpty()

    fun save(packages: Set<String>) {
        preferences.edit().putStringSet(KEY_PACKAGES, packages.toSet()).apply()
    }

    companion object {
        private const val PREFS_NAME = "boostlab_pinned_apps"
        private const val KEY_PACKAGES = "packages"
    }
}
