package com.boostlab.app.data

import com.boostlab.app.model.BoostApp

object InstalledAppOrdering {
    fun visibleApps(
        apps: List<BoostApp>,
        pinnedPackages: Set<String>,
        query: String,
    ): List<BoostApp> {
        val normalizedQuery = query.trim()

        return apps
            .asSequence()
            .filter { app ->
                if (normalizedQuery.isBlank()) {
                    app.isGame || app.packageName in pinnedPackages
                } else {
                    app.label.contains(normalizedQuery, ignoreCase = true) ||
                        app.packageName.contains(normalizedQuery, ignoreCase = true)
                }
            }
            .distinctBy { it.packageName }
            .sortedWith(
                compareByDescending<BoostApp> { it.packageName in pinnedPackages }
                    .thenBy { it.label.lowercase() },
            )
            .toList()
    }
}
