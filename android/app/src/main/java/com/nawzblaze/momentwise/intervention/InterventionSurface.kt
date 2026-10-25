package com.nawzblaze.momentwise.intervention

/**
 * Copy shared by the live pause, the in-app preview, and a new rule.
 * Button labels can still be saved per app. These lines are the defaults.
 */
object InterventionSurface {
    const val CARD_HEIGHT_FRACTION = 0.62f
    const val WINDOW_TITLE = "Momentwise intervention"
    const val WORDMARK = "Momentwise"
    const val OPENING_HEADING = "Before You Dive In"
    const val OPENING_SUPPORT = "Take a second. What do you want from this app right now?"
    const val LEAVE_SUPPORT = "Return to your home screen."
    const val CONTINUE_LABEL = "Open Intentionally"
    const val REMIND_ME_LATER = "Remind Me Later"
    const val DISMISS_FOR_TODAY = "Dismiss for today"
    const val REMINDER_HEADING = "Time for a Little Pause."

    /** Never, and any non-positive interval, must not promise a reminder. */
    fun continueSupport(intervalMinutes: Int?): String = when {
        intervalMinutes == null || intervalMinutes <= 0 -> "Continue without a reminder."
        intervalMinutes == 1 -> "Reminder after 1 minute of use."
        else -> "Reminder after $intervalMinutes minutes of use."
    }

    fun dismissHelp(appName: String): String {
        val name = appName.trim().ifBlank { "this app" }
        return "No more prompts for $name today."
    }

    fun snoozeSupport(minutes: Int): String =
        if (minutes == 1) "Check again after 1 minute of use." else "Check again after $minutes minutes of use."
}
