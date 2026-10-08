package com.boostlab.app.tunnel

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.crypto.Key
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class TunnelTraffic(
    val rxBytes: Long,
    val txBytes: Long,
    val latestHandshakeEpochMillis: Long,
)

class WireGuardTunnelController(context: Context) {
    private val backend by lazy { GoBackend(context.applicationContext) }
    private val tunnel = BoostTunnel()

    suspend fun connect(profile: TunnelProfile): Tunnel.State {
        val startedAt = System.currentTimeMillis()
        val config = WireGuardConfigFactory.build(profile)

        val state = withContext(Dispatchers.IO) {
            backend.setState(tunnel, Tunnel.State.UP, config)
        }

        if (state != Tunnel.State.UP) {
            return state
        }

        try {
            awaitFreshHandshake(
                serverPublicKey = profile.serverPublicKey,
                startedAtEpochMillis = startedAt,
            )
        } catch (error: Exception) {
            runCatching { disconnect() }
            throw error
        }

        return Tunnel.State.UP
    }

    suspend fun disconnect(): Tunnel.State = withContext(Dispatchers.IO) {
        backend.setState(tunnel, Tunnel.State.DOWN, null)
    }

    suspend fun disconnectVerified(): Tunnel.State {
        withContext(Dispatchers.IO) {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
        }

        repeat(DISCONNECT_VERIFY_ATTEMPTS) { attempt ->
            val current = state()
            if (current == Tunnel.State.DOWN) {
                return Tunnel.State.DOWN
            }

            if (attempt + 1 < DISCONNECT_VERIFY_ATTEMPTS) {
                delay(DISCONNECT_VERIFY_POLL_MS)
                withContext(Dispatchers.IO) {
                    backend.setState(tunnel, Tunnel.State.DOWN, null)
                }
            }
        }

        throw IllegalStateException(
            "WireGuard did not confirm DOWN state; system route restoration is not verified",
        )
    }

    suspend fun state(): Tunnel.State = withContext(Dispatchers.IO) {
        backend.getState(tunnel)
    }

    suspend fun traffic(serverPublicKey: String): TunnelTraffic? = withContext(Dispatchers.IO) {
        val peerKey = Key.fromBase64(serverPublicKey)
        backend.getStatistics(tunnel).peer(peerKey)?.let { peerStats ->
            TunnelTraffic(
                rxBytes = peerStats.rxBytes(),
                txBytes = peerStats.txBytes(),
                latestHandshakeEpochMillis = peerStats.latestHandshakeEpochMillis(),
            )
        }
    }

    private suspend fun awaitFreshHandshake(
        serverPublicKey: String,
        startedAtEpochMillis: Long,
    ) {
        val peerKey = Key.fromBase64(serverPublicKey)
        val minimumHandshakeTime = startedAtEpochMillis - CLOCK_SKEW_TOLERANCE_MS

        repeat(HANDSHAKE_ATTEMPTS) {
            val peerStats = withContext(Dispatchers.IO) {
                backend.getStatistics(tunnel).peer(peerKey)
            }

            if (
                peerStats != null &&
                peerStats.latestHandshakeEpochMillis() >= minimumHandshakeTime
            ) {
                return
            }

            delay(HANDSHAKE_POLL_MS)
        }

        throw IllegalStateException(
            "WireGuard handshake failed; tunnel was closed to preserve normal connectivity",
        )
    }

    companion object {
        private const val HANDSHAKE_ATTEMPTS = 16
        private const val HANDSHAKE_POLL_MS = 500L
        private const val CLOCK_SKEW_TOLERANCE_MS = 2_000L
        private const val DISCONNECT_VERIFY_ATTEMPTS = 6
        private const val DISCONNECT_VERIFY_POLL_MS = 250L
    }
}
