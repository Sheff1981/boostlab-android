package com.boostlab.app.data

import android.content.Context
import java.util.UUID

data class SocialProfile(
    val userId: String,
    val squadCode: String?,
    val friends: List<String>,
)

class SocialStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): SocialProfile {
        val userId = preferences.getString(KEY_USER_ID, null) ?: newCode("BL").also {
            preferences.edit().putString(KEY_USER_ID, it).apply()
        }
        val friends = preferences.getStringSet(KEY_FRIENDS, emptySet()).orEmpty().toList().sorted()
        return SocialProfile(
            userId = userId,
            squadCode = preferences.getString(KEY_SQUAD, null),
            friends = friends,
        )
    }

    fun createSquad(): SocialProfile {
        preferences.edit().putString(KEY_SQUAD, newCode("SQ")).apply()
        return load()
    }

    fun joinSquad(code: String): SocialProfile {
        preferences.edit().putString(KEY_SQUAD, code).apply()
        return load()
    }

    fun leaveSquad(): SocialProfile {
        preferences.edit().remove(KEY_SQUAD).apply()
        return load()
    }

    fun addFriend(alias: String): SocialProfile {
        val updated = (load().friends + alias)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        preferences.edit().putStringSet(KEY_FRIENDS, updated).apply()
        return load()
    }

    private fun newCode(prefix: String): String =
        prefix + "-" + UUID.randomUUID().toString().replace("-", "").take(6).uppercase()

    companion object {
        private const val PREFS_NAME = "boostlab_social"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_SQUAD = "squad"
        private const val KEY_FRIENDS = "friends"
    }
}
