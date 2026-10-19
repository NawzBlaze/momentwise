package com.nawzblaze.momentwise.monitoring

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.nawzblaze.momentwise.R

object NotificationChannels {
    const val MONITORING = "momentwise_monitoring"
    const val PAUSE = "momentwise_pause"
    const val MONITORING_NOTIFICATION_ID = 41
    const val PAUSE_NOTIFICATION_ID = 42

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val monitoring = NotificationChannel(
            MONITORING,
            context.getString(R.string.channel_monitoring_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_monitoring_desc)
            setShowBadge(false)
        }
        val pause = NotificationChannel(
            PAUSE,
            context.getString(R.string.channel_pause_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_pause_desc)
        }
        manager.createNotificationChannel(monitoring)
        manager.createNotificationChannel(pause)
    }
}
