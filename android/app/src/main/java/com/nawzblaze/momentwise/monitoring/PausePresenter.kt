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
 * Shows the pause.
 *
 * When display-over-other-apps is granted, the pause is a visible overlay:
 * a card in the middle and a scrim that consumes touches. That does not
 * require a background activity start.
 *
 * If the overlay cannot be added, a background activity start is attempted
 * once and its result is not treated as proof the activity was shown. Since
 * API 29 a foreground service is not allowed to start an activity. The
 * notification remains the fallback, and its text says the other app is not
 * blocked.
 *
 * If overlay is not granted, only the notification is shown. A notification
 * is not described as a cover.
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
            if (PauseOverlay.show(context, request, themeModeName)) {
                cancelNotification(context)
                return
            }
            noteActivityAttempt(context, request)
        } else {
            DebugNotes.append(context, "intervention=notification reason=overlay-not-granted")
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
        DebugNotes.append(context, "intervention=activity")
    }

    /**
     * startActivity returning is not evidence the pause was visible.
     * Background activity starts are commonly blocked without an exception.
     */
    private fun noteActivityAttempt(context: Context, request: PauseRequest) {
        val outcome = try {
            context.startActivity(PauseActivity.intent(context, request))
            "returned"
        } catch (error: Exception) {
            error.javaClass.simpleName
        }
        DebugNotes.append(context, "activity-start=$outcome")
    }

    private fun showNotification(context: Context, request: PauseRequest) {
        val launch = PauseActivity.intent(context, request.copy(fromNotification = true))
        val pending = PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val fallback = context.getString(R.string.pause_notification_fallback, request.appName)
        val visible = "${request.heading}. ${request.notice} $fallback"
        val notification = NotificationCompat.Builder(context, NotificationChannels.PAUSE)
            .setSmallIcon(R.drawable.ic_stat_pause)
            .setContentTitle(request.heading)
            .setContentText(visible)
            .setStyle(NotificationCompat.BigTextStyle().bigText(visible))
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
 * Leave or a Continue. Clear the "pause is showing" latch so the next opening
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
