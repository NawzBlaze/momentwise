package com.nawzblaze.momentwise.monitoring

import android.content.Context
import android.content.Intent
import com.nawzblaze.momentwise.data.AppRuleRepository
import com.nawzblaze.momentwise.data.GuardSessionRepository
import com.nawzblaze.momentwise.intervention.PauseRequest
import com.nawzblaze.momentwise.intervention.SessionClock
import java.time.LocalDate

/**
 * Applies a pause choice. Leave sends the user home. It does not force-stop
 * the other app, and Continue does not relaunch it. The reminder starts only
 * after Continue.
 */
/** Set by explicit Leave so a quick return still shows the opening. Process-local. */
internal object LeaveSignal {
    @Volatile var packageName: String? = null
}

class InterventionController(
    private val context: Context,
    private val rules: AppRuleRepository,
    private val sessions: GuardSessionRepository,
) {
    suspend fun quit() {
        sessions.update {
            LeaveSignal.packageName = it.activePackage
            SessionClock.onQuit(it)
        }
        // Start home while the overlay, if any, is still visible. Some Android
        // 15 builds only allow that background start when an overlay is already up.
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(home) }
        PausePresenter.dismiss(context, PauseDismissReason.CHOICE)
    }

    /**
     * [packageName] is the pause the user answered. Looking up only the stored
     * session can miss the rule when the opening write lost the race, and then
     * Continue schedules no reminder.
     */
    suspend fun continueIntoApp(packageName: String) {
        val rule = rules.ruleFor(packageName)
        sessions.update {
            SessionClock.onContinue(
                snapshot = it,
                intervalMinutes = rule?.reminderFrequency?.minutesOrNull(),
                nowMillis = System.currentTimeMillis(),
                today = LocalDate.now().toString(),
                packageName = packageName,
            )
        }
        PausePresenter.dismiss(context, PauseDismissReason.CHOICE)
    }

    suspend fun snooze(request: PauseRequest) {
        sessions.update {
            SessionClock.onSnooze(it, request.snoozeMinutes, System.currentTimeMillis())
        }
        PausePresenter.dismiss(context, PauseDismissReason.CHOICE)
    }

    suspend fun dismissForToday(request: PauseRequest) {
        sessions.update {
            SessionClock.onDismissForToday(it, request.packageName, LocalDate.now().toString())
        }
        PausePresenter.dismiss(context, PauseDismissReason.CHOICE)
    }
}
