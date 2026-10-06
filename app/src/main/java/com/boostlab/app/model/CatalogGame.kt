package com.boostlab.app.model

enum class GameCatalogTag {
    HOT,
    NEW,
}

data class CatalogGame(
    val title: String,
    val tags: Set<GameCatalogTag> = emptySet(),
)
