package com.boostlab.app.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat
import com.boostlab.app.MainActivity

class BoosterVpnService : VpnService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startStageOne(intent.getStringExtra(EXTRA_PACKAGE_NAME))
            ACTION_STOP -> stopSelf()
        }
        return Service.START_NOT_STICKY
    }

    private fun startStageOne(packageName: String?) {
        createNotificationChannel()

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("BOOSTLAB")
            .setContentText("Stage 1 ready: ${packageName ?: "no app selected"}")
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // Stage 1 deliberately does NOT establish a TUN interface.
        // A VPN interface without a packet relay/gateway would black-hole traffic.
        // Stage 2 adds the encrypted gateway before per-app routing is enabled.
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Booster connection",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.boostlab.app.action.START"
        const val ACTION_STOP = "com.boostlab.app.action.STOP"
        const val EXTRA_PACKAGE_NAME = "package_name"

        private const val CHANNEL_ID = "boost_connection"
        private const val NOTIFICATION_ID = 1001
    }
}
