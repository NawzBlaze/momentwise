package com.nawzblaze.momentwise.data.model

/**
 * One guarded app's complete, independent rule configuration.
 *
 * Every app owns its own [AppRule]; rules never inherit from or share state
 * with each other (verified by RuleIsolationTest). A newly built rule uses:
 *
 * - Opening heading: "Is this where you want your time to go?"
 * - Opening support: "Take a breath. Choose what you want to do next."
 * - Option 1: "Leave {app name}" — returns home. It does not force-stop.
 * - Option 2: "I'm here with purpose" — dismisses the pause and starts the reminder.
 * - Reminder frequency: 5 minutes. Never is still an explicit choice.
 * - Reminder notice: "You're still here?"
 *
 * These constructor defaults are for a new rule. Decoding a saved rule does
 * not replace a stored prompt, label, or interval with them.
 */
data class AppRule(
    /** Stable identifier — the target app's package name once device detection is wired. */
    val appId: String,
    val appName: String,
    val category: String,
    val enabled: Boolean = false,
    /** True once the user has opened/saved this app's configuration at least once. */
    val configuredEver: Boolean = false,
    val openingNotice: String = "Take a breath. Choose what you want to do next.",
    val quitOption: ResponseOption = ResponseOption("Leave", RuleAction.LEAVE_APP),
    val continueOption: ResponseOption = ResponseOption("I'm here with purpose", RuleAction.CONTINUE_INTO_APP),
    val reminderFrequency: ReminderFrequency = ReminderFrequency.EveryMinutes(5),
    val reminderNotice: String = "You're still here?",
    val reminderActions: ReminderActions = ReminderActions(),
    /**
     * True while the native app still ships the seeded sample catalog instead of
     * real installed apps. Sample entries must always be labelled as such in the
     * UI — they are never presented as detected device state.
     */
    val isSample: Boolean = false,
)
