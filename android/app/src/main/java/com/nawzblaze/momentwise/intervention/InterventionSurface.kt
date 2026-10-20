package com.nawzblaze.momentwise.intervention

/**
 * Copy shared by the live pause, the in-app preview, and a new rule.
 * Button labels can still be saved per app. These lines are the defaults.
 */
object InterventionSurface {
    const val CARD_HEIGHT_FRACTION = 0.62f
    const val WINDOW_TITLE = "Momentwise intervention"
    const val WORDMARK = "Momentwise"
    const val OPENING_HEADING = "What did you come here for?"
    const val OPENING_SUPPORT = "Take a moment. Choose what comes next."
    const val LEAVE_SUPPORT = "Return to your home screen."
    const val CONTINUE_LABEL = "Continue intentionally"
    const val DISMISS_FOR_TODAY = "Dismiss for today"
    const val DISMISS_HELP = "Continue without more Momentwise prompts for this app today."
    const val REMINDER_HEADING = "Still here"

    /** Never, and any non-positive interval, must not promise a reminder. */
    fun continueSupport(intervalMinutes: Int?): String = when {
        intervalMinutes == null || intervalMinutes <= 0 -> "Continue without a reminder."
        intervalMinutes == 1 -> "Remind me in 1 minute."
        else -> "Remind me in $intervalMinutes minutes."
    }

    fun snoozeSupport(minutes: Int): String =
        if (minutes == 1) "Check again in 1 minute." else "Check again in $minutes minutes."
}
