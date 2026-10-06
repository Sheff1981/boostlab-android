package com.boostlab.app.model

enum class GameLaunchMode(
    val title: String,
    val description: String,
) {
    SMART(
        title = "УМНЫЙ",
        description = "Защита от сильного перегрева",
    ),
    ONLINE(
        title = "ОНЛАЙН",
        description = "Перегрев + проверка интернета",
    ),
    ALWAYS(
        title = "ВСЕГДА",
        description = "Запуск без блокировок",
    );

    fun next(): GameLaunchMode = when (this) {
        SMART -> ONLINE
        ONLINE -> ALWAYS
        ALWAYS -> SMART
    }
}
