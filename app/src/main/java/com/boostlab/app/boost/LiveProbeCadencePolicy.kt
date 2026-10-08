package com.boostlab.app.boost

data class LiveProbeBudget(
    val label: String,
    val intervalMs: Long,
    val accessSamples: Int,
    val directSamples: Int,
    val routeRefreshCycles: Int,
)

object LiveProbeCadencePolicy {
    fun choose(
        powerSaveMode: Boolean?,
        thermalStatus: Int?,
    ): LiveProbeBudget {
        return when {
            thermalStatus != null && thermalStatus >= THERMAL_CRITICAL -> LiveProbeBudget(
                label = "THERMAL_CRITICAL",
                intervalMs = 30_000L,
                accessSamples = 1,
                directSamples = 1,
                routeRefreshCycles = 6,
            )
            thermalStatus != null && thermalStatus >= THERMAL_SEVERE -> LiveProbeBudget(
                label = "THERMAL_SAFE",
                intervalMs = 15_000L,
                accessSamples = 2,
                directSamples = 2,
                routeRefreshCycles = 4,
            )
            powerSaveMode == true -> LiveProbeBudget(
                label = "POWER_SAVE",
                intervalMs = 10_000L,
                accessSamples = 3,
                directSamples = 2,
                routeRefreshCycles = 3,
            )
            else -> LiveProbeBudget(
                label = "NORMAL",
                intervalMs = 5_000L,
                accessSamples = 4,
                directSamples = 3,
                routeRefreshCycles = 2,
            )
        }
    }

    // Android PowerManager: SEVERE=3, CRITICAL=4.
    private const val THERMAL_SEVERE = 3
    private const val THERMAL_CRITICAL = 4
}
