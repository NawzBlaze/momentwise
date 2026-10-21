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
enum class PauseSurface { NONE, OVERLAY, ACTIVITY, NOTIFICATION, SUSPENDED }

enum class PauseDismissReason {
    /** Leave, Continue, snooze, or dismiss-for-today. */
    CHOICE,
    /** Usage Access saw a leave. */
    LEFT,
    /** Monitoring stopped, or the process is shutting down. Not a choice. */
    STOPPED,
    /** Notification swipe, activity finish, or the system removed the overlay. Not a choice. */
    WITHOUT_CHOICE,
}

object PausePresenter {

    /** Cleared by the monitoring service. The reason says whether a choice was made. */
    @Volatile
    var onDismissed: ((PauseDismissReason, Long) -> Unit)? = null

    /** Set when the pause activity is actually on screen. */
    @Volatile
    var onShown: (() -> Unit)? = null

    @Volatile
    var surface: PauseSurface = PauseSurface.NONE
        private set

    @Volatile
    private var shownGeneration = 0

    @Volatile
    private var shownVisitId = 0L

    @Volatile
    private var handledGeneration = -1

    fun currentGeneration(): Int = shownGeneration

    /**
     * Records why this generation's pause went away. A later destroy of the
     * same generation must not turn a choice into "no choice".
     */
    fun noteCleared(reason: PauseDismissReason, visitId: Long = shownVisitId) {
        val generation = shownGeneration
        if (reason == PauseDismissReason.WITHOUT_CHOICE && visitId != 0L && visitId != shownVisitId) return
        if (handledGeneration == generation && reason == PauseDismissReason.WITHOUT_CHOICE) return
        handledGeneration = generation
        surface = PauseSurface.NONE
        onDismissed?.invoke(reason, visitId)
    }

    fun show(context: Context, request: PauseRequest, themeModeName: String, visitId: Long = 0L) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                show(context, request, themeModeName, visitId)
            }
            return
        }
        shownGeneration += 1
        if (visitId != 0L) shownVisitId = visitId
        if (GrantChecks.read(context).overlay) {
            if (PauseOverlay.show(context, request, themeModeName, shownVisitId)) {
                surface = PauseSurface.OVERLAY
                cancelNotification(context)
                return
            }
            noteActivityAttempt(context, request)
        } else {
            DebugNotes.append(context, "intervention=notification reason=overlay-not-granted")
        }
        surface = PauseSurface.NOTIFICATION
        showNotification(context, request)
    }

    /**
     * Temporarily detaches the overlay when its guarded app is no longer the
     * foreground. This is not a user dismissal: preserve the unanswered request
     * and do not invoke [onDismissed].
     */
    fun suspendForTargetAway(context: Context) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                suspendForTargetAway(context)
            }
            return
        }
        if (surface != PauseSurface.OVERLAY) return
        PauseOverlay.dismiss(context)
        cancelNotification(context)
        surface = PauseSurface.SUSPENDED
        DebugNotes.append(context, "intervention=overlay suspended outside target foreground")
    }

    /**
     * Expires a pause that remained unanswered while its target app was away
     * for the full grace window. It must not be treated as Android removing a
     * visible window, or the normal system-removal recovery would reshow it.
     */
    fun clearSuspended(context: Context) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                clearSuspended(context)
            }
            return
        }
        if (surface != PauseSurface.SUSPENDED) return
        PauseOverlay.dismiss(context)
        cancelNotification(context)
        surface = PauseSurface.NONE
        DebugNotes.append(context, "intervention=suspended pause expired without a choice")
    }

    fun dismiss(context: Context, reason: PauseDismissReason = PauseDismissReason.WITHOUT_CHOICE) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { dismiss(context, reason) }
            return
        }
        noteCleared(reason)
        PauseOverlay.dismiss(context)
        cancelNotification(context)
        DebugNotes.append(context, "intervention=dismissed reason=$reason")
    }

    /**
     * The notification opened [PauseActivity]. Remove the fallback surfaces
     * without clearing the "pause is showing" latch — the activity is the pause.
     */
    fun handOffToActivity(context: Context) {
        PauseOverlay.dismiss(context)
        cancelNotification(context)
        surface = PauseSurface.ACTIVITY
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
        DebugNotes.append(context, "notification posted id=${NotificationChannels.PAUSE_NOTIFICATION_ID}")
    }

    private fun cancelNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(NotificationChannels.PAUSE_NOTIFICATION_ID)
    }
}

/**
 * The user swiped the pause notification away without choosing. That is not a
 * Leave or a Continue. The same visit is not stacked again immediately. The
 * next visit is not suppressed.
 */
class PauseClearedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        PausePresenter.noteCleared(PauseDismissReason.WITHOUT_CHOICE)
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
