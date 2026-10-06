package com.boostlab.app.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boostlab.app.data.InstalledAppsRepository
import com.boostlab.app.model.BoostApp
import com.boostlab.app.model.BoostState
import com.boostlab.app.network.ControlPlaneClient
import com.boostlab.app.network.GatewayMeasurement
import com.boostlab.app.network.RouteScorer
import com.boostlab.app.network.UdpRouteProbe
import com.boostlab.app.vpn.BoosterVpnService
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

    private val _state = MutableStateFlow(BoostState())
    val state: StateFlow<BoostState> = _state.asStateFlow()

    val apps: List<BoostApp> = repository.loadLaunchableApps()

    fun selectApp(app: BoostApp) {
        _state.value = _state.value.copy(selectedApp = app)
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
                val nodes = controlPlane.fetchNodes(_state.value.controlPlaneUrl).take(MAX_AUTO_NODES)
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
                                    score = RouteScorer.score(metrics),
                                )
                            }.getOrNull()
                        }
                    }.awaitAll().filterNotNull()
                }

                measurements
                    .filter { it.metrics.received > 0 && it.score.isFinite() }
                    .minByOrNull { it.score }
                    ?: error("No gateway answered the route probe")
            }.onSuccess { best ->
                _state.value = _state.value.copy(
                    isAutoSelecting = false,
                    gatewayHost = best.node.host,
                    gatewayPort = best.node.udpPort,
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

    fun startBoosterShell() {
        val selected = _state.value.selectedApp ?: return
        val intent = Intent(appContext, BoosterVpnService::class.java).apply {
            action = BoosterVpnService.ACTION_START
            putExtra(BoosterVpnService.EXTRA_PACKAGE_NAME, selected.packageName)
        }

        ContextCompat.startForegroundService(appContext, intent)
        _state.value = _state.value.copy(isBoosting = true)
    }

    fun stopBooster() {
        appContext.startService(
            Intent(appContext, BoosterVpnService::class.java).apply {
                action = BoosterVpnService.ACTION_STOP
            },
        )
        _state.value = _state.value.copy(isBoosting = false)
    }

    companion object {
        private const val MAX_AUTO_NODES = 8
        private const val AUTO_SAMPLES = 6
    }
}
