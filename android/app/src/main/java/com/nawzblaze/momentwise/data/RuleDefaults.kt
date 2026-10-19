package com.nawzblaze.momentwise.data

import com.nawzblaze.momentwise.data.model.AppRule
import com.nawzblaze.momentwise.data.model.ReminderFrequency

/**
 * Single source of truth for the approved default copy and seeded sample catalog.
 * Mirrors `prototype/app.js` (createDefaultRule / DEFAULT_APP_RULES) so the native
 * app and the browser prototype cannot drift apart silently.
 */
object RuleDefaults {
    const val OPENING_NOTICE = "What brought you here?"
    const val QUIT_OPTION_LABEL = "Quit"
    const val CONTINUE_OPTION_LABEL = "Anyways"
    const val REMINDER_NOTICE = "You're still here?"
    const val ADV_QUIT_LABEL = "Okay, I'm quitting"
    const val ADV_SNOOZE_LABEL = "Wait"
    const val ADV_DISMISS_LABEL = "Dismiss reminder"

    /** Custom-interval field's starting value when Custom is first selected. */
    const val CUSTOM_MINUTES_START = 8

    const val MAX_NOTICE_LENGTH = 120
    const val MAX_OPTION_LABEL_LENGTH = 24

    /**
     * Builds a fresh rule with the approved defaults for one app.
     * Each app gets an independent instance — no shared mutable state.
     */
    fun ruleFor(
        appId: String,
        appName: String,
        category: String,
        enabled: Boolean = false,
        reminderFrequency: ReminderFrequency = ReminderFrequency.Never,
        isSample: Boolean = false,
    ): AppRule = AppRule(
        appId = appId,
        appName = appName,
        category = category,
        enabled = enabled,
        configuredEver = enabled,
        reminderFrequency = reminderFrequency,
        isSample = isSample,
    )

    /**
     * Sample catalog for the foundation milestone, matching the prototype seed:
     * Instagram (enabled, 10 min reminders) and TikTok (enabled, 15 min reminders),
     * the rest available but inactive. These are clearly labelled sample entries in
     * the UI until real device app detection is wired in a later milestone.
     */
    fun sampleCatalog(): List<AppRule> = listOf(
        ruleFor("instagram", "Instagram", "Social Media", enabled = true,
            reminderFrequency = ReminderFrequency.EveryMinutes(10), isSample = true),
        ruleFor("tiktok", "TikTok", "Short Video", enabled = true,
            reminderFrequency = ReminderFrequency.EveryMinutes(15), isSample = true),
        ruleFor("x", "X / Twitter", "News & Feeds", isSample = true),
        ruleFor("youtube", "YouTube", "Video & Media", isSample = true),
        ruleFor("reddit", "Reddit", "Communities", isSample = true),
        ruleFor("threads", "Threads", "Social Media", isSample = true),
    )
}
