package com.nawzblaze.momentwise.data

import com.nawzblaze.momentwise.data.model.AppRule
import com.nawzblaze.momentwise.data.model.ReminderFrequency
import com.nawzblaze.momentwise.data.model.ResponseOption
import com.nawzblaze.momentwise.data.model.RuleAction
import com.nawzblaze.momentwise.intervention.InterventionSurface

/**
 * Defaults for a newly configured rule.
 *
 * A stored custom notice, button label, or interval stays as the user saved
 * it. Decoding replaces only the previous shipped generic prompt and the
 * previous shipped Continue label. The browser prototype is a separate
 * design reference and still has the older wording.
 */
object RuleDefaults {
    const val OPENING_HEADING = InterventionSurface.OPENING_HEADING
    const val OPENING_NOTICE = InterventionSurface.OPENING_SUPPORT
    const val QUIT_OPTION_LABEL = "Leave"
    const val CONTINUE_OPTION_LABEL = InterventionSurface.CONTINUE_LABEL
    const val REMINDER_NOTICE = "You're still here?"
    const val ADV_QUIT_LABEL = "Okay, I'm quitting"
    const val ADV_SNOOZE_LABEL = "Wait"
    const val ADV_DISMISS_LABEL = "Dismiss for today"

    /** Custom-interval field's starting value when Custom is first selected. */
    const val CUSTOM_MINUTES_START = 8

    const val MAX_NOTICE_LENGTH = 120
    const val MAX_OPTION_LABEL_LENGTH = 24

    /** New rules remind every 5 minutes. Never remains an explicit choice. */
    val DEFAULT_REMINDER_FREQUENCY: ReminderFrequency = ReminderFrequency.EveryMinutes(5)

    /**
     * Primary label for a new rule. The action stays [RuleAction.LEAVE_APP]
     * even if the user later renames the button. Long names are trimmed to the
     * label limit; they are not rejected.
     */
    fun leaveLabel(appName: String): String {
        val name = appName.trim().ifBlank { "app" }
        val full = "Leave $name"
        if (full.length <= MAX_OPTION_LABEL_LENGTH) return full
        val room = (MAX_OPTION_LABEL_LENGTH - "Leave ".length).coerceAtLeast(0)
        return "Leave ${name.take(room)}".trimEnd()
    }

    /**
     * Builds a fresh rule with the approved defaults for one app.
     * Each app gets an independent instance — no shared mutable state.
     */
    fun ruleFor(
        appId: String,
        appName: String,
        category: String,
        enabled: Boolean = false,
        reminderFrequency: ReminderFrequency = DEFAULT_REMINDER_FREQUENCY,
        isSample: Boolean = false,
    ): AppRule = AppRule(
        appId = appId,
        appName = appName,
        category = category,
        enabled = enabled,
        configuredEver = enabled,
        openingNotice = OPENING_NOTICE,
        quitOption = ResponseOption(leaveLabel(appName), RuleAction.LEAVE_APP),
        continueOption = ResponseOption(CONTINUE_OPTION_LABEL, RuleAction.CONTINUE_INTO_APP),
        reminderFrequency = reminderFrequency,
        isSample = isSample,
    )

    /**
     * Foundation sample seed. Inactive samples stay on Never so they do not
     * look like the user configured a reminder. A newly configured device app
     * uses [DEFAULT_REMINDER_FREQUENCY], not this seed.
     */
    fun sampleCatalog(): List<AppRule> = listOf(
        ruleFor(
            "instagram",
            "Instagram",
            "Social Media",
            enabled = true,
            reminderFrequency = ReminderFrequency.EveryMinutes(10),
            isSample = true,
        ),
        ruleFor(
            "tiktok",
            "TikTok",
            "Short Video",
            enabled = true,
            reminderFrequency = ReminderFrequency.EveryMinutes(15),
            isSample = true,
        ),
        ruleFor("x", "X / Twitter", "News & Feeds", reminderFrequency = ReminderFrequency.Never, isSample = true),
        ruleFor("youtube", "YouTube", "Video & Media", reminderFrequency = ReminderFrequency.Never, isSample = true),
        ruleFor("reddit", "Reddit", "Communities", reminderFrequency = ReminderFrequency.Never, isSample = true),
        ruleFor("threads", "Threads", "Social Media", reminderFrequency = ReminderFrequency.Never, isSample = true),
    )
}
