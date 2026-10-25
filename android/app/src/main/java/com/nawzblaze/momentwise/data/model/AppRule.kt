package com.nawzblaze.momentwise.data.model

/**
 * One guarded app's complete, independent rule configuration.
 *
 * Every app owns its own [AppRule]; rules never inherit from or share state
 * with each other (verified by RuleIsolationTest). A newly built rule uses:
 *
 * - Opening heading: "Before you scroll"
 * - Opening support: "Take a second. What do you want from this app right now?"
 * - Option 1: "Not now" — returns home. It does not force-stop.
 * - Option 2: "Open intentionally" — dismisses the pause and starts the reminder.
 * - Reminder frequency: 5 minutes. Never is still an explicit choice.
 * - Reminder notice: "You've been here for a while."
 *
 * These constructor defaults are for a new rule. JSON decoding updates only
 * previous shipped default copy; custom prompts, labels, and intervals stay unchanged.
 */
data class AppRule(
    /** Stable identifier — the target app's package name once device detection is wired. */
    val appId: String,
    val appName: String,
    val category: String,
    val enabled: Boolean = false,
    /** True once the user has opened/saved this app's configuration at least once. */
    val configuredEver: Boolean = false,
    val openingNotice: String = "Take a second. What do you want from this app right now?",
    val quitOption: ResponseOption = ResponseOption("Leave App", RuleAction.LEAVE_APP),
    val continueOption: ResponseOption = ResponseOption("Open Intentionally", RuleAction.CONTINUE_INTO_APP),
    val reminderFrequency: ReminderFrequency = ReminderFrequency.EveryMinutes(5),
    val reminderNotice: String = "You've been here for a while.",
    val reminderActions: ReminderActions = ReminderActions(),
    /**
     * True while the native app still ships the seeded sample catalog instead of
     * real installed apps. Sample entries must always be labelled as such in the
     * UI — they are never presented as detected device state.
     */
    val isSample: Boolean = false,
)
