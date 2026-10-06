package com.boostlab.app.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun append(title: String, message: String) {
        val stamp = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date())
        val line = "$stamp · $title · $message"
        val updated = (load() + line).takeLast(MAX_EVENTS)
        preferences.edit().putString(KEY_EVENTS, updated.joinToString(SEPARATOR)).apply()
    }

    fun load(): List<String> {
        val raw = preferences.getString(KEY_EVENTS, "").orEmpty()
        return if (raw.isBlank()) emptyList() else raw.split(SEPARATOR)
    }

    fun clear() {
        preferences.edit().remove(KEY_EVENTS).apply()
    }

    companion object {
        private const val PREFS_NAME = "boostlab_events"
        private const val KEY_EVENTS = "events"
        private const val SEPARATOR = "\u001E"
        private const val MAX_EVENTS = 50
    }
}
