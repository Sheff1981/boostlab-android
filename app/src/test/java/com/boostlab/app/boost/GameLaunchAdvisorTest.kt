package com.boostlab.app.boost

import org.junit.Assert.assertEquals
import org.junit.Test

class GameLaunchAdvisorTest {
    @Test
    fun `diagnostics failure never blocks successful launch message`() {
        assertEquals(
            "Игра запущена · диагностика недоступна",
            GameLaunchAdvisor.message(null),
        )
    }

    @Test
    fun `thermal warning has highest priority`() {
        assertEquals(
            "Игра запущена · сильный нагрев",
            GameLaunchAdvisor.message(snapshot(thermalStatus = 4, lowMemory = true)),
        )
    }

    @Test
    fun `low memory warning is reported`() {
        assertEquals(
            "Игра запущена · мало свободной RAM",
            GameLaunchAdvisor.message(snapshot(availableMemoryPercent = 9)),
        )
    }

    @Test
    fun `power saver warning is reported`() {
        assertEquals(
            "Игра запущена · энергосбережение включено",
            GameLaunchAdvisor.message(snapshot(powerSaveMode = true)),
        )
    }

    @Test
    fun `network warning is reported`() {
        assertEquals(
            "Игра запущена · проверь соединение",
            GameLaunchAdvisor.message(snapshot(networkValidated = false)),
        )
    }

    @Test
    fun `healthy device reports ready`() {
        assertEquals(
            "Игра запущена · система готова",
            GameLaunchAdvisor.message(snapshot()),
        )
    }

    private fun snapshot(
        availableMemoryPercent: Int = 50,
        lowMemory: Boolean = false,
        powerSaveMode: Boolean? = false,
        thermalStatus: Int? = 0,
        networkValidated: Boolean? = true,
    ) = GameReadinessSnapshot(
        availableMemoryMb = 2048,
        totalMemoryMb = 4096,
        availableMemoryPercent = availableMemoryPercent,
        lowMemory = lowMemory,
        lowRamDevice = false,
        powerSaveMode = powerSaveMode,
        thermalStatus = thermalStatus,
        networkValidated = networkValidated,
        networkTransport = "Wi-Fi",
    )
}
