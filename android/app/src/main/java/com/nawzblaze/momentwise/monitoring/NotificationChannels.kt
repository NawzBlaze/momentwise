package com.nawzblaze.momentwise.monitoring

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.nawzblaze.momentwise.R

object NotificationChannels {
    /**
     * v2 replaces the low-importance channel. Android will not raise the
     * importance of a channel the user, or an older build, already created.
     * A hidden low-importance notification looks like monitoring stopped.
     */
    const val MONITORING = "momentwise_monitoring_v2"
    const val PAUSE = "momentwise_pause"
    const val MONITORING_NOTIFICATION_ID = 41
    const val PAUSE_NOTIFICATION_ID = 42

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val monitoring = NotificationChannel(
            MONITORING,
            context.getString(R.string.channel_monitoring_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_monitoring_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
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
