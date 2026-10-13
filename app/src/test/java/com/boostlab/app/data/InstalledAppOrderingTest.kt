package com.boostlab.app.data

import com.boostlab.app.model.BoostApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledAppOrderingTest {
    private val apps = listOf(
        BoostApp(label = "PUBG Mobile", packageName = "com.pubg", icon = null),
        BoostApp(label = "Brawl Stars", packageName = "com.brawl", icon = null),
        BoostApp(label = "Unknown Game", packageName = "com.example.unknown", icon = null),
    )

    @Test
    fun pinnedAppsComeFirstButDoNotHideOtherInstalledApps() {
        val result = InstalledAppOrdering.visibleApps(
            apps = apps,
            pinnedPackages = setOf("com.pubg"),
            query = "",
        )

        assertEquals(3, result.size)
        assertEquals("com.pubg", result.first().packageName)
        assertTrue(result.any { it.packageName == "com.example.unknown" })
    }

    @Test
    fun searchStillFindsUnpinnedApps() {
        val result = InstalledAppOrdering.visibleApps(
            apps = apps,
            pinnedPackages = setOf("com.pubg"),
            query = "unknown",
        )

        assertEquals(listOf("com.example.unknown"), result.map { it.packageName })
    }

    @Test
    fun packageNameCanBeUsedForSearch() {
        val result = InstalledAppOrdering.visibleApps(
            apps = apps,
            pinnedPackages = emptySet(),
            query = "com.brawl",
        )

        assertEquals(listOf("com.brawl"), result.map { it.packageName })
    }
}
