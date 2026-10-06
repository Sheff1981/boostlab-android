package com.boostlab.app.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosticLogStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun append(message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val updated = (load() + "[$timestamp] $message").takeLast(MAX_ENTRIES)
        preferences.edit().putString(KEY_LOG, updated.joinToString(SEPARATOR)).apply()
    }

    fun load(): List<String> {
        val raw = preferences.getString(KEY_LOG, "").orEmpty()
        return if (raw.isBlank()) emptyList() else raw.split(SEPARATOR)
    }

    fun clear() {
        preferences.edit().remove(KEY_LOG).apply()
    }

    fun exportText(): String = buildString {
        val entries = load()
        appendLine("BOOSTLAB diagnostic log")
        appendLine("Entries: ${entries.size}")
        appendLine()
        if (entries.isEmpty()) {
            appendLine("No diagnostic entries.")
        } else {
            entries.forEach { appendLine(it) }
        }
    }

    companion object {
        private const val PREFS_NAME = "boostlab_diagnostic_log"
        private const val KEY_LOG = "entries"
        private const val SEPARATOR = "\u001E"
        private const val MAX_ENTRIES = 200
    }
}
