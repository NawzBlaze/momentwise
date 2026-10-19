package com.nawzblaze.momentwise.data.model

/**
 * One guarded app's complete, independent rule configuration.
 *
 * Every app owns its own [AppRule]; rules never inherit from or share state
 * with each other (verified by RuleIsolationTest). Defaults follow the
 * approved product specification exactly:
 *
 * - Opening notice: "What brought you here?"
 * - Option 1: "Quit" — leaves the distracting app.
 * - Option 2: "Anyways" — continues into the app.
 * - Reminder frequency: Never (presets 5/10/15/20 or Custom when enabled).
 * - Reminder notice: "You're still here?"
 * - Advanced actions: quit / snooze (10 min) / dismiss-for-today, collapsed by default.
 */
data class AppRule(
    /** Stable identifier — the target app's package name once device detection is wired. */
    val appId: String,
    val appName: String,
    val category: String,
    val enabled: Boolean = false,
    /** True once the user has opened/saved this app's configuration at least once. */
    val configuredEver: Boolean = false,
    val openingNotice: String = "What brought you here?",
    val quitOption: ResponseOption = ResponseOption("Quit", RuleAction.LEAVE_APP),
    val continueOption: ResponseOption = ResponseOption("Anyways", RuleAction.CONTINUE_INTO_APP),
    val reminderFrequency: ReminderFrequency = ReminderFrequency.Never,
    val reminderNotice: String = "You're still here?",
    val reminderActions: ReminderActions = ReminderActions(),
    /**
     * True while the native app still ships the seeded sample catalog instead of
     * real installed apps. Sample entries must always be labelled as such in the
     * UI — they are never presented as detected device state.
     */
    val isSample: Boolean = false,
)
