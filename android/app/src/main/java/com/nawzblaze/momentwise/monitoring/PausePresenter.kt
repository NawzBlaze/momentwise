package com.nawzblaze.momentwise.monitoring

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.nawzblaze.momentwise.R
import com.nawzblaze.momentwise.intervention.PauseActivity
import com.nawzblaze.momentwise.intervention.PauseRequest

/**
 * Shows the pause. An overlay is used only when the user has allowed display
 * over other apps, and the overlay is the pause itself — never an invisible
 * window held open to keep a privilege. Otherwise a notification opens the
 * same screen. That path is labelled as a notification, not as a pause that
 * appeared on its own.
 */
object PausePresenter {

    fun show(context: Context, request: PauseRequest, themeModeName: String) {
        if (GrantChecks.read(context).overlay) {
            val shown = PauseOverlay.show(context, request, themeModeName)
            if (shown) {
                cancelNotification(context)
                return
            }
        }
        showNotification(context, request)
    }

    fun dismiss(context: Context) {
        PauseOverlay.dismiss(context)
        cancelNotification(context)
        onDismissed?.invoke()
    }

    private fun showNotification(context: Context, request: PauseRequest) {
        val launch = PauseActivity.intent(context, request.copy(fromNotification = true))
        val pending = PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.PAUSE)
            .setSmallIcon(R.drawable.ic_stat_pause)
            .setContentTitle(context.getString(R.string.pause_notification_title))
            .setContentText(request.notice)
            .setStyle(NotificationCompat.BigTextStyle().bigText(request.notice))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NotificationChannels.PAUSE_NOTIFICATION_ID, notification)
    }

    private fun cancelNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(NotificationChannels.PAUSE_NOTIFICATION_ID)
    }
}

object BootFlags {
    @Volatile
    var dropSessionOnNextStart: Boolean = false
}
