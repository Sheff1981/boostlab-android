package com.boostlab.app.boost

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveProbeCadencePolicyTest {
    @Test
    fun normalModeUsesFullMonitoringBudget() {
        val budget = LiveProbeCadencePolicy.choose(
            powerSaveMode = false,
            thermalStatus = 0,
        )

        assertEquals("NORMAL", budget.label)
        assertEquals(5_000L, budget.intervalMs)
        assertEquals(4, budget.accessSamples)
        assertEquals(3, budget.directSamples)
        assertEquals(2, budget.routeRefreshCycles)
    }

    @Test
    fun powerSaveReducesMonitoringLoad() {
        val budget = LiveProbeCadencePolicy.choose(
            powerSaveMode = true,
            thermalStatus = 1,
        )

        assertEquals("POWER_SAVE", budget.label)
        assertEquals(10_000L, budget.intervalMs)
        assertEquals(3, budget.accessSamples)
        assertEquals(2, budget.directSamples)
        assertEquals(3, budget.routeRefreshCycles)
    }

    @Test
    fun severeThermalStatusOverridesPowerSave() {
        val budget = LiveProbeCadencePolicy.choose(
            powerSaveMode = true,
            thermalStatus = 3,
        )

        assertEquals("THERMAL_SAFE", budget.label)
        assertEquals(15_000L, budget.intervalMs)
        assertEquals(2, budget.accessSamples)
        assertEquals(4, budget.routeRefreshCycles)
    }

    @Test
    fun criticalThermalStatusUsesMinimumProbeBudget() {
        val budget = LiveProbeCadencePolicy.choose(
            powerSaveMode = false,
            thermalStatus = 4,
        )

        assertEquals("THERMAL_CRITICAL", budget.label)
        assertEquals(30_000L, budget.intervalMs)
        assertEquals(1, budget.accessSamples)
        assertEquals(1, budget.directSamples)
        assertEquals(6, budget.routeRefreshCycles)
    }
}
