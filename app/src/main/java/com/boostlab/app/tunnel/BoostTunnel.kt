package com.boostlab.app.tunnel

import com.wireguard.android.backend.Tunnel

class BoostTunnel : Tunnel {
    @Volatile
    var state: Tunnel.State = Tunnel.State.DOWN
        private set

    override fun getName(): String = "boostlab"

    override fun onStateChange(newState: Tunnel.State) {
        state = newState
    }
}
