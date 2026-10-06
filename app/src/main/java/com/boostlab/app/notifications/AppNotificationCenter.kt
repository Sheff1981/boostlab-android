package com.boostlab.app.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.boostlab.app.MainActivity

class AppNotificationCenter(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(
                MESSAGE_CHANNEL_ID,
                "Сообщения отряда",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CALL_CHANNEL_ID,
                "Голосовые звонки",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Входящие голосовые звонки BOOSTLAB"
            },
        )
    }

    fun notifyMessage(squadCode: String, sender: String, text: String) {
        if (!canNotify()) return
        val notification = baseBuilder(
            channelId = MESSAGE_CHANNEL_ID,
            squadCode = squadCode,
            title = sender,
            text = text,
        )
            .setCategory(Notification.CATEGORY_MESSAGE)
            .build()
        manager.notify(messageNotificationId(squadCode, sender), notification)
    }

    fun notifyIncomingCall(squadCode: String, sender: String) {
        if (!canNotify()) return
        val notification = baseBuilder(
            channelId = CALL_CHANNEL_ID,
            squadCode = squadCode,
            title = "Входящий звонок BOOSTLAB",
            text = "Участник $sender звонит в отряд $squadCode",
        )
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(false)
            .build()
        manager.notify(CALL_NOTIFICATION_ID, notification)
    }

    fun cancelIncomingCall() {
        manager.cancel(CALL_NOTIFICATION_ID)
    }

    private fun baseBuilder(
        channelId: String,
        squadCode: String,
        title: String,
        text: String,
    ): Notification.Builder {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("boostlab://squad/$squadCode"),
            appContext,
            MainActivity::class.java,
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            squadCode.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(appContext, channelId)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
    }

    private fun canNotify(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun messageNotificationId(squadCode: String, sender: String): Int =
        10_000 + (31 * squadCode.hashCode() + sender.hashCode()).and(0x3FFFFFFF)

    companion object {
        private const val MESSAGE_CHANNEL_ID = "boostlab_squad_messages"
        private const val CALL_CHANNEL_ID = "boostlab_squad_calls"
        private const val CALL_NOTIFICATION_ID = 42_001
    }
}
