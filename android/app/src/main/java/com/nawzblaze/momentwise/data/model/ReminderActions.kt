package com.nawzblaze.momentwise.data.model

/**
 * The three advanced reminder actions, each with a user-customisable label.
 *
 * Defaults follow the approved product specification:
 * - Quit: "Take a break" — leaves the distracting app (return to home screen).
 * - Snooze: "Continue for now" — postpones the next check-in, 10 minutes by default.
 * - Dismiss: "Dismiss for today" — stops prompts for this app for the rest of the local day.
 */
data class ReminderActions(
    val quitLabel: String = "Okay, I'm quitting",
    val quitAction: RuleAction = RuleAction.LEAVE_APP,
    val snoozeLabel: String = "Wait",
    val snoozeAction: RuleAction = RuleAction.SNOOZE_REMINDER,
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    val dismissLabel: String = "Dismiss for today",
    val dismissAction: RuleAction = RuleAction.DISMISS_FOR_TODAY,
) {
    companion object {
        const val DEFAULT_SNOOZE_MINUTES = 10
        const val MIN_SNOOZE_MINUTES = 1
        const val MAX_SNOOZE_MINUTES = 60
    }
}
