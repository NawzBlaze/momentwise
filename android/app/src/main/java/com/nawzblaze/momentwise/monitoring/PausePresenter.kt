package com.nawzblaze.momentwise.monitoring

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
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

    /** Cleared by the monitoring service so a later pause is not blocked. */
    @Volatile
    var onDismissed: (() -> Unit)? = null

    /** Set when the pause activity is actually on screen. */
    @Volatile
    var onShown: (() -> Unit)? = null

    @Volatile
    private var shownGeneration = 0

    fun currentGeneration(): Int = shownGeneration

    fun show(context: Context, request: PauseRequest, themeModeName: String) {
        shownGeneration += 1
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

    /**
     * The notification opened [PauseActivity]. Remove the fallback surfaces
     * without clearing the "pause is showing" latch — the activity is the pause.
     */
    fun handOffToActivity(context: Context) {
        PauseOverlay.dismiss(context)
        cancelNotification(context)
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
            .setDeleteIntent(PauseClearedReceiver.pending(context))
            .setAutoCancel(false)
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

/**
 * The user swiped the pause notification away without choosing. That is not a
 * Quit or an Anyways. Clear the "pause is showing" latch so the next opening
 * can show again.
 */
class PauseClearedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        PausePresenter.onDismissed?.invoke()
    }

    companion object {
        const val ACTION = "com.nawzblaze.momentwise.action.PAUSE_CLEARED"

        fun pending(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                2,
                Intent(context, PauseClearedReceiver::class.java).setAction(ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}

object BootFlags {
    @Volatile
    var dropSessionOnNextStart: Boolean = false
}
