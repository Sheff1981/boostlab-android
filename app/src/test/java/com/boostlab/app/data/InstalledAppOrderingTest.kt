package com.boostlab.app.data

import com.boostlab.app.model.BoostApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledAppOrderingTest {
    private val apps = listOf(
        BoostApp(label = "PUBG Mobile", packageName = "com.pubg", icon = null, isGame = true),
        BoostApp(label = "Brawl Stars", packageName = "com.brawl", icon = null, isGame = true),
        BoostApp(label = "AndroidIDE", packageName = "com.itsaky.androidide", icon = null, isGame = false),
    )

    @Test
    fun defaultListShowsDetectedGamesAndPinnedManualApps() {
        val result = InstalledAppOrdering.visibleApps(
            apps = apps,
            pinnedPackages = setOf("com.pubg"),
            query = "",
        )

        assertEquals(2, result.size)
        assertEquals("com.pubg", result.first().packageName)
        assertFalse(result.any { it.packageName == "com.itsaky.androidide" })
    }

    @Test
    fun manuallyPinnedNonGameRemainsAvailable() {
        val result = InstalledAppOrdering.visibleApps(
            apps = apps,
            pinnedPackages = setOf("com.itsaky.androidide"),
            query = "",
        )

        assertTrue(result.any { it.packageName == "com.itsaky.androidide" })
    }

    @Test
    fun searchCanFindUnpinnedNonGameForManualAdd() {
        val result = InstalledAppOrdering.visibleApps(
            apps = apps,
            pinnedPackages = emptySet(),
            query = "androidide",
        )

        assertEquals(listOf("com.itsaky.androidide"), result.map { it.packageName })
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
