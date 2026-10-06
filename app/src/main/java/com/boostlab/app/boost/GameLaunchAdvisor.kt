package com.boostlab.app.boost

object GameLaunchAdvisor {
    fun message(readiness: GameReadinessSnapshot?): String = when {
        readiness == null -> "Игра запущена · диагностика недоступна"
        readiness.thermalStatus != null && readiness.thermalStatus >= 4 ->
            "Игра запущена · сильный нагрев"
        readiness.lowMemory || readiness.availableMemoryPercent < 10 ->
            "Игра запущена · мало свободной RAM"
        readiness.powerSaveMode == true ->
            "Игра запущена · энергосбережение включено"
        readiness.networkValidated == false ->
            "Игра запущена · проверь соединение"
        else ->
            "Игра запущена · система готова"
    }
}
