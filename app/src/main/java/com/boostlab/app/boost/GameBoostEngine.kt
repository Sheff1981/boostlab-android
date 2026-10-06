package com.boostlab.app.boost

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager

data class GameReadinessSnapshot(
    val availableMemoryMb: Long,
    val lowMemory: Boolean,
    val powerSaveMode: Boolean,
    val thermalStatus: Int?,
)

class GameBoostEngine(
    private val context: Context,
) {
    fun inspect(): GameReadinessSnapshot {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val powerManager = context.getSystemService(PowerManager::class.java)
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager.currentThermalStatus
        } else {
            null
        }

        return GameReadinessSnapshot(
            availableMemoryMb = memoryInfo.availMem / BYTES_PER_MEGABYTE,
            lowMemory = memoryInfo.lowMemory,
            powerSaveMode = powerManager.isPowerSaveMode,
            thermalStatus = thermalStatus,
        )
    }

    fun launch(packageName: String): Result<Unit> = runCatching {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: error("У игры нет доступного экрана запуска")

        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
        )

        context.startActivity(launchIntent)
    }

    companion object {
        private const val BYTES_PER_MEGABYTE = 1024L * 1024L
    }
}
