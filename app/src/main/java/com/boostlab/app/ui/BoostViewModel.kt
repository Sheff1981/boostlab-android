package com.boostlab.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boostlab.app.data.BoostHistoryStore
import com.boostlab.app.data.DiagnosticLogStore
import com.boostlab.app.data.GameCatalogRepository
import com.boostlab.app.data.GameProfileStore
import com.boostlab.app.data.InstalledAppsRepository
import com.boostlab.app.data.PinnedAppsStore
import com.boostlab.app.data.PrivateProfileStore
import com.boostlab.app.data.SocialStore
import com.boostlab.app.data.UserSettingsStore
import com.boostlab.app.data.PrivateServerProfile
import com.boostlab.app.boost.GameBoostEngine
import com.boostlab.app.boost.GameLaunchAdvisor
import com.boostlab.app.boost.GameLaunchPolicy
import com.boostlab.app.model.BoostApp
import com.boostlab.app.model.BoostState
import com.boostlab.app.model.GameLaunchMode
import com.boostlab.app.network.ControlPlaneClient
import com.boostlab.app.network.GatewayMeasurement
import com.boostlab.app.network.GatewayNode
import com.boostlab.app.network.LanGatewayDiscovery
import com.boostlab.app.network.RouteDecisionPolicy
import com.boostlab.app.network.RouteScorer
import com.boostlab.app.network.UdpRouteProbe
import com.boostlab.app.tunnel.ClientIdentityStore
import com.boostlab.app.tunnel.TunnelProfile
import com.boostlab.app.tunnel.WireGuardTunnelController
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BoostViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = InstalledAppsRepository(appContext)
    private val profileStore = PrivateProfileStore(appContext)
    private val gameProfileStore = GameProfileStore(appContext)
    private val pinnedAppsStore = PinnedAppsStore(appContext)
    private val userSettingsStore = UserSettingsStore(appContext)
    private val diagnosticLogStore = DiagnosticLogStore(appContext)
    private val boostHistoryStore = BoostHistoryStore(appContext)
    private val socialStore = SocialStore(appContext)
    private val routeProbe = UdpRouteProbe()
    private val lanDiscovery = LanGatewayDiscovery()
    private val controlPlane = ControlPlaneClient()
    private val identityStore = ClientIdentityStore(appContext)
    private val tunnelController = WireGuardTunnelController(appContext)
    private val gameBoostEngine = GameBoostEngine(appContext)
    private var liveMetricsJob: Job? = null
    private var trafficBaselineRx = 0L
    private var trafficBaselineTx = 0L

    private val _state = MutableStateFlow(BoostState())
    val state: StateFlow<BoostState> = _state.asStateFlow()

    val apps: List<BoostApp> = repository.loadLaunchableApps()
    val catalogGames = GameCatalogRepository.games

    init {
        val saved = profileStore.load()
        val userSettings = userSettingsStore.load()
        val social = socialStore.load()
        val history = boostHistoryStore.load()
        val pinned = pinnedAppsStore.load()
        val savedApp = saved.selectedPackage?.let { packageName ->
            apps.firstOrNull { it.packageName == packageName }
        }

        _state.value = _state.value.copy(
            selectedApp = savedApp,
            controlPlaneUrl = saved.controlPlaneUrl,
            gatewayHost = saved.gatewayHost,
            gatewayPort = saved.gatewayPort,
            wireGuardServerPublicKey = saved.wireGuardServerPublicKey,
            wireGuardPort = saved.wireGuardPort,
            tunnelAddress = saved.tunnelAddress,
            dnsServer = saved.dnsServer,
            gameLaunchMode = savedApp?.let { gameProfileStore.load(it.packageName) }
                ?: GameLaunchMode.SMART,
            pinnedPackages = pinned,
            autoLaunchAfterNetworkBoost = userSettings.autoLaunchAfterNetworkBoost,
            confirmStop = userSettings.confirmStop,
            debugLogging = userSettings.debugLogging,
            showPing = userSettings.showPing,
            autoSelectBestNode = userSettings.autoSelectBestNode,
            preferredRegion = userSettings.preferredRegion,
            boostMode = userSettings.boostMode,
            customDnsEnabled = userSettings.customDnsEnabled,
            customDnsServers = userSettings.customDnsServers,
            diagnosticLogEntries = diagnosticLogStore.load(),
            localUserId = social.userId,
            squadCode = social.squadCode,
            friends = social.friends,
            boostSessionCount = history.sessionCount,
            totalBoostSeconds = history.totalBoostSeconds,
            lastBoostSeconds = history.lastBoostSeconds,
            lastBoostPingMs = history.lastPingMs,
            lastBoostJitterMs = history.lastJitterMs,
            lastBoostPacketLossPct = history.lastPacketLossPct,
            serverLabel = if (saved.gatewayHost.isNotBlank()) {
                "Сохранённый сервер: ${saved.gatewayHost}"
            } else {
                "Сервер ещё не настроен"
            },
        )

        runCatching { identityStore.loadOrCreate() }
            .onSuccess { identity ->
                _state.value = _state.value.copy(
                    clientPublicKey = identity.publicKeyBase64,
                    identityError = null,
                )
            }
            .onFailure { error ->
                _state.value = _state.value.copy(
                    clientPublicKey = null,
                    identityError = "Client identity unavailable: ${error::class.java.simpleName}",
                )
            }

        refreshGameReadiness()

        viewModelScope.launch {
            restoreTunnelOrRefreshRoute(saved)
        }
    }

    fun selectApp(app: BoostApp) {
        if (_state.value.isBoosting || _state.value.isTunnelConnecting) return
        val pinned = _state.value.pinnedPackages + app.packageName
        pinnedAppsStore.save(pinned)
        _state.value = _state.value.copy(
            selectedApp = app,
            pinnedPackages = pinned,
            gameLaunchMode = gameProfileStore.load(app.packageName),
            gameLaunchError = null,
            gameBoostMessage = "Готов к запуску",
            tunnelError = null,
        )
        refreshGameReadiness()
        persistProfile()
        debugLog("Выбрано приложение: ${app.label} [${app.packageName}]")
    }

    fun togglePinnedApp(app: BoostApp) {
        if (_state.value.isBoosting || _state.value.isTunnelConnecting) return
        val current = _state.value.pinnedPackages
        val updated = if (app.packageName in current) current - app.packageName else current + app.packageName
        pinnedAppsStore.save(updated)
        _state.value = _state.value.copy(pinnedPackages = updated)
        debugLog(if (app.packageName in updated) "Добавлено в игры: ${app.label}" else "Удалено из игр: ${app.label}")
    }

    fun setAutoLaunchAfterNetworkBoost(enabled: Boolean) {
        val updated = userSettingsStore.load().copy(autoLaunchAfterNetworkBoost = enabled)
        userSettingsStore.save(updated)
        _state.value = _state.value.copy(autoLaunchAfterNetworkBoost = enabled)
        debugLog("Автозапуск после Network Boost: $enabled")
    }

    fun setConfirmStop(enabled: Boolean) {
        val updated = userSettingsStore.load().copy(confirmStop = enabled)
        userSettingsStore.save(updated)
        _state.value = _state.value.copy(confirmStop = enabled)
        debugLog("Подтверждение остановки: $enabled")
    }

    fun setDebugLogging(enabled: Boolean) {
        val updated = userSettingsStore.load().copy(debugLogging = enabled)
        userSettingsStore.save(updated)
        _state.value = _state.value.copy(debugLogging = enabled)
        if (enabled) debugLog("Отладочный лог включён")
    }


    fun setShowPing(enabled: Boolean) {
        val updated = userSettingsStore.load().copy(showPing = enabled)
        userSettingsStore.save(updated)
        _state.value = _state.value.copy(showPing = enabled)
        debugLog("Показ ping: $enabled")
    }

    fun setAutoSelectBestNode(enabled: Boolean) {
        val updated = userSettingsStore.load().copy(autoSelectBestNode = enabled)
        userSettingsStore.save(updated)
        _state.value = _state.value.copy(autoSelectBestNode = enabled)
        debugLog("Автовыбор лучшего узла: $enabled")
    }

    fun setPreferredRegion(region: String) {
        val normalized = region.trim().uppercase().ifBlank { "AUTO" }
        val updated = userSettingsStore.load().copy(preferredRegion = normalized)
        userSettingsStore.save(updated)
        _state.value = _state.value.copy(preferredRegion = normalized)
        debugLog("Регион узла: $normalized")
    }

    fun setBoostMode(mode: String) {
        val normalized = mode.trim().uppercase().let {
            when (it) {
                "LOW_PING", "STABLE" -> it
                else -> "SMART"
            }
        }
        val updated = userSettingsStore.load().copy(boostMode = normalized)
        userSettingsStore.save(updated)
        _state.value = _state.value.copy(boostMode = normalized)
        debugLog("Режим Network Boost: $normalized")
    }

    fun setCustomDnsEnabled(enabled: Boolean) {
        val updated = userSettingsStore.load().copy(customDnsEnabled = enabled)
        userSettingsStore.save(updated)
        _state.value = _state.value.copy(customDnsEnabled = enabled)
        debugLog("Custom DNS: $enabled")
    }

    fun addCustomDns(value: String) {
        val dns = value.trim()
        if (dns.isBlank() || dns.length > 253 || dns.any { it.isWhitespace() }) return
        val current = userSettingsStore.load()
        val servers = (current.customDnsServers + dns).distinct().take(4)
        val updated = current.copy(customDnsServers = servers)
        userSettingsStore.save(updated)
        _state.value = _state.value.copy(customDnsServers = servers)
        debugLog("Добавлен Custom DNS: $dns")
    }

    fun removeCustomDns(value: String) {
        val current = userSettingsStore.load()
        val servers = current.customDnsServers.filterNot { it == value }
        val updated = current.copy(customDnsServers = servers)
        userSettingsStore.save(updated)
        _state.value = _state.value.copy(customDnsServers = servers)
        debugLog("Удалён Custom DNS: $value")
    }

    fun selectDns(dns: String) {
        updateDnsServer(dns)
        debugLog("DNS: $dns")
    }

    fun createSquad() {
        val social = socialStore.createSquad()
        _state.value = _state.value.copy(
            squadCode = social.squadCode,
            localUserId = social.userId,
            friends = social.friends,
        )
        debugLog("Создан локальный отряд: ${social.squadCode}")
    }

    fun joinSquad(code: String) {
        val normalized = code.trim().uppercase()
        if (normalized.length < 4) return
        val social = socialStore.joinSquad(normalized)
        _state.value = _state.value.copy(
            squadCode = social.squadCode,
            localUserId = social.userId,
            friends = social.friends,
        )
        debugLog("Выбран код отряда: $normalized")
    }

    fun leaveSquad() {
        val social = socialStore.leaveSquad()
        _state.value = _state.value.copy(
            squadCode = null,
            localUserId = social.userId,
            friends = social.friends,
        )
        debugLog("Выход из отряда")
    }

    fun addFriend(alias: String) {
        val name = alias.trim()
        if (name.isBlank()) return
        val social = socialStore.addFriend(name)
        _state.value = _state.value.copy(friends = social.friends)
        debugLog("Добавлен друг: $name")
    }

    fun shareSquadInvite() {
        val code = _state.value.squadCode ?: return
        shareText("BOOSTLAB отряд", "Присоединяйся к моему отряду BOOSTLAB. Код: $code")
    }

    fun openStoreSearch(query: String) {
        val encoded = Uri.encode(query)
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$encoded&c=apps"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/search?q=$encoded&c=apps"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(market) }
            .recoverCatching { appContext.startActivity(fallback) }
    }

    fun exportDiagnosticLog() {
        shareText("BOOSTLAB diagnostic log", diagnosticLogStore.exportText())
    }

    fun clearDiagnosticLog() {
        diagnosticLogStore.clear()
        _state.value = _state.value.copy(diagnosticLogEntries = emptyList())
    }

    fun shareApp() {
        shareText("BOOSTLAB", "BOOSTLAB — игровой бустер и Network Boost для Android.")
    }

    fun shareFeedbackReport() {
        val snapshot = _state.value
        val metrics = "ping=${snapshot.pingMs ?: "-"}ms, jitter=${snapshot.jitterMs ?: "-"}ms, loss=${snapshot.packetLossPct ?: "-"}%"
        val game = snapshot.selectedApp?.label ?: "не выбрана"
        val body = buildString {
            appendLine("BOOSTLAB feedback")
            appendLine("Игра: $game")
            appendLine("Режим: ${snapshot.boostMode}")
            appendLine("Регион: ${snapshot.preferredRegion}")
            appendLine("Узел: ${snapshot.selectedGatewayRegion ?: "-"} / ${snapshot.selectedGatewayId ?: "-"}")
            appendLine("Метрики: $metrics")
            appendLine()
            appendLine("Опиши проблему ниже:")
        }
        shareText("BOOSTLAB feedback", body)
    }

    fun cycleGameLaunchMode() {
        val snapshot = _state.value
        val selectedApp = snapshot.selectedApp ?: return
        if (snapshot.isGameLaunching) return

        val nextMode = snapshot.gameLaunchMode.next()
        gameProfileStore.save(selectedApp.packageName, nextMode)
        _state.value = snapshot.copy(
            gameLaunchMode = nextMode,
            gameLaunchError = null,
            gameBoostMessage = "Профиль: ${nextMode.title}",
        )
    }

    fun boostAndLaunchGame() {
        val snapshot = _state.value
        if (snapshot.isGameLaunching) return

        val selectedApp = snapshot.selectedApp
        if (selectedApp == null) {
            _state.value = snapshot.copy(
                gameLaunchError = "Сначала выбери игру",
                gameBoostMessage = "Игра не выбрана",
            )
            return
        }

        val readiness = runCatching { gameBoostEngine.inspect() }.getOrNull()
        _state.value = snapshot.copy(
            isGameLaunching = true,
            gameLaunchError = null,
            gameBoostMessage = "Подготавливаем запуск…",
            availableMemoryMb = readiness?.availableMemoryMb ?: snapshot.availableMemoryMb,
            totalMemoryMb = readiness?.totalMemoryMb ?: snapshot.totalMemoryMb,
            availableMemoryPercent = readiness?.availableMemoryPercent
                ?: snapshot.availableMemoryPercent,
            deviceLowMemory = readiness?.lowMemory ?: snapshot.deviceLowMemory,
            lowRamDevice = readiness?.lowRamDevice ?: snapshot.lowRamDevice,
            powerSaveMode = readiness?.powerSaveMode ?: snapshot.powerSaveMode,
            thermalStatus = readiness?.thermalStatus ?: snapshot.thermalStatus,
            networkValidated = readiness?.networkValidated ?: snapshot.networkValidated,
            networkTransport = readiness?.networkTransport ?: snapshot.networkTransport,
        )

        GameLaunchPolicy.blockReason(snapshot.gameLaunchMode, readiness)?.let { reason ->
            _state.value = _state.value.copy(
                isGameLaunching = false,
                gameLaunchError = reason,
                gameBoostMessage = "Профиль остановил запуск",
            )
            return
        }

        gameBoostEngine.launch(selectedApp.packageName)
            .onSuccess {
                _state.value = _state.value.copy(
                    isGameLaunching = false,
                    gameLaunchError = null,
                    gameBoostMessage = GameLaunchAdvisor.message(readiness),
                )
            }
            .onFailure { error ->
                _state.value = _state.value.copy(
                    isGameLaunching = false,
                    gameLaunchError = error.message ?: "Не удалось запустить игру",
                    gameBoostMessage = "Ошибка запуска",
                )
            }
    }

    fun refreshGameReadiness() {
        runCatching { gameBoostEngine.inspect() }
            .onSuccess { readiness ->
                _state.value = _state.value.copy(
                    availableMemoryMb = readiness.availableMemoryMb,
                    totalMemoryMb = readiness.totalMemoryMb,
                    availableMemoryPercent = readiness.availableMemoryPercent,
                    deviceLowMemory = readiness.lowMemory,
                    lowRamDevice = readiness.lowRamDevice,
                    powerSaveMode = readiness.powerSaveMode,
                    thermalStatus = readiness.thermalStatus,
                    networkValidated = readiness.networkValidated,
                    networkTransport = readiness.networkTransport,
                )
            }
    }

    fun toggleAdvancedSettings() {
        _state.value = _state.value.copy(
            showAdvancedSettings = !_state.value.showAdvancedSettings,
        )
    }

    fun updateControlPlaneUrl(value: String) {
        if (_state.value.isBoosting || _state.value.isTunnelConnecting) return
        _state.value = _state.value.copy(
            controlPlaneUrl = value.trim(),
            probeError = null,
        )
        persistProfile()
    }

    fun updateGatewayHost(value: String) {
        if (_state.value.isBoosting || _state.value.isTunnelConnecting) return
        _state.value = _state.value.copy(
            gatewayHost = value.trim(),
            selectedGatewayId = null,
            selectedGatewayRegion = null,
            probeError = null,
            tunnelError = null,
        )
        persistProfile()
    }

    fun updateWireGuardServerPublicKey(value: String) {
        if (_state.value.isBoosting || _state.value.isTunnelConnecting) return
        _state.value = _state.value.copy(
            wireGuardServerPublicKey = value.trim(),
            tunnelError = null,
        )
        persistProfile()
    }

    fun updateTunnelAddress(value: String) {
        if (_state.value.isBoosting || _state.value.isTunnelConnecting) return
        _state.value = _state.value.copy(
            tunnelAddress = value.trim(),
            tunnelError = null,
        )
        persistProfile()
    }

    fun updateDnsServer(value: String) {
        if (_state.value.isBoosting || _state.value.isTunnelConnecting) return
        _state.value = _state.value.copy(
            dnsServer = value.trim(),
            tunnelError = null,
        )
        persistProfile()
    }

    fun discoverLanGateway() {
        val current = _state.value
        if (
            current.isLanDiscovering ||
            current.isAutoSelecting ||
            current.isProbing ||
            current.isBoosting ||
            current.isTunnelConnecting
        ) return

        _state.value = current.copy(
            isLanDiscovering = true,
            lanGatewayCount = 0,
            probeError = null,
            serverLabel = "Ищем BOOSTLAB в локальной сети…",
        )

        viewModelScope.launch {
            runCatching {
                val hosts = lanDiscovery.discover()
                if (hosts.isEmpty()) {
                    error("BOOSTLAB gateway в локальной сети не найден")
                }

                _state.value = _state.value.copy(
                    lanGatewayCount = hosts.size,
                    serverLabel = "Найдено локальных серверов: ${hosts.size}",
                )

                val measurements = coroutineScope {
                    hosts.map { host ->
                        async {
                            runCatching {
                                val metrics = routeProbe.measure(
                                    host = host,
                                    port = UdpRouteProbe.DEFAULT_PORT,
                                    samples = LAN_AUTO_SAMPLES,
                                )
                                GatewayMeasurement(
                                    node = GatewayNode(
                                        id = "lan-$host",
                                        region = "LAN",
                                        host = host,
                                        udpPort = UdpRouteProbe.DEFAULT_PORT,
                                        wireGuardPublicKey = null,
                                        wireGuardPort = null,
                                        healthy = true,
                                    ),
                                    metrics = metrics,
                                    score = routeScore(metrics, _state.value.boostMode),
                                )
                            }.getOrNull()
                        }
                    }.awaitAll().filterNotNull()
                }

                measurements
                    .filter { it.metrics.received > 0 && it.score.isFinite() }
                    .minByOrNull { it.score }
                    ?: error("Локальный gateway найден, но не отвечает на измерения")
            }.onSuccess { best ->
                _state.value = _state.value.copy(
                    isLanDiscovering = false,
                    gatewayHost = best.node.host,
                    gatewayPort = best.node.udpPort,
                    selectedGatewayId = best.node.id,
                    selectedGatewayRegion = best.node.region,
                    pingMs = best.metrics.medianRttMs,
                    jitterMs = best.metrics.jitterMs,
                    packetLossPct = best.metrics.packetLossPct,
                    serverLabel = "Локальный gateway: ${best.node.host}",
                    probeError = null,
                )
                persistProfile()
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    isLanDiscovering = false,
                    serverLabel = "Локальный сервер не найден",
                    probeError = error.message ?: "Ошибка локального поиска",
                )
            }
        }
    }

    fun autoSelectGateway() {
        val current = _state.value
        if (
            current.controlPlaneUrl.isBlank() ||
            current.isAutoSelecting ||
            current.isBoosting ||
            current.isTunnelConnecting
        ) return

        _state.value = current.copy(
            isAutoSelecting = true,
            discoveredNodes = 0,
            probeError = null,
            serverLabel = "Получаем список серверов…",
        )

        viewModelScope.launch {
            runCatching {
                val fetchedNodes = controlPlane.fetchNodes(_state.value.controlPlaneUrl)
                val preferredRegion = _state.value.preferredRegion
                val regionalNodes = if (preferredRegion == "AUTO") {
                    fetchedNodes
                } else {
                    fetchedNodes.filter { it.region.contains(preferredRegion, ignoreCase = true) }
                        .ifEmpty { fetchedNodes }
                }
                val nodes = regionalNodes.take(MAX_AUTO_NODES)
                if (nodes.isEmpty()) {
                    error("Control API returned no healthy gateways")
                }

                _state.value = _state.value.copy(
                    discoveredNodes = nodes.size,
                    serverLabel = "Проверяем ${nodes.size} серверов…",
                )

                val measurements = coroutineScope {
                    nodes.map { node ->
                        async {
                            runCatching {
                                val metrics = routeProbe.measure(
                                    host = node.host,
                                    port = node.udpPort,
                                    samples = AUTO_SAMPLES,
                                )
                                GatewayMeasurement(
                                    node = node,
                                    metrics = metrics,
                                    score = routeScore(metrics, _state.value.boostMode),
                                )
                            }.getOrNull()
                        }
                    }.awaitAll().filterNotNull()
                }

                val eligible = measurements
                    .filter {
                        it.metrics.received > 0 &&
                            it.score.isFinite() &&
                            !it.node.wireGuardPublicKey.isNullOrBlank() &&
                            it.node.wireGuardPort != null
                    }

                val best = eligible.minByOrNull { it.score }
                    ?: error("Нет доступного сервера, готового к бусту")

                val currentMeasurement = current.selectedGatewayId?.let { currentId ->
                    eligible.firstOrNull { it.node.id == currentId }
                }

                RouteDecisionPolicy.choose(
                    current = currentMeasurement,
                    bestCandidate = best,
                )
            }.onSuccess { best ->
                _state.value = _state.value.copy(
                    isAutoSelecting = false,
                    gatewayHost = best.node.host,
                    gatewayPort = best.node.udpPort,
                    wireGuardServerPublicKey = best.node.wireGuardPublicKey
                        ?: _state.value.wireGuardServerPublicKey,
                    wireGuardPort = best.node.wireGuardPort
                        ?: _state.value.wireGuardPort,
                    selectedGatewayId = best.node.id,
                    selectedGatewayRegion = best.node.region,
                    pingMs = best.metrics.medianRttMs,
                    jitterMs = best.metrics.jitterMs,
                    packetLossPct = best.metrics.packetLossPct,
                    serverLabel = "Автовыбор: ${best.node.region} · ${best.node.id}",
                    showAdvancedSettings = false,
                    probeError = null,
                )
                persistProfile()
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    isAutoSelecting = false,
                    serverLabel = "Автовыбор не удался",
                    probeError = error.message ?: "Неизвестная ошибка",
                )
            }
        }
    }

    fun probeGateway() {
        val current = _state.value
        if (current.gatewayHost.isBlank() || current.isProbing) return

        _state.value = current.copy(
            isProbing = true,
            probeError = null,
            serverLabel = "Проверяем маршрут…",
        )

        viewModelScope.launch {
            runCatching {
                routeProbe.measure(
                    host = _state.value.gatewayHost,
                    port = _state.value.gatewayPort,
                )
            }.onSuccess { metrics ->
                _state.value = _state.value.copy(
                    isProbing = false,
                    pingMs = metrics.medianRttMs,
                    jitterMs = metrics.jitterMs,
                    packetLossPct = metrics.packetLossPct,
                    serverLabel = if (metrics.received > 0) {
                        "Сервер отвечает: ${metrics.received}/${metrics.sent}"
                    } else {
                        "Сервер не отвечает"
                    },
                    probeError = null,
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    isProbing = false,
                    pingMs = null,
                    jitterMs = null,
                    packetLossPct = null,
                    serverLabel = "Проверка не удалась",
                    probeError = error.message ?: "Неизвестная ошибка",
                )
            }
        }
    }

    fun connectTunnel() {
        val snapshot = _state.value
        if (snapshot.isTunnelConnecting || snapshot.isBoosting) return

        val selectedApp = snapshot.selectedApp
        if (selectedApp == null) {
            _state.value = snapshot.copy(tunnelError = "Выбери приложение")
            return
        }
        if (snapshot.gatewayHost.isBlank()) {
            _state.value = snapshot.copy(tunnelError = "Не задан gateway")
            return
        }
        if (snapshot.wireGuardServerPublicKey.isBlank()) {
            _state.value = snapshot.copy(tunnelError = "Не задан публичный ключ WireGuard-сервера")
            return
        }

        _state.value = snapshot.copy(
            isTunnelConnecting = true,
            tunnelError = null,
            routeHealth = "CONNECTING",
            routeProbeFailures = 0,
        )

        viewModelScope.launch {
            runCatching {
                val identity = identityStore.loadOrCreate()
                val profile = TunnelProfile(
                    privateKey = identity.privateKeyBase64,
                    serverPublicKey = snapshot.wireGuardServerPublicKey,
                    endpointHost = snapshot.gatewayHost,
                    endpointPort = snapshot.wireGuardPort,
                    addressCidr = snapshot.tunnelAddress,
                    dnsServer = snapshot.dnsServer,
                    selectedPackage = selectedApp.packageName,
                )
                tunnelController.connect(profile)
            }.onSuccess { tunnelState ->
                val traffic = if (tunnelState == Tunnel.State.UP) {
                    runCatching {
                        tunnelController.traffic(snapshot.wireGuardServerPublicKey)
                    }.getOrNull()
                } else {
                    null
                }

                trafficBaselineRx = traffic?.rxBytes ?: 0L
                trafficBaselineTx = traffic?.txBytes ?: 0L

                _state.value = _state.value.copy(
                    isTunnelConnecting = false,
                    isBoosting = tunnelState == Tunnel.State.UP,
                    tunnelError = if (tunnelState == Tunnel.State.UP) null else "WireGuard tunnel did not reach UP state",
                    tunnelRxBytes = traffic?.rxBytes ?: 0L,
                    tunnelTxBytes = traffic?.txBytes ?: 0L,
                    gameTrafficVerified = false,
                    routeHealth = if (tunnelState == Tunnel.State.UP) "CONNECTED" else "DOWN",
                    routeProbeFailures = 0,
                )

                if (tunnelState == Tunnel.State.UP) {
                    debugLog("Network Boost: handshake подтверждён · " + snapshot.gatewayHost)
                    startLiveMetrics()
                    if (_state.value.autoLaunchAfterNetworkBoost) {
                        boostAndLaunchGame()
                    }
                } else {
                    stopLiveMetrics()
                }
            }.onFailure { error ->
                stopLiveMetrics()
                trafficBaselineRx = 0L
                trafficBaselineTx = 0L
                _state.value = _state.value.copy(
                    isTunnelConnecting = false,
                    isBoosting = false,
                    tunnelError = "WireGuard connect failed: ${error::class.java.simpleName}",
                    tunnelRxBytes = 0L,
                    tunnelTxBytes = 0L,
                    gameTrafficVerified = false,
                    routeHealth = "DOWN",
                    routeProbeFailures = 0,
                )
            }
        }
    }

    fun disconnectTunnel() {
        if (_state.value.isTunnelConnecting) return

        stopLiveMetrics()
        _state.value = _state.value.copy(
            isTunnelConnecting = true,
            tunnelError = null,
        )

        viewModelScope.launch {
            runCatching { tunnelController.disconnect() }
                .onSuccess {
                    trafficBaselineRx = 0L
                    trafficBaselineTx = 0L
                    _state.value = _state.value.copy(
                        isTunnelConnecting = false,
                        isBoosting = false,
                        tunnelError = null,
                        tunnelRxBytes = 0L,
                        tunnelTxBytes = 0L,
                        gameTrafficVerified = false,
                        routeHealth = "IDLE",
                        routeProbeFailures = 0,
                    )
                    debugLog("Network Boost отключён; системный маршрут восстановлен")
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isTunnelConnecting = false,
                        tunnelError = "WireGuard disconnect failed: ${error::class.java.simpleName}",
                    )
                    if (_state.value.isBoosting) {
                        startLiveMetrics()
                    }
                }
        }
    }

    private suspend fun restoreTunnelOrRefreshRoute(saved: PrivateServerProfile) {
        val existingTunnelState = runCatching { tunnelController.state() }.getOrNull()
        val canRestoreActiveSession =
            existingTunnelState == Tunnel.State.UP &&
                _state.value.selectedApp != null &&
                saved.gatewayHost.isNotBlank() &&
                saved.wireGuardServerPublicKey.isNotBlank()

        if (canRestoreActiveSession) {
            val traffic = runCatching {
                tunnelController.traffic(saved.wireGuardServerPublicKey)
            }.getOrNull()
            trafficBaselineRx = traffic?.rxBytes ?: 0L
            trafficBaselineTx = traffic?.txBytes ?: 0L
            _state.value = _state.value.copy(
                isBoosting = true,
                isTunnelConnecting = false,
                tunnelError = null,
                serverLabel = "Буст активен · ${saved.gatewayHost}",
                tunnelRxBytes = traffic?.rxBytes ?: 0L,
                tunnelTxBytes = traffic?.txBytes ?: 0L,
                gameTrafficVerified = false,
                routeHealth = "CONNECTED",
                routeProbeFailures = 0,
            )
            startLiveMetrics()
            return
        }

        if (existingTunnelState == Tunnel.State.UP) {
            runCatching { tunnelController.disconnect() }
        }

        if (saved.controlPlaneUrl.startsWith("https://")) {
            autoSelectGateway()
        } else if (saved.gatewayHost.isNotBlank()) {
            probeGateway()
        }
    }

    private fun startLiveMetrics() {
        stopLiveMetrics()
        liveMetricsJob = viewModelScope.launch {
            var consecutiveProbeFailures = 0

            while (_state.value.isBoosting) {
                val snapshot = _state.value
                if (snapshot.gatewayHost.isBlank()) break

                when (runCatching { tunnelController.state() }.getOrNull()) {
                    Tunnel.State.DOWN -> {
                        trafficBaselineRx = 0L
                        trafficBaselineTx = 0L
                        _state.value = _state.value.copy(
                            isBoosting = false,
                            isTunnelConnecting = false,
                            serverLabel = "Буст отключён",
                            tunnelError = "VPN-туннель остановлен",
                            tunnelRxBytes = 0L,
                            tunnelTxBytes = 0L,
                            gameTrafficVerified = false,
                            routeHealth = "DOWN",
                        )
                        break
                    }
                    else -> Unit
                }

                val traffic = runCatching {
                    tunnelController.traffic(snapshot.wireGuardServerPublicKey)
                }.getOrNull()

                val transferredSinceConnect = traffic?.let {
                    (it.rxBytes - trafficBaselineRx).coerceAtLeast(0L) +
                        (it.txBytes - trafficBaselineTx).coerceAtLeast(0L)
                } ?: 0L
                val trafficVerifiedNow = transferredSinceConnect >= TRAFFIC_VERIFY_MIN_BYTES

                if (traffic != null && _state.value.isBoosting) {
                    _state.value = _state.value.copy(
                        tunnelRxBytes = traffic.rxBytes,
                        tunnelTxBytes = traffic.txBytes,
                        gameTrafficVerified = _state.value.gameTrafficVerified || trafficVerifiedNow,
                    )
                }

                runCatching {
                    routeProbe.measure(
                        host = snapshot.gatewayHost,
                        port = snapshot.gatewayPort,
                        samples = LIVE_METRICS_SAMPLES,
                    )
                }.onSuccess { metrics ->
                    consecutiveProbeFailures = if (metrics.received > 0) 0 else consecutiveProbeFailures + 1
                    if (_state.value.isBoosting) {
                        val verified = _state.value.gameTrafficVerified || trafficVerifiedNow
                        _state.value = _state.value.copy(
                            pingMs = metrics.medianRttMs,
                            jitterMs = metrics.jitterMs,
                            packetLossPct = metrics.packetLossPct,
                            routeProbeFailures = consecutiveProbeFailures,
                            routeHealth = when {
                                consecutiveProbeFailures >= MAX_LIVE_PROBE_FAILURES -> "DEGRADED"
                                verified -> "TRAFFIC"
                                else -> "CONNECTED"
                            },
                        )
                    }
                }.onFailure {
                    consecutiveProbeFailures += 1
                    if (_state.value.isBoosting) {
                        _state.value = _state.value.copy(
                            routeProbeFailures = consecutiveProbeFailures,
                            routeHealth = if (consecutiveProbeFailures >= MAX_LIVE_PROBE_FAILURES) {
                                "DEGRADED"
                            } else {
                                _state.value.routeHealth
                            },
                        )
                    }
                }

                delay(LIVE_METRICS_INTERVAL_MS)
            }
        }
    }

    private fun stopLiveMetrics() {
        liveMetricsJob?.cancel()
        liveMetricsJob = null
    }

    private fun activeDnsValue(snapshot: BoostState): String {
        return if (snapshot.customDnsEnabled && snapshot.customDnsServers.isNotEmpty()) {
            snapshot.customDnsServers.take(4).joinToString(",")
        } else {
            snapshot.dnsServer
        }
    }

    private fun routeScore(metrics: com.boostlab.app.network.RouteMetrics, mode: String): Double {
        val rtt = metrics.medianRttMs ?: return Double.POSITIVE_INFINITY
        val jitter = metrics.jitterMs ?: 50
        return when (mode) {
            "LOW_PING" -> rtt.toDouble() + (jitter * 1.2) + (metrics.packetLossPct * 16.0)
            "STABLE" -> (rtt * 0.7) + (jitter * 3.5) + (metrics.packetLossPct * 30.0)
            else -> RouteScorer.score(metrics)
        }
    }

    private fun recordBoostSessionIfNeeded() = _state.value.boostStartedAtEpochMs?.let { startedAt ->
        val snapshot = _state.value
        val durationSeconds = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0L)
        boostHistoryStore.record(
            durationSeconds = durationSeconds,
            pingMs = snapshot.pingMs,
            jitterMs = snapshot.jitterMs,
            packetLossPct = snapshot.packetLossPct,
        )
    }

    private fun debugLog(message: String) {
        if (!_state.value.debugLogging) return
        diagnosticLogStore.append(message)
        _state.value = _state.value.copy(diagnosticLogEntries = diagnosticLogStore.load())
    }

    private fun shareText(subject: String, body: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(send, subject)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(chooser) }
    }

    private fun persistProfile() {
        val snapshot = _state.value
        profileStore.save(
            PrivateServerProfile(
                selectedPackage = snapshot.selectedApp?.packageName,
                controlPlaneUrl = snapshot.controlPlaneUrl,
                gatewayHost = snapshot.gatewayHost,
                gatewayPort = snapshot.gatewayPort,
                wireGuardServerPublicKey = snapshot.wireGuardServerPublicKey,
                wireGuardPort = snapshot.wireGuardPort,
                tunnelAddress = snapshot.tunnelAddress,
                dnsServer = snapshot.dnsServer,
            ),
        )
    }

    companion object {
        private const val MAX_AUTO_NODES = 8
        private const val AUTO_SAMPLES = 6
        private const val LAN_AUTO_SAMPLES = 4
        private const val LIVE_METRICS_SAMPLES = 4
        private const val LIVE_METRICS_INTERVAL_MS = 5_000L
        private const val TRAFFIC_VERIFY_MIN_BYTES = 1_024L
        private const val MAX_LIVE_PROBE_FAILURES = 3
    }
}
