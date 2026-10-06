package com.boostlab.app.data

import com.boostlab.app.model.GameCatalogTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameCatalogRepositoryTest {
    @Test
    fun catalogHasUniqueTitles() {
        val titles = GameCatalogRepository.games.map { it.title.lowercase() }
        assertEquals(titles.size, titles.distinct().size)
    }

    @Test
    fun catalogContainsHotAndNewGames() {
        assertTrue(GameCatalogRepository.games.any { GameCatalogTag.HOT in it.tags })
        assertTrue(GameCatalogRepository.games.any { GameCatalogTag.NEW in it.tags })
    }

    @Test
    fun catalogIsLargeEnoughForFirstLayer() {
        assertTrue(GameCatalogRepository.games.size >= 60)
    }
}
