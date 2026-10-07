package com.boostlab.app.boost

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager

data class GameReadinessSnapshot(
    val availableMemoryMb: Long,
    val totalMemoryMb: Long,
    val availableMemoryPercent: Int,
    val lowMemory: Boolean,
    val lowRamDevice: Boolean,
    val powerSaveMode: Boolean?,
    val thermalStatus: Int?,
    val networkValidated: Boolean?,
    val networkTransport: String?,
    val networkMtu: Int? = null,
    val vpnActive: Boolean = false,
)

class GameBoostEngine(
    private val context: Context,
) {
    fun inspect(): GameReadinessSnapshot {
        val activityManager = context.getSystemService(ActivityManager::class.java)
            ?: error("ActivityManager unavailable")
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val powerManager = context.getSystemService(PowerManager::class.java)
        val thermalStatus = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null
        ) {
            powerManager.currentThermalStatus
        } else {
            null
        }

        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val activeNetwork = connectivityManager?.activeNetwork
        val activeCapabilities = if (connectivityManager != null && activeNetwork != null) {
            connectivityManager.getNetworkCapabilities(activeNetwork)
        } else {
            null
        }

        val physicalNetwork = if (
            connectivityManager != null &&
            activeCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        ) {
            connectivityManager.allNetworks.firstOrNull { candidate ->
                val caps = connectivityManager.getNetworkCapabilities(candidate) ?: return@firstOrNull false
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    (
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                        )
            }
        } else {
            activeNetwork
        }

        val networkCapabilities = if (connectivityManager != null && physicalNetwork != null) {
            connectivityManager.getNetworkCapabilities(physicalNetwork)
        } else {
            activeCapabilities
        }
        val networkMtu = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            connectivityManager != null &&
            physicalNetwork != null
        ) {
            connectivityManager.getLinkProperties(physicalNetwork)
                ?.mtu
                ?.takeIf { it in 1280..9000 }
        } else {
            null
        }

        val availableMemoryMb = memoryInfo.availMem / BYTES_PER_MEGABYTE
        val totalMemoryMb = memoryInfo.totalMem / BYTES_PER_MEGABYTE
        val availableMemoryPercent = if (memoryInfo.totalMem > 0L) {
            ((memoryInfo.availMem * 100L) / memoryInfo.totalMem)
                .toInt()
                .coerceIn(0, 100)
        } else {
            0
        }

        return GameReadinessSnapshot(
            availableMemoryMb = availableMemoryMb,
            totalMemoryMb = totalMemoryMb,
            availableMemoryPercent = availableMemoryPercent,
            lowMemory = memoryInfo.lowMemory,
            lowRamDevice = activityManager.isLowRamDevice,
            powerSaveMode = powerManager?.isPowerSaveMode,
            thermalStatus = thermalStatus,
            networkValidated = networkCapabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED,
            ),
            networkTransport = networkTransportLabel(networkCapabilities),
            networkMtu = networkMtu,
            vpnActive = activeCapabilities?.hasTransport(
                NetworkCapabilities.TRANSPORT_VPN,
            ) == true,
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

    private fun networkTransportLabel(capabilities: NetworkCapabilities?): String? = when {
        capabilities == null -> null
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> "Other"
    }

    companion object {
        private const val BYTES_PER_MEGABYTE = 1024L * 1024L
    }
}
