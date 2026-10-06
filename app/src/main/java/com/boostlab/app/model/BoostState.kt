package com.boostlab.app.model

data class BoostState(
    val selectedApp: BoostApp? = null,
    val showAdvancedSettings: Boolean = false,

    val isGameLaunching: Boolean = false,
    val gameLaunchError: String? = null,
    val gameBoostMessage: String = "Готов к запуску",
    val availableMemoryMb: Long? = null,
    val totalMemoryMb: Long? = null,
    val availableMemoryPercent: Int? = null,
    val deviceLowMemory: Boolean? = null,
    val lowRamDevice: Boolean? = null,
    val powerSaveMode: Boolean? = null,
    val thermalStatus: Int? = null,
    val networkValidated: Boolean? = null,
    val networkTransport: String? = null,
    val gameLaunchMode: GameLaunchMode = GameLaunchMode.SMART,
    val pinnedPackages: Set<String> = emptySet(),
    val autoLaunchAfterNetworkBoost: Boolean = false,
    val confirmStop: Boolean = true,
    val debugLogging: Boolean = false,
    val diagnosticLogEntries: List<String> = emptyList(),
    val localUserId: String = "",
    val squadCode: String? = null,
    val friends: List<String> = emptyList(),

    // Legacy name kept for compatibility: this flag now means VPN/network boost is active.
    val isBoosting: Boolean = false,
    val isTunnelConnecting: Boolean = false,
    val tunnelError: String? = null,

    val clientPublicKey: String? = null,
    val identityError: String? = null,

    val wireGuardServerPublicKey: String = "",
    val wireGuardPort: Int = 51820,
    val tunnelAddress: String = "10.77.0.2/32",
    val dnsServer: String = "1.1.1.1",

    val controlPlaneUrl: String = "",
    val isAutoSelecting: Boolean = false,
    val isLanDiscovering: Boolean = false,
    val lanGatewayCount: Int = 0,
    val discoveredNodes: Int = 0,
    val selectedGatewayId: String? = null,
    val selectedGatewayRegion: String? = null,

    val gatewayHost: String = "",
    val gatewayPort: Int = 51821,
    val isProbing: Boolean = false,
    val probeError: String? = null,
    val serverLabel: String = "Сервер ещё не задан",

    val pingMs: Int? = null,
    val jitterMs: Int? = null,
    val packetLossPct: Double? = null,
)
