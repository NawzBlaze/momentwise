package com.nawzblaze.momentwise.intervention

/**
 * Copy shared by the live pause, the in-app preview, and a new rule.
 * Button labels can still be saved per app. These lines are the defaults.
 */
object InterventionSurface {
    const val CARD_HEIGHT_FRACTION = 0.62f
    const val WINDOW_TITLE = "Momentwise intervention"
    const val EYEBROW = "MOMENTWISE"
    const val OPENING_HEADING = "Is this where you want your time to go?"
    const val OPENING_SUPPORT = "Take a breath. Choose what you want to do next."
    const val LEAVE_SUPPORT = "Step away and return to your home screen."
    const val CONTINUE_LABEL = "I'm here with purpose"
    const val REMINDER_HEADING = "Still here"

    /** Never, and any non-positive interval, must not promise a reminder. */
    fun continueSupport(intervalMinutes: Int?): String = when {
        intervalMinutes == null || intervalMinutes <= 0 -> "Continue. No reminder will be scheduled."
        intervalMinutes == 1 -> "Continue, with a reminder in 1 minute."
        else -> "Continue, with a reminder in $intervalMinutes minutes."
    }

    fun snoozeSupport(minutes: Int): String =
        if (minutes == 1) "Check again in 1 minute." else "Check again in $minutes minutes."
}
