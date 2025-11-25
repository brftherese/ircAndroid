package com.example.ircclient

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    private const val CHANNEL_HIGHLIGHTS = "irc_highlights"
    private const val CHANNEL_DIRECT = "irc_direct"
    private const val CHANNEL_SESSION = "irc_session"

    enum class NotificationKind { HIGHLIGHT, DIRECT }

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val highlightChannel = NotificationChannel(
                CHANNEL_HIGHLIGHTS,
                "IRC Highlights",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Mentions in channels"
                enableLights(true)
                lightColor = Color.CYAN
            }
            val dmChannel = NotificationChannel(
                CHANNEL_DIRECT,
                "IRC Direct Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Direct/private messages"
                enableLights(true)
                lightColor = Color.MAGENTA
            }
            val sessionChannel = NotificationChannel(
                CHANNEL_SESSION,
                "IRC Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground session status"
                setShowBadge(false)
            }
            nm.createNotificationChannel(highlightChannel)
            nm.createNotificationChannel(dmChannel)
            nm.createNotificationChannel(sessionChannel)
        }
    }

    fun notifyEvent(context: Context, kind: NotificationKind, title: String, body: String, id: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val channelId = when (kind) {
            NotificationKind.HIGHLIGHT -> CHANNEL_HIGHLIGHTS
            NotificationKind.DIRECT -> CHANNEL_DIRECT
        }
        val icon = when (kind) {
            NotificationKind.HIGHLIGHT -> android.R.drawable.star_on
            NotificationKind.DIRECT -> android.R.drawable.stat_notify_chat
        }
        val category = when (kind) {
            NotificationKind.HIGHLIGHT -> NotificationCompat.CATEGORY_EVENT
            NotificationKind.DIRECT -> NotificationCompat.CATEGORY_MESSAGE
        }
        val priority = when (kind) {
            NotificationKind.HIGHLIGHT -> NotificationCompat.PRIORITY_DEFAULT
            NotificationKind.DIRECT -> NotificationCompat.PRIORITY_HIGH
        }
        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(category)
            .setPriority(priority)
            .setOnlyAlertOnce(false)
            .build()
        NotificationManagerCompat.from(context).notify(id, notif)
    }

    fun buildSessionNotification(
        context: Context,
        title: String,
        text: String,
        stopIntent: PendingIntent,
        contentIntent: PendingIntent,
    ) = NotificationCompat.Builder(context, CHANNEL_SESSION)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(contentIntent)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", stopIntent)
        .build()
}
