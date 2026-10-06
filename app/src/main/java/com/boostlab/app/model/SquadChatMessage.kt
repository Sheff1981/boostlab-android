package com.boostlab.app.model

data class SquadChatMessage(
    val id: Long,
    val sender: String,
    val text: String,
    val createdAt: String,
)
