package com.nawzblaze.momentwise.monitoring

import android.content.Context
import android.content.Intent
import com.nawzblaze.momentwise.data.AppRuleRepository
import com.nawzblaze.momentwise.data.GuardSessionRepository
import com.nawzblaze.momentwise.intervention.PauseRequest
import com.nawzblaze.momentwise.intervention.SessionClock
import java.time.LocalDate

/**
 * Applies a pause choice. Quit sends the user home. It does not force-stop
 * the other app, and Continue does not relaunch it.
 */
class InterventionController(
    private val context: Context,
    private val rules: AppRuleRepository,
    private val sessions: GuardSessionRepository,
) {
    suspend fun quit() {
        sessions.update { SessionClock.onQuit(it) }
        PausePresenter.dismiss(context)
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(home) }
    }

    suspend fun continueIntoApp() {
        val rule = sessions.current().activePackage?.let { rules.ruleFor(it) }
        sessions.update {
            SessionClock.onContinue(
                snapshot = it,
                intervalMinutes = rule?.reminderFrequency?.minutesOrNull(),
                nowMillis = System.currentTimeMillis(),
                today = LocalDate.now().toString(),
            )
        }
        PausePresenter.dismiss(context)
    }

    suspend fun snooze(request: PauseRequest) {
        sessions.update {
            SessionClock.onSnooze(it, request.snoozeMinutes, System.currentTimeMillis())
        }
        PausePresenter.dismiss(context)
    }

    suspend fun dismissForToday(request: PauseRequest) {
        sessions.update {
            SessionClock.onDismissForToday(it, request.packageName, LocalDate.now().toString())
        }
        PausePresenter.dismiss(context)
    }
}
