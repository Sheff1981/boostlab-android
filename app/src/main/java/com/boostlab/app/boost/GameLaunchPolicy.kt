package com.boostlab.app.boost

import com.boostlab.app.model.GameLaunchMode

object GameLaunchPolicy {
    fun blockReason(
        mode: GameLaunchMode,
        readiness: GameReadinessSnapshot?,
    ): String? {
        if (mode == GameLaunchMode.ALWAYS || readiness == null) return null

        if ((readiness.thermalStatus ?: 0) >= SEVERE_THERMAL_STATUS) {
            return "Телефон сильно нагрет. Остуди его и повтори запуск или выбери профиль «ВСЕГДА»."
        }

        if (mode == GameLaunchMode.ONLINE && readiness.networkValidated == false) {
            return "Нет подтверждённого интернета. Проверь Wi-Fi или мобильную сеть."
        }

        return null
    }

    private const val SEVERE_THERMAL_STATUS = 4
}
