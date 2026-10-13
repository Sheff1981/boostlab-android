package com.boostlab.app

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log

/**
 * Android VPN permission and lifecycle entry point.
 *
 * No tunnel is established until a real WireGuard engine and provisioned
 * credentials are integrated. Never call Builder.establish() with a fake
 * interface or report a successful connection without a handshake.
 */
class BoostVpnService : VpnService() {
    private var tunnel: ParcelFileDescriptor? = null

    override fun onRevoke() {
        closeTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        closeTunnel()
        super.onDestroy()
    }

    private fun closeTunnel() {
        try {
            tunnel?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close VPN descriptor", e)
        } finally {
            tunnel = null
        }
    }

    companion object {
        private const val TAG = "BoostVpnService"
    }
}
