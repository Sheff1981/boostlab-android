package com.boostlab.app.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.boostlab.app.data.InstalledAppsRepository
import com.boostlab.app.model.BoostApp
import com.boostlab.app.model.BoostState
import com.boostlab.app.vpn.BoosterVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BoostViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = InstalledAppsRepository(appContext)

    private val _state = MutableStateFlow(BoostState())
    val state: StateFlow<BoostState> = _state.asStateFlow()

    val apps: List<BoostApp> = repository.loadLaunchableApps()

    fun selectApp(app: BoostApp) {
        _state.value = _state.value.copy(selectedApp = app)
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
