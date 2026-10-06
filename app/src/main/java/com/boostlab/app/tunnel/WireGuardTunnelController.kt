package com.boostlab.app.tunnel

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WireGuardTunnelController(context: Context) {
    private val backend by lazy { GoBackend(context.applicationContext) }
    private val tunnel = BoostTunnel()

    suspend fun connect(profile: TunnelProfile): Tunnel.State = withContext(Dispatchers.IO) {
        val config = WireGuardConfigFactory.build(profile)
        backend.setState(tunnel, Tunnel.State.UP, config)
    }

    suspend fun disconnect(): Tunnel.State = withContext(Dispatchers.IO) {
        backend.setState(tunnel, Tunnel.State.DOWN, null)
    }

    suspend fun state(): Tunnel.State = withContext(Dispatchers.IO) {
        backend.getState(tunnel)
    }
}
