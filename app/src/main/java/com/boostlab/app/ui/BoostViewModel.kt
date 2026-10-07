package com.boostlab.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boostlab.app.auth.DeviceAuthStore
import com.boostlab.app.data.BoostHistoryStore
import com.boostlab.app.data.DiagnosticLogStore
import com.boostlab.app.data.EventStore
import com.boostlab.app.data.GameCatalogRepository
import com.boostlab.app.data.GameProfileStore
import com.boostlab.app.data.InstalledAppsRepository
import com.boostlab.app.data.PinnedAppsStore
import com.boostlab.app.data.PrivateProfileStore
import com.boostlab.app.data.RouteMemoryStore
import com.boostlab.app.data.SocialStore
import com.boostlab.app.data.UserSettingsStore
import com.boostlab.app.data.PrivateServerProfile
import com.boostlab.app.boost.GameBoostEngine
import com.boostlab.app.boost.GameLaunchAdvisor
import com.boostlab.app.boost.GameLaunchPolicy
import com.boostlab.app.model.BoostApp
import com.boostlab.app.model.BoostState
import com.boostlab.app.model.GameLaunchMode
import com.boostlab.app.model.SquadChatMessage
import com.boostlab.app.network.ControlPlaneClient
import com.boostlab.app.network.TcpRouteProbe
import com.boostlab.app.network.RouteIntelligence
import com.boostlab.app.network.GatewayProvisionClient
import com.boostlab.app.network.GatewayRouteQualityClient
import com.boostlab.app.network.GatewayStatusClient
import com.boostlab.app.network.AutoRouteSelection
import com.boostlab.app.network.GatewayCapacityPolicy
import com.boostlab.app.network.GatewayMeasurement
import com.boostlab.app.network.GatewayNode
import com.boostlab.app.network.LanGatewayDiscovery
import com.boostlab.app.network.RouteDecisionPolicy
import com.boostlab.app.network.RouteRacePolicy
import com.boostlab.app.network.RouteScorer
import com.boostlab.app.network.SquadApiClient
import com.boostlab.app.network.UdpRouteProbe
import com.boostlab.app.notifications.AppNotificationCenter
import com.boostlab.app.tunnel.ClientIdentityStore
import com.boostlab.app.tunnel.TunnelMtuPolicy
import com.boostlab.app.tunnel.TunnelProfile
import com.boostlab.app.tunnel.WireGuardTunnelController
import com.boostlab.app.voice.VoiceIceServer
import com.boostlab.app.voice.WebRtcVoiceController
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
import org.json.JSONObject
import kotlin.math.abs

class BoostViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = InstalledAppsRepository(appContext)
    private val profileStore = PrivateProfileStore(appContext)
    private val gameProfileStore = GameProfileStore(appContext)
    private val pinnedAppsStore = PinnedAppsStore(appContext)
    private val userSettingsStore = UserSettingsStore(appContext)
    private val diagnosticLogStore = DiagnosticLogStore(appContext)
    private val boostHistoryStore = BoostHistoryStore(appContext)
    private val routeMemoryStore = RouteMemoryStore(appContext)
    private val eventStore = EventStore(appContext)
    private val socialStore = SocialStore(appContext)
    private val routeProbe = UdpRouteProbe()
    private val directRouteProbe = TcpRouteProbe()
    private val gatewayRouteQuality = GatewayRouteQualityClient()
    private val gatewayStatusClient = GatewayStatusClient()
    private val lanDiscovery = LanGatewayDiscovery()
    private val controlPlane = ControlPlaneClient()
    private val gatewayProvisionClient = GatewayProvisionClient()
    private val squadApi = SquadApiClient()
    private val deviceAuthStore = DeviceAuthStore()
    private val identityStore = ClientIdentityStore(appContext)
    private val tunnelController = WireGuardTunnelController(appContext)
    private val gameBoostEngine = GameBoostEngine(appContext)
    private val voiceController = WebRtcVoiceController(appContext)
    private val notificationCenter = AppNotificationCenter(appContext)
    private var liveMetricsJob: Job? = null
    private var squadSyncJob: Job? = null
    private var directSyncJob: Job? = null
    private var squadLastEventId = 0L
    private var directLastEventId = 0L
    private var squadInitialSyncDone = false
    private var pendingVoiceOfferSender: String? = null
    private var pendingVoiceOfferPayload: String? = null
    private val pendingIncomingVoiceIce = mutableListOf<Pair<String, String>>()
    private var trafficBaselineRx = 0L
    private var trafficBaselineTx = 0L
    private var deviceAccessToken: String? = null
    private var deviceAccessTokenExpiresAtEpochMs = 0L
    private var authenticatedDeviceId: String? = null

    private val _state = MutableStateFlow(BoostState())
    val state: StateFlow<BoostState> = _state.asStateFlow()

    val apps: List<BoostApp> = repository.loadLaunchableApps()
    val catalogGames: List<com.boostlab.app.model.CatalogGame>
        get() = (GameCatalogRepository.games + _state.value.remoteCatalogGames)
            .distinctBy { it.title.lowercase() }

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
            provisioningEnrollmentCode = saved.provisioningEnrollmentCode,
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
            appEvents = eventStore.load(),
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
        startSquadSync()
        refreshRemoteCatalog()
        refreshVoiceInfrastructure()
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
            routeRecommendation = "UNKNOWN",
            routeDecisionTransport = null,
            routeDecisionAtEpochMs = null,
            routeTargetId = null,
            routeTargetHost = null,
            routeTargetPort = null,
            selectedRouteApiUrl = null,
            directPingMs = null,
            directP95Ms = null,
            directJitterMs = null,
            directPacketLossPct = null,
            boostedEstimatedPingMs = null,
            boostedEstimatedP95Ms = null,
            routeGainMs = null,
            routeCandidatesTested = 0,
            routeSelectorVisible = true,
        )
        refreshGameReadiness()
        persistProfile()
        debugLog("Выбрано приложение: ${app.label} [${app.packageName}]")
        addEvent("Игра", "Выбрана ${app.label}")
        refreshGatewayDirectory()
    }

    fun closeRouteSelector() {
        if (_state.value.isAutoSelecting || _state.value.isTunnelConnecting) return
        _state.value = _state.value.copy(routeSelectorVisible = false)
    }

    fun refreshGatewayDirectory() {
        val snapshot = _state.value
        if (
            snapshot.gatewayDirectoryLoading ||
            snapshot.isAutoSelecting ||
            snapshot.isBoosting ||
            snapshot.isTunnelConnecting
        ) return

        if (!snapshot.controlPlaneUrl.startsWith("https://")) {
            _state.value = snapshot.copy(
                gatewayDirectoryLoading = false,
                gatewayDirectory = emptyList(),
                gatewayDirectoryPingMs = emptyMap(),
                gatewayDirectoryLossPct = emptyMap(),
                gatewayDirectoryError = "Сеть BOOSTLAB ещё не настроена",
            )
            return
        }

        _state.value = snapshot.copy(
            gatewayDirectoryLoading = true,
            gatewayDirectoryError = null,
        )

        viewModelScope.launch {
            runCatching {
                controlPlane.fetchNodes(_state.value.controlPlaneUrl)
                    .filter {
                        !it.wireGuardPublicKey.isNullOrBlank() &&
                            it.wireGuardPort != null
                    }
                    .take(MAX_DIRECTORY_NODES)
            }.onSuccess { nodes ->
                _state.value = _state.value.copy(
                    gatewayDirectory = nodes,
                    gatewayDirectoryLoading = nodes.isNotEmpty(),
                    gatewayDirectoryError = if (nodes.isEmpty()) {
                        "Нет доступных Gateway"
                    } else {
                        null
                    },
                )

                val measurements = coroutineScope {
                    nodes.map { node ->
                        async {
                            val metrics = runCatching {
                                routeProbe.measure(
                                    host = node.host,
                                    port = node.udpPort,
                                    samples = DIRECTORY_SAMPLES,
                                )
                            }.getOrNull()
                            node.id to metrics
                        }
                    }.awaitAll()
                }

                val pingMap = measurements.mapNotNull { (id, metrics) ->
                    metrics?.medianRttMs?.let { id to it }
                }.toMap()
                val lossMap = measurements.mapNotNull { (id, metrics) ->
                    metrics?.let { id to it.packetLossPct }
                }.toMap()

                _state.value = _state.value.copy(
                    gatewayDirectoryLoading = false,
                    gatewayDirectoryPingMs = pingMap,
                    gatewayDirectoryLossPct = lossMap,
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    gatewayDirectoryLoading = false,
                    gatewayDirectory = emptyList(),
                    gatewayDirectoryPingMs = emptyMap(),
                    gatewayDirectoryLossPct = emptyMap(),
                    gatewayDirectoryError = error.message ?: "Не удалось загрузить серверы",
                )
            }
        }
    }

    fun selectAutoRoute(mode: String) {
        val snapshot = _state.value
        if (snapshot.isBoosting || snapshot.isTunnelConnecting) return
        if (!snapshot.controlPlaneUrl.startsWith("https://")) {
            _state.value = snapshot.copy(
                gatewayDirectoryError = "Сначала настрой BOOSTLAB Control Server",
            )
            return
        }

        val normalized = when (mode.trim().uppercase()) {
            "LOW_PING" -> "LOW_PING"
            "STABLE" -> "STABLE"
            else -> "SMART"
        }
        val settings = userSettingsStore.load().copy(
            autoSelectBestNode = true,
            boostMode = normalized,
            preferredRegion = "AUTO",
        )
        userSettingsStore.save(settings)
        _state.value = _state.value.copy(
            autoSelectBestNode = true,
            boostMode = normalized,
            preferredRegion = "AUTO",
            routeSelectorVisible = false,
            tunnelError = null,
        )
        invalidateRouteIntelligence()
        autoSelectGateway()
    }

    fun selectGatewayFromDirectory(nodeId: String) {
        val snapshot = _state.value
        if (
            snapshot.isBoosting ||
            snapshot.isTunnelConnecting ||
            snapshot.isAutoSelecting
        ) return
        val node = snapshot.gatewayDirectory.firstOrNull { it.id == nodeId } ?: return
        val publicKey = node.wireGuardPublicKey ?: return
        val wireGuardPort = node.wireGuardPort ?: return

        val settings = userSettingsStore.load().copy(autoSelectBestNode = false)
        userSettingsStore.save(settings)
        _state.value = snapshot.copy(
            autoSelectBestNode = false,
            routeSelectorVisible = false,
            gatewayHost = node.host,
            gatewayPort = node.udpPort,
            wireGuardServerPublicKey = publicKey,
            wireGuardPort = wireGuardPort,
            selectedGatewayId = node.id,
            selectedGatewayRegion = node.region,
            selectedRouteApiUrl = node.routeApiUrl,
            routeRecommendation = "GATEWAY_ONLY",
            pingMs = snapshot.gatewayDirectoryPingMs[node.id],
            p95PingMs = null,
            jitterMs = null,
            packetLossPct = snapshot.gatewayDirectoryLossPct[node.id],
            serverLabel = node.displayName
                ?: node.city?.let { "${node.countryCode ?: node.region} · $it" }
                ?: node.region,
            probeError = null,
            tunnelError = null,
        )
        persistProfile()
        probeGateway()
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
        if (_state.value.isBoosting || _state.value.isTunnelConnecting || _state.value.isAutoSelecting) return
        val updated = userSettingsStore.load().copy(autoSelectBestNode = enabled)
        userSettingsStore.save(updated)
        _state.value = _state.value.copy(autoSelectBestNode = enabled)
        invalidateRouteIntelligence()
        debugLog("Автовыбор лучшего узла: $enabled")
    }

    fun setPreferredRegion(region: String) {
        if (_state.value.isBoosting || _state.value.isTunnelConnecting || _state.value.isAutoSelecting) return
        val normalized = region.trim().uppercase().ifBlank { "AUTO" }
        val updated = userSettingsStore.load().copy(preferredRegion = normalized)
        userSettingsStore.save(updated)
        _state.value = _state.value.copy(preferredRegion = normalized)
        invalidateRouteIntelligence()
        debugLog("Регион узла: $normalized")
    }

    fun setBoostMode(mode: String) {
        if (_state.value.isBoosting || _state.value.isTunnelConnecting || _state.value.isAutoSelecting) return
        val normalized = mode.trim().uppercase().let {
            when (it) {
                "LOW_PING", "STABLE" -> it
                else -> "SMART"
            }
        }
        val updated = userSettingsStore.load().copy(boostMode = normalized)
        userSettingsStore.save(updated)
        _state.value = _state.value.copy(boostMode = normalized)
        invalidateRouteIntelligence()
        debugLog("Режим Network Boost: $normalized")
    }

    fun setCustomDnsEnabled(enabled: Boolean) {
        if (_state.value.isBoosting || _state.value.isTunnelConnecting) return
        val updated = userSettingsStore.load().copy(customDnsEnabled = enabled)
        userSettingsStore.save(updated)
        _state.value = _state.value.copy(customDnsEnabled = enabled)
        debugLog("Custom DNS: $enabled")
    }

    fun addCustomDns(value: String) {
        if (_state.value.isBoosting || _state.value.isTunnelConnecting) return
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
        if (_state.value.isBoosting || _state.value.isTunnelConnecting) return
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
        addEvent("Отряд", "Создан ${social.squadCode}")
        startSquadSync(resetCursor = true)
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
        addEvent("Отряд", "Присоединение по коду $normalized")
        startSquadSync(resetCursor = true)
    }

    fun leaveSquad() {
        when (_state.value.voiceCallState) {
            "RINGING" -> rejectVoiceCall()
            "CALLING", "CONNECTING", "RECONNECTING", "CONNECTED" -> voiceController.hangup()
        }
        pendingVoiceOfferSender = null
        pendingVoiceOfferPayload = null
        pendingIncomingVoiceIce.clear()
        squadSyncJob?.cancel()
        squadSyncJob = null
        squadLastEventId = 0L
        squadInitialSyncDone = false
        notificationCenter.cancelIncomingCall()
        val social = socialStore.leaveSquad()
        _state.value = _state.value.copy(
            squadCode = null,
            localUserId = social.userId,
            friends = social.friends,
            squadMessages = emptyList(),
            squadOnlineUsers = emptyList(),
            squadSyncError = null,
            voiceCallState = "IDLE",
            voicePeerId = null,
            voiceMuted = false,
            voiceError = null,
        )
        debugLog("Выход из отряда")
    }

    fun addFriend(alias: String) {
        val name = alias.trim()
        if (name.isBlank()) return
        val social = socialStore.addFriend(name)
        _state.value = _state.value.copy(friends = social.friends)
        debugLog("Добавлен друг: $name")
        addEvent("Друзья", "Добавлен $name")
    }


    fun openDirectChat(peer: String) {
        val normalized = peer.trim()
        if (normalized.isBlank() || normalized == _state.value.localUserId) return
        directSyncJob?.cancel()
        directLastEventId = 0L
        _state.value = _state.value.copy(
            directPeerId = normalized,
            directMessages = emptyList(),
            directSyncError = null,
        )
        startDirectSync()
    }

    fun closeDirectChat() {
        directSyncJob?.cancel()
        directSyncJob = null
        directLastEventId = 0L
        _state.value = _state.value.copy(
            directPeerId = null,
            directMessages = emptyList(),
            directSyncError = null,
        )
    }

    fun sendDirectMessage(text: String) {
        val message = text.trim()
        val snapshot = _state.value
        val peer = snapshot.directPeerId ?: return
        if (message.isBlank()) return
        if (!snapshot.controlPlaneUrl.startsWith("https://")) {
            _state.value = snapshot.copy(directSyncError = "Для личного чата нужен Control API HTTPS")
            return
        }

        viewModelScope.launch {
            runCatching {
                squadApi.sendDirectChat(
                    baseUrl = _state.value.controlPlaneUrl,
                    peer = peer,
                    sender = _state.value.localUserId,
                    text = message.take(1000),
                )
            }.onSuccess { event ->
                directLastEventId = maxOf(directLastEventId, event.id)
                val chat = SquadChatMessage(
                    id = event.id,
                    sender = event.sender,
                    text = event.text,
                    createdAt = event.createdAt,
                )
                _state.value = _state.value.copy(
                    directMessages = (_state.value.directMessages + chat)
                        .distinctBy { it.id }
                        .sortedBy { it.id }
                        .takeLast(MAX_DIRECT_MESSAGES),
                    directSyncError = null,
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    directSyncError = error.message ?: "Не удалось отправить личное сообщение",
                )
            }
        }
    }

    fun refreshDirectChat() {
        startDirectSync()
    }

    fun shareSquadInvite() {
        val code = _state.value.squadCode ?: return
        shareText(
            "BOOSTLAB отряд",
            "Присоединяйся к моему отряду BOOSTLAB. Код: $code\nboostlab://squad/$code",
        )
    }


    fun sendSquadMessage(text: String) {
        val message = text.trim()
        val snapshot = _state.value
        val code = snapshot.squadCode ?: return
        if (message.isBlank()) return
        if (!snapshot.controlPlaneUrl.startsWith("https://")) {
            _state.value = snapshot.copy(squadSyncError = "Для сетевого чата укажи Control API HTTPS")
            return
        }

        viewModelScope.launch {
            runCatching {
                squadApi.sendChat(
                    baseUrl = _state.value.controlPlaneUrl,
                    code = code,
                    sender = _state.value.localUserId,
                    text = message.take(1000),
                )
            }.onSuccess { event ->
                squadLastEventId = maxOf(squadLastEventId, event.id)
                val chat = SquadChatMessage(
                    id = event.id,
                    sender = event.sender,
                    text = event.text,
                    createdAt = event.createdAt,
                )
                _state.value = _state.value.copy(
                    squadMessages = (_state.value.squadMessages + chat)
                        .distinctBy { it.id }
                        .sortedBy { it.id }
                        .takeLast(MAX_SQUAD_MESSAGES),
                    squadSyncError = null,
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    squadSyncError = error.message ?: "Не удалось отправить сообщение",
                )
            }
        }
    }

    fun refreshSquadNow() {
        startSquadSync(resetCursor = false)
    }

    fun sendVoiceSignal(type: String, payload: String) {
        val snapshot = _state.value
        val code = snapshot.squadCode ?: return
        if (!snapshot.controlPlaneUrl.startsWith("https://")) return
        if (type !in setOf("voice_offer", "voice_answer", "voice_ice", "voice_hangup")) return
        if (payload.isBlank()) return

        viewModelScope.launch {
            runCatching {
                squadApi.sendSignal(
                    baseUrl = _state.value.controlPlaneUrl,
                    code = code,
                    sender = _state.value.localUserId,
                    type = type,
                    payload = payload,
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    squadSyncError = error.message ?: "Ошибка voice signaling",
                )
            }
        }
    }

    fun refreshRemoteCatalog() {
        val baseUrl = _state.value.controlPlaneUrl
        if (!baseUrl.startsWith("https://")) return

        viewModelScope.launch {
            runCatching { controlPlane.fetchGames(baseUrl) }
                .onSuccess { games ->
                    _state.value = _state.value.copy(remoteCatalogGames = games)
                    if (games.isNotEmpty()) {
                        debugLog("Удалённый каталог игр обновлён: ${games.size}")
                    }
                }
                .onFailure { error ->
                    debugLog("Не удалось обновить каталог игр: ${error::class.java.simpleName}")
                }
        }
    }


    fun refreshVoiceInfrastructure() {
        val baseUrl = _state.value.controlPlaneUrl
        if (!baseUrl.startsWith("https://")) {
            _state.value = _state.value.copy(
                voiceIceServerCount = 0,
                voiceTurnAvailable = false,
                voiceInfrastructureError = "Control API HTTPS не настроен",
            )
            return
        }

        viewModelScope.launch {
            runCatching { squadApi.fetchVoiceIce(baseUrl, _state.value.localUserId) }
                .onSuccess { servers ->
                    _state.value = _state.value.copy(
                        voiceIceServerCount = servers.sumOf { it.urls.size },
                        voiceTurnAvailable = servers.any { server ->
                            server.urls.any { it.startsWith("turn:") || it.startsWith("turns:") }
                        },
                        voiceInfrastructureError = null,
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        voiceIceServerCount = 0,
                        voiceTurnAvailable = false,
                        voiceInfrastructureError = error.message ?: "Не удалось проверить Voice ICE",
                    )
                }
        }
    }


    fun startOrAcceptVoiceCall() {
        val snapshot = _state.value
        if (snapshot.squadCode == null) return
        if (!snapshot.controlPlaneUrl.startsWith("https://")) {
            _state.value = snapshot.copy(voiceError = "Для звонка нужен Control API HTTPS")
            return
        }

        val stateSink: (String, String?) -> Unit = { state, error ->
            _state.value = _state.value.copy(
                voiceCallState = state,
                voiceError = error,
                voicePeerId = if (state == "IDLE") null else _state.value.voicePeerId,
                voiceMuted = if (state == "IDLE") false else _state.value.voiceMuted,
            )
            if (state == "IDLE") {
                pendingVoiceOfferSender = null
                pendingVoiceOfferPayload = null
                pendingIncomingVoiceIce.clear()
            }
        }
        val signalSink: (String, String) -> Unit = { type, payload ->
            sendVoiceSignal(type, payload)
        }

        if (snapshot.voiceCallState == "RINGING") {
            val sender = pendingVoiceOfferSender ?: return
            val payload = pendingVoiceOfferPayload ?: return

            viewModelScope.launch {
                val iceServers = loadVoiceIceServers(snapshot.controlPlaneUrl)
                if (
                    _state.value.voiceCallState != "RINGING" ||
                    pendingVoiceOfferSender != sender
                ) {
                    return@launch
                }

                _state.value = _state.value.copy(
                    voiceCallState = "CONNECTING",
                    voicePeerId = sender,
                    voiceError = null,
                )
                runCatching {
                    voiceController.acceptIncoming(
                        localUserId = snapshot.localUserId,
                        peerUserId = sender,
                        offerPayload = payload,
                        iceServers = iceServers,
                        signalSink = signalSink,
                        stateSink = stateSink,
                    )
                    pendingIncomingVoiceIce
                        .filter { it.first == sender }
                        .forEach { (_, icePayload) ->
                            voiceController.handleSignal(sender, "voice_ice", icePayload)
                        }
                    pendingIncomingVoiceIce.clear()
                }.onFailure { error ->
                    _state.value = _state.value.copy(
                        voiceCallState = "FAILED",
                        voiceError = error.message ?: "Не удалось принять звонок",
                    )
                }
            }
            return
        }

        if (snapshot.voiceCallState !in setOf("IDLE", "FAILED")) return
        val peer = snapshot.squadOnlineUsers.firstOrNull { it != snapshot.localUserId }
        if (peer == null) {
            _state.value = snapshot.copy(voiceError = "В отряде сейчас нет второго участника онлайн")
            return
        }

        pendingVoiceOfferSender = null
        pendingVoiceOfferPayload = null
        pendingIncomingVoiceIce.clear()
        _state.value = snapshot.copy(
            voiceCallState = "CALLING",
            voicePeerId = peer,
            voiceMuted = false,
            voiceError = null,
        )
        viewModelScope.launch {
            val iceServers = loadVoiceIceServers(snapshot.controlPlaneUrl)
            if (_state.value.voiceCallState != "CALLING" || _state.value.voicePeerId != peer) {
                return@launch
            }

            runCatching {
                voiceController.startOutgoing(
                    localUserId = snapshot.localUserId,
                    peerUserId = peer,
                    iceServers = iceServers,
                    signalSink = signalSink,
                    stateSink = stateSink,
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    voiceCallState = "FAILED",
                    voiceError = error.message ?: "Не удалось начать звонок",
                )
            }
        }
    }

    fun rejectVoiceCall() {
        val sender = pendingVoiceOfferSender
        if (sender != null) {
            val payload = JSONObject().put("target", sender).toString()
            sendVoiceSignal("voice_hangup", payload)
        }
        pendingVoiceOfferSender = null
        pendingVoiceOfferPayload = null
        pendingIncomingVoiceIce.clear()
        notificationCenter.cancelIncomingCall()
        _state.value = _state.value.copy(
            voiceCallState = "IDLE",
            voicePeerId = null,
            voiceMuted = false,
            voiceError = null,
        )
    }

    fun hangupVoiceCall() {
        voiceController.hangup()
        notificationCenter.cancelIncomingCall()
        pendingVoiceOfferSender = null
        pendingVoiceOfferPayload = null
        pendingIncomingVoiceIce.clear()
        _state.value = _state.value.copy(
            voiceCallState = "IDLE",
            voicePeerId = null,
            voiceMuted = false,
        )
    }

    fun toggleVoiceMute() {
        val muted = !_state.value.voiceMuted
        voiceController.setMuted(muted)
        _state.value = _state.value.copy(voiceMuted = muted)
    }

    fun onVoicePermissionDenied() {
        _state.value = _state.value.copy(
            voiceError = "Без разрешения на микрофон голосовой звонок не работает",
        )
    }


    fun onNotificationPermissionResult(granted: Boolean) {
        addEvent(
            "Уведомления",
            if (granted) "Разрешены уведомления отряда" else "Уведомления не разрешены",
        )
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

    fun clearAppEvents() {
        eventStore.clear()
        _state.value = _state.value.copy(appEvents = emptyList())
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
                    networkMtu = readiness.networkMtu,
                    tunnelMtu = TunnelMtuPolicy.choose(
                        linkMtu = readiness.networkMtu,
                        networkTransport = readiness.networkTransport,
                    ),
                    externalVpnDetected = readiness.vpnActive && !_state.value.isBoosting,
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
        invalidateRouteIntelligence(clearSelectedGateway = true)
        persistProfile()
        if (value.trim().startsWith("https://")) {
            refreshRemoteCatalog()
            refreshVoiceInfrastructure()
            if (_state.value.squadCode != null) {
                startSquadSync(resetCursor = false)
            }
        }
    }

    fun updateGatewayHost(value: String) {
        if (_state.value.isBoosting || _state.value.isTunnelConnecting) return
        _state.value = _state.value.copy(
            gatewayHost = value.trim(),
            selectedGatewayId = null,
            selectedGatewayRegion = null,
            selectedRouteApiUrl = null,
            probeError = null,
            tunnelError = null,
        )
        invalidateRouteIntelligence()
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

    fun updateProvisioningEnrollmentCode(value: String) {
        if (_state.value.isBoosting || _state.value.isTunnelConnecting) return
        _state.value = _state.value.copy(
            provisioningEnrollmentCode = value.trim(),
            peerProvisionError = null,
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
                    p95PingMs = best.metrics.p95RttMs,
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

        val readiness = runCatching { gameBoostEngine.inspect() }.getOrNull()
        if (readiness?.vpnActive == true) {
            _state.value = current.copy(
                externalVpnDetected = true,
                probeError = "Отключи другой VPN перед проверкой маршрута",
                serverLabel = "Конфликт VPN",
            )
            return
        }
        val selectionTransport = readiness?.networkTransport ?: current.networkTransport

        invalidateRouteIntelligence()
        _state.value = _state.value.copy(
            isAutoSelecting = true,
            networkTransport = selectionTransport,
            externalVpnDetected = false,
            discoveredNodes = 0,
            routeCandidatesTested = 0,
            probeError = null,
            serverLabel = "Получаем список серверов…",
        )

        viewModelScope.launch {
            runCatching {
                val snapshot = _state.value
                val fetchedNodes = controlPlane.fetchNodes(snapshot.controlPlaneUrl)
                val preferredRegion = snapshot.preferredRegion
                val regionalNodes = if (preferredRegion == "AUTO") {
                    fetchedNodes
                } else {
                    fetchedNodes
                        .filter { it.region.contains(preferredRegion, ignoreCase = true) }
                        .ifEmpty { fetchedNodes }
                }
                val nodes = regionalNodes.take(MAX_AUTO_NODES)
                if (nodes.isEmpty()) {
                    error("Control API returned no healthy gateways")
                }

                val packageName = snapshot.selectedApp?.packageName
                val rememberedGatewayId = packageName?.let {
                    routeMemoryStore.load(
                        packageName = it,
                        boostMode = snapshot.boostMode,
                        networkTransport = selectionTransport,
                    )?.gatewayId
                }

                _state.value = _state.value.copy(
                    discoveredNodes = nodes.size,
                    serverLabel = "Быстрая гонка ${nodes.size} серверов…",
                )

                val quickMeasurements = coroutineScope {
                    nodes.map { node ->
                        async {
                            runCatching {
                                val metrics = routeProbe.measure(
                                    host = node.host,
                                    port = node.udpPort,
                                    samples = AUTO_QUICK_SAMPLES,
                                )
                                GatewayMeasurement(
                                    node = node,
                                    metrics = metrics,
                                    score = routeScore(metrics, snapshot.boostMode),
                                )
                            }.getOrNull()
                        }
                    }.awaitAll().filterNotNull()
                }

                val finalists = RouteRacePolicy.shortlist(
                    quickMeasurements = quickMeasurements,
                    rememberedGatewayId = rememberedGatewayId,
                    limit = AUTO_FINALISTS,
                )
                if (finalists.isEmpty()) {
                    error("Нет доступного сервера, готового к бусту")
                }

                _state.value = _state.value.copy(
                    serverLabel = "Финальная проверка ${finalists.size} серверов…",
                )

                val accessMeasurements = coroutineScope {
                    finalists.map { node ->
                        async {
                            runCatching {
                                val metrics = routeProbe.measure(
                                    host = node.host,
                                    port = node.udpPort,
                                    samples = AUTO_FINAL_SAMPLES,
                                )
                                val runtimeStatus = node.routeApiUrl?.let { routeApiUrl ->
                                    gatewayStatusClient.fetch(routeApiUrl)
                                }
                                if (runtimeStatus != null && !runtimeStatus.dataPlaneReady) {
                                    error("Gateway data plane is not ready")
                                }
                                val capacityPenalty = GatewayCapacityPolicy.penalty(runtimeStatus)
                                GatewayMeasurement(
                                    node = node,
                                    metrics = metrics,
                                    score = routeScore(metrics, snapshot.boostMode) + capacityPenalty,
                                    capacityPenalty = capacityPenalty,
                                )
                            }.getOrNull()
                        }
                    }.awaitAll().filterNotNull()
                }

                val eligibleAccess = accessMeasurements.filter {
                    it.metrics.received > 0 &&
                        it.score.isFinite() &&
                        !it.node.wireGuardPublicKey.isNullOrBlank() &&
                        it.node.wireGuardPort != null
                }
                if (eligibleAccess.isEmpty()) {
                    error("Финалисты не подтвердили стабильный маршрут")
                }

                val packageNameForTargets = packageName
                val targets = if (!packageNameForTargets.isNullOrBlank()) {
                    runCatching {
                        controlPlane.fetchRouteTargets(
                            snapshot.controlPlaneUrl,
                            packageNameForTargets,
                        )
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }

                if (targets.isEmpty() || eligibleAccess.none { !it.node.routeApiUrl.isNullOrBlank() }) {
                    val best = eligibleAccess.minByOrNull { it.score }
                        ?: error("Нет доступного сервера, готового к бусту")
                    val currentMeasurement = current.selectedGatewayId?.let { currentId ->
                        eligibleAccess.firstOrNull { it.node.id == currentId }
                    }
                    val chosen = RouteDecisionPolicy.choose(
                        current = currentMeasurement,
                        bestCandidate = best,
                    )
                    return@runCatching AutoRouteSelection(
                        gateway = chosen,
                        recommendation = "GATEWAY_ONLY",
                        candidatesTested = eligibleAccess.size,
                    )
                }

                _state.value = _state.value.copy(
                    serverLabel = "Сравниваем Direct и маршруты до игры…",
                )

                val directMeasurements = coroutineScope {
                    targets.map { target ->
                        async {
                            runCatching {
                                target to directRouteProbe.measure(
                                    host = target.host,
                                    port = target.tcpPort,
                                    samples = DIRECT_ROUTE_SAMPLES,
                                )
                            }.getOrNull()
                        }
                    }.awaitAll().filterNotNull()
                }.filter { (_, metrics) ->
                    metrics.received > 0 && routeScore(metrics, snapshot.boostMode).isFinite()
                }

                val directByTarget = directMeasurements.associate { (target, metrics) ->
                    target.id to metrics
                }
                val bestDirect = directMeasurements.minByOrNull { (_, metrics) ->
                    routeScore(metrics, snapshot.boostMode)
                }

                if (bestDirect == null) {
                    val best = eligibleAccess.minByOrNull { it.score }
                        ?: error("Нет доступного сервера, готового к бусту")
                    return@runCatching AutoRouteSelection(
                        gateway = best,
                        recommendation = "GATEWAY_ONLY",
                        candidatesTested = eligibleAccess.size,
                    )
                }

                val candidates = coroutineScope {
                    eligibleAccess.flatMap { access ->
                        val routeApiUrl = access.node.routeApiUrl
                        if (routeApiUrl.isNullOrBlank()) {
                            emptyList()
                        } else {
                            targets.mapNotNull { target ->
                                if (directByTarget[target.id] == null) {
                                    return@mapNotNull null
                                }
                                async {
                                    runCatching {
                                        val remote = gatewayRouteQuality.fetch(
                                            routeApiUrl = routeApiUrl,
                                            target = target,
                                        )
                                        if (
                                            remote.metrics.received <= 0 ||
                                            remote.metrics.medianRttMs == null
                                        ) {
                                            return@runCatching null
                                        }
                                        RouteIntelligence.buildCandidate(
                                            node = access.node,
                                            target = target,
                                            directByTarget = directByTarget,
                                            phoneToGatewayMetrics = access.metrics,
                                            gatewayToGameMetrics = remote.metrics,
                                            capacityPenalty = access.capacityPenalty,
                                            scorer = { metrics ->
                                                routeScore(metrics, snapshot.boostMode)
                                            },
                                        )
                                    }.getOrNull()
                                }
                            }
                        }
                    }.awaitAll().filterNotNull()
                }

                if (candidates.isEmpty()) {
                    error("Не удалось проверить полный маршрут до игры")
                }

                val bestPerNode = candidates
                    .groupBy { it.node.id }
                    .mapValues { (_, items) -> items.minBy { it.boostedScore } }

                val bestCandidate = bestPerNode.values.minBy { it.boostedScore }
                val currentCandidate = current.selectedGatewayId?.let(bestPerNode::get)

                val chosenGateway = RouteDecisionPolicy.choose(
                    current = currentCandidate?.let {
                        GatewayMeasurement(
                            node = it.node,
                            metrics = it.boostedMetrics,
                            score = it.boostedScore,
                            capacityPenalty = accessPenaltyForNode(
                                eligibleAccess,
                                it.node.id,
                            ),
                        )
                    },
                    bestCandidate = GatewayMeasurement(
                        node = bestCandidate.node,
                        metrics = bestCandidate.boostedMetrics,
                        score = bestCandidate.boostedScore,
                        capacityPenalty = accessPenaltyForNode(
                            eligibleAccess,
                            bestCandidate.node.id,
                        ),
                    ),
                )

                val chosenCandidate = bestPerNode[chosenGateway.node.id] ?: bestCandidate
                val useBoost = RouteIntelligence.shouldUseBoost(
                    chosenCandidate,
                    snapshot.boostMode,
                )

                AutoRouteSelection(
                    gateway = if (useBoost) {
                        GatewayMeasurement(
                            node = chosenCandidate.node,
                            metrics = chosenCandidate.boostedMetrics,
                            score = chosenCandidate.boostedScore,
                            capacityPenalty = accessPenaltyForNode(
                                eligibleAccess,
                                chosenCandidate.node.id,
                            ),
                        )
                    } else {
                        null
                    },
                    recommendation = if (useBoost) "BOOST" else "DIRECT",
                    target = chosenCandidate.target,
                    directMetrics = chosenCandidate.directMetrics,
                    boostedMetrics = chosenCandidate.boostedMetrics,
                    gainMs = chosenCandidate.gainMs,
                    candidatesTested = candidates.size,
                )
            }.onSuccess { selection ->
                val direct = selection.directMetrics
                val boosted = selection.boostedMetrics
                val gateway = selection.gateway

                if (selection.recommendation == "DIRECT") {
                    _state.value = _state.value.copy(
                        isAutoSelecting = false,
                        selectedGatewayId = null,
                        selectedGatewayRegion = null,
                        selectedRouteApiUrl = null,
                        routeRecommendation = "DIRECT",
                        routeDecisionTransport = selectionTransport,
                        routeDecisionAtEpochMs = System.currentTimeMillis(),
                        routeTargetId = selection.target?.id,
                        routeTargetHost = selection.target?.host,
                        routeTargetPort = selection.target?.tcpPort,
                        directPingMs = direct?.medianRttMs,
                        directP95Ms = direct?.p95RttMs,
                        directJitterMs = direct?.jitterMs,
                        directPacketLossPct = direct?.packetLossPct,
                        boostedEstimatedPingMs = boosted?.medianRttMs,
                        boostedEstimatedP95Ms = boosted?.p95RttMs,
                        routeGainMs = selection.gainMs,
                        routeCandidatesTested = selection.candidatesTested,
                        pingMs = direct?.medianRttMs,
                        p95PingMs = direct?.p95RttMs,
                        jitterMs = direct?.jitterMs,
                        packetLossPct = direct?.packetLossPct,
                        serverLabel = "DIRECT лучше · VPN не нужен",
                        showAdvancedSettings = false,
                        probeError = null,
                    )
                    debugLog(
                        "Route Intelligence: DIRECT; direct=${direct?.medianRttMs}ms, " +
                            "bestBoost=${boosted?.medianRttMs}ms",
                    )
                    return@onSuccess
                }

                if (gateway == null) {
                    error("Route selection returned no gateway")
                }

                _state.value = _state.value.copy(
                    isAutoSelecting = false,
                    gatewayHost = gateway.node.host,
                    gatewayPort = gateway.node.udpPort,
                    wireGuardServerPublicKey = gateway.node.wireGuardPublicKey
                        ?: _state.value.wireGuardServerPublicKey,
                    wireGuardPort = gateway.node.wireGuardPort
                        ?: _state.value.wireGuardPort,
                    selectedGatewayId = gateway.node.id,
                    selectedGatewayRegion = gateway.node.region,
                    selectedRouteApiUrl = gateway.node.routeApiUrl,
                    routeRecommendation = selection.recommendation,
                    routeDecisionTransport = selectionTransport,
                    routeDecisionAtEpochMs = System.currentTimeMillis(),
                    routeTargetId = selection.target?.id,
                    routeTargetHost = selection.target?.host,
                    routeTargetPort = selection.target?.tcpPort,
                    directPingMs = direct?.medianRttMs,
                    directP95Ms = direct?.p95RttMs,
                    directJitterMs = direct?.jitterMs,
                    directPacketLossPct = direct?.packetLossPct,
                    boostedEstimatedPingMs = boosted?.medianRttMs,
                    boostedEstimatedP95Ms = boosted?.p95RttMs,
                    routeGainMs = selection.gainMs,
                    routeCandidatesTested = selection.candidatesTested,
                    pingMs = boosted?.medianRttMs ?: gateway.metrics.medianRttMs,
                    p95PingMs = boosted?.p95RttMs ?: gateway.metrics.p95RttMs,
                    jitterMs = boosted?.jitterMs ?: gateway.metrics.jitterMs,
                    packetLossPct = boosted?.packetLossPct ?: gateway.metrics.packetLossPct,
                    serverLabel = when (selection.recommendation) {
                        "BOOST" -> {
                            val gain = selection.gainMs
                            if (gain != null && gain > 0) {
                                "BEST ROUTE: ${gateway.node.region} · −${gain} ms"
                            } else {
                                "BEST ROUTE: ${gateway.node.region} · ${gateway.node.id}"
                            }
                        }
                        else -> "Автовыбор: ${gateway.node.region} · ${gateway.node.id}"
                    },
                    showAdvancedSettings = false,
                    probeError = null,
                )
                persistProfile()
                _state.value.selectedApp?.packageName?.let { selectedPackage ->
                    routeMemoryStore.save(
                        packageName = selectedPackage,
                        boostMode = _state.value.boostMode,
                        networkTransport = selectionTransport,
                        gatewayId = gateway.node.id,
                        gainMs = selection.gainMs,
                    )
                }
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    isAutoSelecting = false,
                    selectedGatewayId = null,
                    selectedGatewayRegion = null,
                    selectedRouteApiUrl = null,
                    serverLabel = "Автовыбор не удался",
                    probeError = error.message ?: "Неизвестная ошибка",
                )
            }
        }
    }

    fun probeGateway() {
        val current = _state.value
        if (
            current.gatewayHost.isBlank() ||
            current.isProbing ||
            current.isBoosting ||
            current.isTunnelConnecting ||
            current.isAutoSelecting
        ) return
        if (
            current.autoSelectBestNode &&
            current.routeRecommendation != "GATEWAY_ONLY"
        ) {
            _state.value = current.copy(
                probeError = "Для Auto-режима используй «Лучший маршрут»",
            )
            return
        }

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
                    p95PingMs = metrics.p95RttMs,
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
                    p95PingMs = null,
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
        if (snapshot.autoSelectBestNode && snapshot.routeRecommendation == "UNKNOWN") {
            _state.value = snapshot.copy(
                tunnelError = "Сначала нажми «Лучший маршрут»",
            )
            return
        }

        val connectReadiness = runCatching { gameBoostEngine.inspect() }.getOrNull()
        if (connectReadiness?.vpnActive == true) {
            _state.value = snapshot.copy(
                externalVpnDetected = true,
                tunnelError = "Отключи другой VPN перед запуском BOOSTLAB",
            )
            return
        }

        if (snapshot.autoSelectBestNode) {
            val decidedAt = snapshot.routeDecisionAtEpochMs
            if (
                decidedAt == null ||
                System.currentTimeMillis() - decidedAt !in 0L..MAX_ROUTE_DECISION_AGE_MS
            ) {
                invalidateRouteIntelligence()
                _state.value = _state.value.copy(
                    tunnelError = "Маршрут устарел — запусти Auto-проверку ещё раз",
                    serverLabel = "Нужна новая проверка маршрута",
                )
                return
            }

            val currentTransport = connectReadiness?.networkTransport
            val routeTransport = snapshot.routeDecisionTransport
            if (
                currentTransport != null &&
                routeTransport != null &&
                currentTransport != routeTransport
            ) {
                invalidateRouteIntelligence()
                _state.value = _state.value.copy(
                    networkTransport = currentTransport,
                    tunnelError = "Сеть изменилась: $routeTransport → $currentTransport. Пересчитай маршрут.",
                    serverLabel = "Нужна новая проверка маршрута",
                )
                return
            }
        }
        if (snapshot.routeRecommendation == "DIRECT" && snapshot.autoSelectBestNode) {
            _state.value = snapshot.copy(
                tunnelError = "Прямой маршрут быстрее — Network Boost не нужен",
            )
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

        val selectedTunnelMtu = TunnelMtuPolicy.choose(
            linkMtu = connectReadiness?.networkMtu ?: snapshot.networkMtu,
            networkTransport = connectReadiness?.networkTransport ?: snapshot.networkTransport,
        )

        _state.value = snapshot.copy(
            isTunnelConnecting = true,
            isPeerProvisioning = false,
            peerProvisionError = null,
            tunnelError = null,
            routeHealth = "CONNECTING",
            routeProbeFailures = 0,
            networkMtu = connectReadiness?.networkMtu ?: snapshot.networkMtu,
            tunnelMtu = selectedTunnelMtu,
        )

        viewModelScope.launch {
            runCatching {
                val identity = identityStore.loadOrCreate()
                var tunnelAddress = snapshot.tunnelAddress

                val managedGateway = (
                    !snapshot.selectedGatewayId.isNullOrBlank() &&
                        !snapshot.selectedRouteApiUrl.isNullOrBlank() &&
                        snapshot.controlPlaneUrl.startsWith("https://")
                    )

                if (managedGateway) {
                    _state.value = _state.value.copy(
                        isPeerProvisioning = true,
                        peerProvisionError = null,
                        serverLabel = "Регистрируем устройство на Gateway…",
                    )

                    val registration = try {
                        provisionPeerForSelectedGateway(
                            snapshot = snapshot,
                            wireGuardPublicKey = identity.publicKeyBase64,
                        )
                    } catch (error: Exception) {
                        _state.value = _state.value.copy(
                            isPeerProvisioning = false,
                            peerProvisionError =
                                error.message ?: "Автоматическая регистрация peer недоступна",
                            serverLabel = "Gateway не подтвердил регистрацию устройства",
                        )
                        debugLog(
                            "Peer provisioning blocked connect: ${error::class.java.simpleName}",
                        )
                        throw error
                    }

                    tunnelAddress = registration
                    _state.value = _state.value.copy(
                        isPeerProvisioning = false,
                        peerProvisionError = null,
                        tunnelAddress = registration,
                        serverLabel = "Устройство зарегистрировано · подключаем WireGuard…",
                    )
                    persistProfile()
                }

                val profile = TunnelProfile(
                    privateKey = identity.privateKeyBase64,
                    serverPublicKey = snapshot.wireGuardServerPublicKey,
                    endpointHost = snapshot.gatewayHost,
                    endpointPort = snapshot.wireGuardPort,
                    addressCidr = tunnelAddress,
                    dnsServer = activeDnsValue(snapshot),
                    selectedPackage = selectedApp.packageName,
                    mtu = selectedTunnelMtu,
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
                    _state.value = _state.value.copy(
                        boostStartedAtEpochMs = _state.value.boostStartedAtEpochMs ?: System.currentTimeMillis(),
                    )
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
                    val history = recordBoostSessionIfNeeded()
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
                        boostStartedAtEpochMs = null,
                        boostSessionCount = history?.sessionCount ?: _state.value.boostSessionCount,
                        totalBoostSeconds = history?.totalBoostSeconds ?: _state.value.totalBoostSeconds,
                        lastBoostSeconds = history?.lastBoostSeconds ?: _state.value.lastBoostSeconds,
                        lastBoostPingMs = history?.lastPingMs ?: _state.value.lastBoostPingMs,
                        lastBoostJitterMs = history?.lastJitterMs ?: _state.value.lastBoostJitterMs,
                        lastBoostPacketLossPct = history?.lastPacketLossPct ?: _state.value.lastBoostPacketLossPct,
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
                boostStartedAtEpochMs = System.currentTimeMillis(),
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

        if (saved.controlPlaneUrl.startsWith("https://") && _state.value.autoSelectBestNode) {
            autoSelectGateway()
        } else if (saved.gatewayHost.isNotBlank()) {
            probeGateway()
        }
    }

    private fun startLiveMetrics() {
        stopLiveMetrics()
        liveMetricsJob = viewModelScope.launch {
            var consecutiveProbeFailures = 0
            var consecutiveDirectWins = 0
            var intelligenceCycle = 0
            var cachedGatewayToGame: com.boostlab.app.network.RouteMetrics? = null
            var cachedDirectToGame: com.boostlab.app.network.RouteMetrics? = null

            while (_state.value.isBoosting) {
                val snapshot = _state.value
                if (snapshot.gatewayHost.isBlank()) break

                val liveReadiness = runCatching { gameBoostEngine.inspect() }.getOrNull()
                val liveTransport = liveReadiness?.networkTransport
                val liveRecommendedMtu = TunnelMtuPolicy.choose(
                    linkMtu = liveReadiness?.networkMtu,
                    networkTransport = liveTransport,
                )
                val physicalNetworkChanged = (
                    !snapshot.routeDecisionTransport.isNullOrBlank() &&
                        !liveTransport.isNullOrBlank() &&
                        snapshot.routeDecisionTransport != liveTransport
                    )
                val mtuChanged = (
                    liveReadiness?.networkMtu != null &&
                        abs(liveRecommendedMtu - snapshot.tunnelMtu) >= LIVE_MTU_DRIFT_THRESHOLD
                    )

                when (runCatching { tunnelController.state() }.getOrNull()) {
                    Tunnel.State.DOWN -> {
                        val history = recordBoostSessionIfNeeded()
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
                            boostStartedAtEpochMs = null,
                            boostSessionCount = history?.sessionCount ?: _state.value.boostSessionCount,
                            totalBoostSeconds = history?.totalBoostSeconds ?: _state.value.totalBoostSeconds,
                            lastBoostSeconds = history?.lastBoostSeconds ?: _state.value.lastBoostSeconds,
                            lastBoostPingMs = history?.lastPingMs ?: _state.value.lastBoostPingMs,
                            lastBoostJitterMs = history?.lastJitterMs ?: _state.value.lastBoostJitterMs,
                            lastBoostPacketLossPct = history?.lastPacketLossPct ?: _state.value.lastBoostPacketLossPct,
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

                val accessResult = runCatching {
                    routeProbe.measure(
                        host = snapshot.gatewayHost,
                        port = snapshot.gatewayPort,
                        samples = LIVE_METRICS_SAMPLES,
                    )
                }
                val accessMetrics = accessResult.getOrNull()

                if (accessMetrics != null) {
                    consecutiveProbeFailures =
                        if (accessMetrics.received > 0) 0 else consecutiveProbeFailures + 1

                    val intelligenceReady =
                        snapshot.routeRecommendation == "BOOST" &&
                            !snapshot.selectedRouteApiUrl.isNullOrBlank() &&
                            !snapshot.routeTargetId.isNullOrBlank() &&
                            !snapshot.routeTargetHost.isNullOrBlank() &&
                            snapshot.routeTargetPort != null

                    var routeMetricsRefreshed = false
                    if (
                        intelligenceReady &&
                        (
                            intelligenceCycle % LIVE_ROUTE_REFRESH_CYCLES == 0 ||
                                cachedGatewayToGame == null ||
                                cachedDirectToGame == null
                            )
                    ) {
                        val freshGatewayToGame = runCatching {
                            gatewayRouteQuality.fetch(
                                routeApiUrl = requireNotNull(snapshot.selectedRouteApiUrl),
                                target = com.boostlab.app.network.GameRouteTarget(
                                    id = requireNotNull(snapshot.routeTargetId),
                                    host = requireNotNull(snapshot.routeTargetHost),
                                    tcpPort = requireNotNull(snapshot.routeTargetPort),
                                ),
                            ).metrics
                        }.getOrNull()

                        val freshDirectToGame = runCatching {
                            directRouteProbe.measure(
                                host = requireNotNull(snapshot.routeTargetHost),
                                port = requireNotNull(snapshot.routeTargetPort),
                                samples = LIVE_DIRECT_ROUTE_SAMPLES,
                            )
                        }.getOrNull()

                        if (freshGatewayToGame != null && freshDirectToGame != null) {
                            cachedGatewayToGame = freshGatewayToGame
                            cachedDirectToGame = freshDirectToGame
                            routeMetricsRefreshed = true
                        }
                    }

                    intelligenceCycle += 1

                    val endToEndMetrics = cachedGatewayToGame?.let { gameLeg ->
                        RouteIntelligence.combine(accessMetrics, gameLeg)
                    } ?: accessMetrics
                    val directMetrics = cachedDirectToGame
                    val gainMs = if (
                        directMetrics?.medianRttMs != null &&
                        endToEndMetrics.medianRttMs != null
                    ) {
                        directMetrics.medianRttMs - endToEndMetrics.medianRttMs
                    } else {
                        snapshot.routeGainMs
                    }

                    if (routeMetricsRefreshed && intelligenceReady) {
                        consecutiveDirectWins = if (
                            gainMs != null &&
                            gainMs <= -ROUTE_REGRESSION_MS
                        ) {
                            consecutiveDirectWins + 1
                        } else {
                            0
                        }
                    }

                    if (_state.value.isBoosting) {
                        val verified = _state.value.gameTrafficVerified || trafficVerifiedNow
                        _state.value = _state.value.copy(
                            pingMs = endToEndMetrics.medianRttMs,
                            p95PingMs = endToEndMetrics.p95RttMs,
                            jitterMs = endToEndMetrics.jitterMs,
                            packetLossPct = endToEndMetrics.packetLossPct,
                            directPingMs = directMetrics?.medianRttMs ?: _state.value.directPingMs,
                            directP95Ms = directMetrics?.p95RttMs ?: _state.value.directP95Ms,
                            directJitterMs = directMetrics?.jitterMs ?: _state.value.directJitterMs,
                            directPacketLossPct = directMetrics?.packetLossPct
                                ?: _state.value.directPacketLossPct,
                            boostedEstimatedPingMs = endToEndMetrics.medianRttMs,
                            boostedEstimatedP95Ms = endToEndMetrics.p95RttMs,
                            routeGainMs = gainMs,
                            routeProbeFailures = consecutiveProbeFailures,
                            networkTransport = liveTransport ?: _state.value.networkTransport,
                            networkMtu = liveReadiness?.networkMtu ?: _state.value.networkMtu,
                            routeHealth = when {
                                physicalNetworkChanged || mtuChanged -> "NETWORK_CHANGED"
                                consecutiveProbeFailures >= MAX_LIVE_PROBE_FAILURES -> "DEGRADED"
                                consecutiveDirectWins >= MAX_DIRECT_WIN_CYCLES -> "DIRECT_BETTER"
                                verified && cachedGatewayToGame != null -> "GAME_ROUTE"
                                verified -> "TRAFFIC"
                                else -> "CONNECTED"
                            },
                            serverLabel = when {
                                physicalNetworkChanged ->
                                    "Сеть изменилась: ${snapshot.routeDecisionTransport} → $liveTransport · переподключи Boost"
                                mtuChanged ->
                                    "MTU сети изменился · переподключи Boost"
                                consecutiveDirectWins >= MAX_DIRECT_WIN_CYCLES &&
                                    gainMs != null &&
                                    gainMs < 0 ->
                                    "DIRECT теперь быстрее · BOOST +${-gainMs} ms"
                                gainMs != null &&
                                    gainMs > 0 &&
                                    snapshot.routeRecommendation == "BOOST" ->
                                    "BEST ROUTE: ${snapshot.selectedGatewayRegion ?: "Gateway"} · −$gainMs ms"
                                else -> _state.value.serverLabel
                            },
                        )
                    }
                } else {
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

    private suspend fun loadVoiceIceServers(baseUrl: String): List<VoiceIceServer> {
        return runCatching {
            squadApi.fetchVoiceIce(baseUrl, _state.value.localUserId).map { remote ->
                VoiceIceServer(
                    urls = remote.urls,
                    username = remote.username,
                    credential = remote.credential,
                )
            }
        }.getOrElse {
            listOf(
                VoiceIceServer(
                    urls = listOf(
                        "stun:stun.l.google.com:19302",
                        "stun:stun1.l.google.com:19302",
                    ),
                ),
            )
        }
    }

    private fun handleIncomingVoiceEvent(sender: String, type: String, payload: String) {
        if (sender == _state.value.localUserId) return

        val target = runCatching { JSONObject(payload).optString("target") }.getOrDefault("")
        if (target.isNotBlank() && target != _state.value.localUserId) return

        when (type) {
            "voice_offer" -> {
                if (_state.value.voiceCallState in setOf("IDLE", "FAILED")) {
                    pendingVoiceOfferSender = sender
                    pendingVoiceOfferPayload = payload
                    _state.value = _state.value.copy(
                        voiceCallState = "RINGING",
                        voicePeerId = sender,
                        voiceMuted = false,
                        voiceError = null,
                    )
                    addEvent("Voice", "Входящий звонок от $sender")
                    _state.value.squadCode?.let { code ->
                        notificationCenter.notifyIncomingCall(code, sender)
                    }
                }
            }
            "voice_hangup" -> {
                if (_state.value.voiceCallState == "RINGING" && pendingVoiceOfferSender == sender) {
                    pendingVoiceOfferSender = null
                    pendingVoiceOfferPayload = null
                    pendingIncomingVoiceIce.clear()
                    notificationCenter.cancelIncomingCall()
                    _state.value = _state.value.copy(
                        voiceCallState = "IDLE",
                        voicePeerId = null,
                        voiceMuted = false,
                        voiceError = null,
                    )
                } else {
                    voiceController.handleSignal(sender, type, payload)
                }
            }
            "voice_ice" -> {
                if (
                    _state.value.voiceCallState == "RINGING" && pendingVoiceOfferSender == sender ||
                    _state.value.voiceCallState in setOf("IDLE", "FAILED")
                ) {
                    pendingIncomingVoiceIce += sender to payload
                    if (pendingIncomingVoiceIce.size > MAX_PENDING_VOICE_ICE) {
                        pendingIncomingVoiceIce.removeAt(0)
                    }
                } else {
                    voiceController.handleSignal(sender, type, payload)
                }
            }
            "voice_answer" -> voiceController.handleSignal(sender, type, payload)
        }
    }

    private fun startDirectSync() {
        directSyncJob?.cancel()
        directSyncJob = null

        val peer = _state.value.directPeerId ?: return
        directSyncJob = viewModelScope.launch {
            while (_state.value.directPeerId == peer) {
                val snapshot = _state.value
                val baseUrl = snapshot.controlPlaneUrl
                if (!baseUrl.startsWith("https://")) {
                    _state.value = snapshot.copy(
                        directSyncError = "Укажи Control API HTTPS, чтобы включить личный чат",
                    )
                    delay(DIRECT_SYNC_INTERVAL_MS)
                    continue
                }

                runCatching {
                    squadApi.fetchDirectEvents(
                        baseUrl = baseUrl,
                        self = snapshot.localUserId,
                        peer = peer,
                        after = directLastEventId,
                    )
                }.onSuccess { events ->
                    if (events.isNotEmpty()) {
                        directLastEventId = maxOf(directLastEventId, events.maxOf { it.id })
                    }
                    val chats = events
                        .filter { it.type == "chat" && it.text.isNotBlank() }
                        .map {
                            SquadChatMessage(
                                id = it.id,
                                sender = it.sender,
                                text = it.text,
                                createdAt = it.createdAt,
                            )
                        }
                    _state.value = _state.value.copy(
                        directMessages = (_state.value.directMessages + chats)
                            .distinctBy { it.id }
                            .sortedBy { it.id }
                            .takeLast(MAX_DIRECT_MESSAGES),
                        directSyncError = null,
                    )
                }.onFailure { error ->
                    _state.value = _state.value.copy(
                        directSyncError = error.message ?: "Ошибка синхронизации личного чата",
                    )
                }

                delay(DIRECT_SYNC_INTERVAL_MS)
            }
        }
    }

    private fun startSquadSync(resetCursor: Boolean = false) {
        squadSyncJob?.cancel()
        squadSyncJob = null

        val code = _state.value.squadCode ?: return
        if (resetCursor) {
            squadLastEventId = 0L
            squadInitialSyncDone = false
            _state.value = _state.value.copy(
                squadMessages = emptyList(),
                squadOnlineUsers = emptyList(),
                squadSyncError = null,
            )
        }

        squadSyncJob = viewModelScope.launch {
            while (_state.value.squadCode == code) {
                val snapshot = _state.value
                val baseUrl = snapshot.controlPlaneUrl

                if (!baseUrl.startsWith("https://")) {
                    _state.value = snapshot.copy(
                        squadSyncError = "Укажи Control API HTTPS, чтобы включить сетевой чат",
                    )
                    delay(SQUAD_SYNC_INTERVAL_MS)
                    continue
                }

                runCatching {
                    squadApi.touchPresence(baseUrl, code, snapshot.localUserId)
                    val events = squadApi.fetchEvents(baseUrl, code, squadLastEventId)
                    val presence = squadApi.fetchPresence(baseUrl, code)
                    events to presence
                }.onSuccess { (events, presence) ->
                    if (events.isNotEmpty()) {
                        squadLastEventId = maxOf(squadLastEventId, events.maxOf { it.id })
                    }
                    events
                        .filter { it.type.startsWith("voice_") }
                        .forEach { handleIncomingVoiceEvent(it.sender, it.type, it.payload) }

                    val chats = events
                        .filter { it.type == "chat" && it.text.isNotBlank() }
                        .map {
                            SquadChatMessage(
                                id = it.id,
                                sender = it.sender,
                                text = it.text,
                                createdAt = it.createdAt,
                            )
                        }

                    if (squadInitialSyncDone) {
                        chats
                            .filter { it.sender != _state.value.localUserId }
                            .forEach { chat ->
                                notificationCenter.notifyMessage(
                                    squadCode = code,
                                    sender = chat.sender,
                                    text = chat.text,
                                )
                            }
                    }

                    _state.value = _state.value.copy(
                        squadMessages = (_state.value.squadMessages + chats)
                            .distinctBy { it.id }
                            .sortedBy { it.id }
                            .takeLast(MAX_SQUAD_MESSAGES),
                        squadOnlineUsers = presence.map { it.userId }.distinct().sorted(),
                        squadSyncError = null,
                    )
                    squadInitialSyncDone = true
                }.onFailure { error ->
                    _state.value = _state.value.copy(
                        squadSyncError = error.message ?: "Ошибка синхронизации отряда",
                    )
                }

                delay(
                    if (_state.value.voiceCallState in setOf("CALLING", "RINGING", "CONNECTING", "RECONNECTING")) {
                        SQUAD_VOICE_SYNC_INTERVAL_MS
                    } else {
                        SQUAD_SYNC_INTERVAL_MS
                    },
                )
            }
        }
    }

    private fun invalidateRouteIntelligence(clearSelectedGateway: Boolean = false) {
        val snapshot = _state.value
        _state.value = snapshot.copy(
            routeRecommendation = "UNKNOWN",
            routeTargetId = null,
            routeTargetHost = null,
            routeTargetPort = null,
            directPingMs = null,
            directP95Ms = null,
            directJitterMs = null,
            directPacketLossPct = null,
            boostedEstimatedPingMs = null,
            boostedEstimatedP95Ms = null,
            routeGainMs = null,
            routeCandidatesTested = 0,
            p95PingMs = null,
            selectedGatewayId = if (clearSelectedGateway) null else snapshot.selectedGatewayId,
            selectedGatewayRegion = if (clearSelectedGateway) null else snapshot.selectedGatewayRegion,
            selectedRouteApiUrl = if (clearSelectedGateway) null else snapshot.selectedRouteApiUrl,
            tunnelError = null,
        )
    }

    private fun accessPenaltyForNode(
        measurements: List<GatewayMeasurement>,
        nodeId: String,
    ): Double {
        return measurements.firstOrNull { it.node.id == nodeId }?.capacityPenalty ?: 0.0
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
        val p95 = metrics.p95RttMs ?: rtt
        val tail = (p95 - rtt).coerceAtLeast(0)
        return when (mode) {
            "LOW_PING" ->
                rtt.toDouble() + (jitter * 0.8) + (tail * 0.5) + (metrics.packetLossPct * 18.0)
            "STABLE" ->
                (rtt * 0.65) + (jitter * 3.2) + (tail * 1.8) + (metrics.packetLossPct * 35.0)
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

    private fun addEvent(title: String, message: String) {
        eventStore.append(title, message)
        _state.value = _state.value.copy(appEvents = eventStore.load())
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

    private suspend fun ensureDeviceSession(baseUrl: String): Pair<String, String> {
        val now = System.currentTimeMillis()
        val cachedToken = deviceAccessToken
        val cachedDeviceId = authenticatedDeviceId
        if (
            !cachedToken.isNullOrBlank() &&
            !cachedDeviceId.isNullOrBlank() &&
            deviceAccessTokenExpiresAtEpochMs - now > DEVICE_SESSION_REFRESH_MARGIN_MS
        ) {
            return cachedDeviceId to cachedToken
        }

        val publicKey = deviceAuthStore.publicKeyBase64()
        val challenge = controlPlane.requestDeviceChallenge(
            baseUrl = baseUrl,
            publicKeyBase64 = publicKey,
            enrollmentCode = _state.value.provisioningEnrollmentCode,
        )
        val signature = deviceAuthStore.sign(challenge.message)
        val session = controlPlane.exchangeDeviceSession(
            baseUrl = baseUrl,
            challengeId = challenge.challengeId,
            signatureBase64 = signature,
        )

        authenticatedDeviceId = session.deviceId
        deviceAccessToken = session.accessToken
        deviceAccessTokenExpiresAtEpochMs = session.expiresAtEpochMs
        _state.value = _state.value.copy(
            deviceAuthId = session.deviceId,
            provisioningEnrollmentCode = "",
        )
        persistProfile()
        return session.deviceId to session.accessToken
    }

    private suspend fun provisionPeerForSelectedGateway(
        snapshot: BoostState,
        wireGuardPublicKey: String,
    ): String {
        val nodeId = snapshot.selectedGatewayId
            ?.takeIf { it.isNotBlank() }
            ?: error("Gateway node id is unavailable")
        val (deviceId, accessToken) = ensureDeviceSession(snapshot.controlPlaneUrl)
        val ticket = controlPlane.requestGatewayProvisionTicket(
            baseUrl = snapshot.controlPlaneUrl,
            nodeId = nodeId,
            accessToken = accessToken,
            wireGuardPublicKey = wireGuardPublicKey,
        )
        require(ticket.deviceId == deviceId) {
            "Provisioning device identity mismatch"
        }

        val registration = gatewayProvisionClient.registerPeer(
            gatewayUrl = ticket.gatewayUrl,
            ticket = ticket.ticket,
        )
        require(registration.deviceId == deviceId) {
            "Gateway returned another device identity"
        }
        require(registration.tunnelAddress.endsWith("/32")) {
            "Gateway returned invalid tunnel address"
        }
        return registration.tunnelAddress
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
                provisioningEnrollmentCode = snapshot.provisioningEnrollmentCode,
            ),
        )
    }

    override fun onCleared() {
        squadSyncJob?.cancel()
        directSyncJob?.cancel()
        liveMetricsJob?.cancel()
        voiceController.release()
        super.onCleared()
    }

    companion object {
        private const val MAX_AUTO_NODES = 12
        private const val AUTO_FINALISTS = 3
        private const val AUTO_QUICK_SAMPLES = 3
        private const val AUTO_FINAL_SAMPLES = 7
        private const val MAX_DIRECTORY_NODES = 64
        private const val DIRECTORY_SAMPLES = 3
        private const val LAN_AUTO_SAMPLES = 4
        private const val LIVE_METRICS_SAMPLES = 4
        private const val LIVE_METRICS_INTERVAL_MS = 5_000L
        private const val TRAFFIC_VERIFY_MIN_BYTES = 1_024L
        private const val MAX_LIVE_PROBE_FAILURES = 3
        private const val SQUAD_SYNC_INTERVAL_MS = 3_000L
        private const val SQUAD_VOICE_SYNC_INTERVAL_MS = 750L
        private const val MAX_SQUAD_MESSAGES = 100
        private const val DIRECT_SYNC_INTERVAL_MS = 3_000L
        private const val MAX_DIRECT_MESSAGES = 100
        private const val MAX_PENDING_VOICE_ICE = 64
        private const val DIRECT_ROUTE_SAMPLES = 7
        private const val LIVE_DIRECT_ROUTE_SAMPLES = 3
        private const val LIVE_ROUTE_REFRESH_CYCLES = 2
        private const val MAX_ROUTE_DECISION_AGE_MS = 2L * 60L * 1000L
        private const val ROUTE_REGRESSION_MS = 10
        private const val MAX_DIRECT_WIN_CYCLES = 3
        private const val DEVICE_SESSION_REFRESH_MARGIN_MS = 30_000L
        private const val LIVE_MTU_DRIFT_THRESHOLD = 40
    }
}
