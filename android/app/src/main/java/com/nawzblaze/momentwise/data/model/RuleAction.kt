package com.nawzblaze.momentwise.data.model

/**
 * What a configured button on a Momentwise notice actually does.
 *
 * Every user-facing label is customisable, but the underlying behaviour is fixed
 * and typed, so the intervention engine can never be misconfigured into claiming
 * unsupported behaviour (for example force-stopping another app — see
 * ANDROID_IMPLEMENTATION_PLAN.md, "Quit action semantics").
 */
enum class RuleAction {
    /**
     * Leave the distracting app. Natively implemented as returning the user to
     * their home screen through supported Android behaviour — never a force-stop.
     */
    LEAVE_APP,

    /**
     * Continue into the distracting app and start the configured reminder
     * interval (if reminders are enabled for this app).
     */
    CONTINUE_INTO_APP,

    /**
     * Snooze the current reminder for the configured number of minutes.
     */
    SNOOZE_REMINDER,

    /**
     * Stop further reminders for this app for the rest of the day.
     * Does not disable the rule itself.
     */
    DISMISS_FOR_TODAY,
}
