package com.boostlab.app.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boostlab.app.data.InstalledAppsRepository
import com.boostlab.app.model.BoostApp
import com.boostlab.app.model.BoostState
import com.boostlab.app.network.UdpRouteProbe
import com.boostlab.app.vpn.BoosterVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BoostViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = InstalledAppsRepository(appContext)
    private val routeProbe = UdpRouteProbe()

    private val _state = MutableStateFlow(BoostState())
    val state: StateFlow<BoostState> = _state.asStateFlow()

    val apps: List<BoostApp> = repository.loadLaunchableApps()

    fun selectApp(app: BoostApp) {
        _state.value = _state.value.copy(selectedApp = app)
    }

    fun updateGatewayHost(value: String) {
        _state.value = _state.value.copy(
            gatewayHost = value.trim(),
            probeError = null,
        )
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
}
