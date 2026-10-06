package com.boostlab.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boostlab.app.data.InstalledAppsRepository
import com.boostlab.app.model.BoostApp
import com.boostlab.app.model.BoostState
import com.boostlab.app.network.ClientPolicy
import com.boostlab.app.network.ControlPlaneClient
import com.boostlab.app.network.GatewayMeasurement
import com.boostlab.app.network.RouteDecisionPolicy
import com.boostlab.app.network.RouteScorer
import com.boostlab.app.network.UdpRouteProbe
import com.boostlab.app.tunnel.ClientIdentityStore
import com.boostlab.app.tunnel.TunnelProfile
import com.boostlab.app.tunnel.WireGuardTunnelController
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BoostViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = InstalledAppsRepository(appContext)
    private val routeProbe = UdpRouteProbe()
    private val controlPlane = ControlPlaneClient()
    private val identityStore = ClientIdentityStore(appContext)
    private val tunnelController = WireGuardTunnelController(appContext)

    private val _state = MutableStateFlow(BoostState())
    val state: StateFlow<BoostState> = _state.asStateFlow()

    val apps: List<BoostApp> = repository.loadLaunchableApps()

    init {
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
    }

    fun selectApp(app: BoostApp) {
        _state.value = _state.value.copy(
            selectedApp = app,
            tunnelError = null,
        )
    }

    fun updateControlPlaneUrl(value: String) {
        _state.value = _state.value.copy(
            controlPlaneUrl = value.trim(),
            probeError = null,
        )
    }

    fun updateGatewayHost(value: String) {
        _state.value = _state.value.copy(
            gatewayHost = value.trim(),
            selectedGatewayId = null,
            selectedGatewayRegion = null,
            probeError = null,
            tunnelError = null,
        )
    }

    fun updateWireGuardServerPublicKey(value: String) {
        _state.value = _state.value.copy(
            wireGuardServerPublicKey = value.trim(),
            tunnelError = null,
        )
    }

    fun updateTunnelAddress(value: String) {
        _state.value = _state.value.copy(
            tunnelAddress = value.trim(),
            tunnelError = null,
        )
    }

    fun updateDnsServer(value: String) {
        _state.value = _state.value.copy(
            dnsServer = value.trim(),
            tunnelError = null,
        )
    }

    fun autoSelectGateway() {
        val current = _state.value
        if (current.controlPlaneUrl.isBlank() || current.isAutoSelecting) return

        _state.value = current.copy(
            isAutoSelecting = true,
            discoveredNodes = 0,
            probeError = null,
            serverLabel = "Получаем список серверов…",
        )

        viewModelScope.launch {
            runCatching {
                val policy = runCatching {
                    controlPlane.fetchClientPolicy(_state.value.controlPlaneUrl)
                }.getOrElse {
                    ClientPolicy.fallbackFree()
                }
                val tier = policy.defaultTier
                val tierPolicy = policy.forTier(tier)
                val candidateLimit = tierPolicy.maxAutoCandidates
                    .coerceAtMost(MAX_AUTO_NODES)

                val nodes = controlPlane.fetchNodes(_state.value.controlPlaneUrl)
                    .take(candidateLimit)
                if (nodes.isEmpty()) {
                    error("Control API returned no healthy gateways")
                }

                _state.value = _state.value.copy(
                    planTier = tier,
                    adsEnabled = tierPolicy.adsEnabled,
                    priorityRouting = tierPolicy.priorityRouting,
                    maxAutoCandidates = candidateLimit,
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
                                    score = RouteScorer.score(metrics),
                                )
                            }.getOrNull()
                        }
                    }.awaitAll().filterNotNull()
                }

                val eligible = measurements
                    .filter { it.metrics.received > 0 && it.score.isFinite() }

                val best = eligible.minByOrNull { it.score }
                    ?: error("No gateway answered the route probe")

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
                    probeError = null,
                )
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
        )

        viewModelScope.launch {
            runCatching {
                val identity = identityStore.loadOrCreate()
                val profile = TunnelProfile(
                    privateKey = identity.privateKeyBase64,
                    serverPublicKey = _state.value.wireGuardServerPublicKey,
                    endpointHost = _state.value.gatewayHost,
                    endpointPort = _state.value.wireGuardPort,
                    addressCidr = _state.value.tunnelAddress,
                    dnsServer = _state.value.dnsServer,
                    selectedPackage = selectedApp.packageName,
                )
                tunnelController.connect(profile)
            }.onSuccess { tunnelState ->
                _state.value = _state.value.copy(
                    isTunnelConnecting = false,
                    isBoosting = tunnelState == Tunnel.State.UP,
                    tunnelError = if (tunnelState == Tunnel.State.UP) {
                        null
                    } else {
                        "WireGuard tunnel did not reach UP state"
                    },
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    isTunnelConnecting = false,
                    isBoosting = false,
                    tunnelError = "WireGuard connect failed: ${error::class.java.simpleName}",
                )
            }
        }
    }

    fun disconnectTunnel() {
        if (_state.value.isTunnelConnecting) return

        _state.value = _state.value.copy(
            isTunnelConnecting = true,
            tunnelError = null,
        )

        viewModelScope.launch {
            runCatching { tunnelController.disconnect() }
                .onSuccess {
                    _state.value = _state.value.copy(
                        isTunnelConnecting = false,
                        isBoosting = false,
                        tunnelError = null,
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isTunnelConnecting = false,
                        tunnelError = "WireGuard disconnect failed: ${error::class.java.simpleName}",
                    )
                }
        }
    }

    companion object {
        private const val MAX_AUTO_NODES = 8
        private const val AUTO_SAMPLES = 6
    }
}
