package com.nawzblaze.momentwise.data

import com.nawzblaze.momentwise.data.model.AppRule
import com.nawzblaze.momentwise.data.model.FrequencySelection
import com.nawzblaze.momentwise.data.model.ReminderActions
import com.nawzblaze.momentwise.data.model.ReminderFrequency
import com.nawzblaze.momentwise.data.model.ResponseOption
import com.nawzblaze.momentwise.data.model.RuleAction
import com.nawzblaze.momentwise.data.model.resolveFrequency

/**
 * The Configure Rule screen's editable state, kept separate from the persisted
 * [AppRule] so invalid text never reaches storage: a draft is only converted via
 * [toRule] after [RuleValidator] reports it valid.
 */
data class AppRuleDraft(
    val appId: String,
    val appName: String,
    val category: String,
    val isSample: Boolean,
    val enabled: Boolean,
    val openingNotice: String,
    val option1Label: String,
    val option2Label: String,
    val frequencySelection: FrequencySelection,
    /** Raw custom-interval field; only meaningful when [frequencySelection] is CUSTOM. */
    val customMinutes: Int?,
    val reminderNotice: String,
    val advancedQuitLabel: String,
    val advancedSnoozeLabel: String,
    /** Raw snooze field; invalid values normalise to the default (see [toRule]). */
    val snoozeMinutes: Int?,
    val advancedDismissLabel: String,
) {
    companion object {
        /** Loads an existing rule into an editable draft. */
        fun fromRule(rule: AppRule): AppRuleDraft = AppRuleDraft(
            appId = rule.appId,
            appName = rule.appName,
            category = rule.category,
            isSample = rule.isSample,
            enabled = rule.enabled,
            openingNotice = rule.openingNotice,
            option1Label = rule.quitOption.label,
            option2Label = rule.continueOption.label,
            frequencySelection = FrequencySelection.forFrequency(rule.reminderFrequency),
            customMinutes = rule.reminderFrequency.minutesOrNull() ?: RuleDefaults.CUSTOM_MINUTES_START,
            reminderNotice = rule.reminderNotice,
            advancedQuitLabel = rule.reminderActions.quitLabel,
            advancedSnoozeLabel = rule.reminderActions.snoozeLabel,
            snoozeMinutes = rule.reminderActions.snoozeMinutes,
            advancedDismissLabel = rule.reminderActions.dismissLabel,
        )
    }

    /**
     * Produces the rule to persist. Callers must validate first
     * ([RuleValidator.validate]); normalisation here mirrors the approved
     * prototype's save behaviour:
     * - advanced labels fall back to the approved defaults when blank;
     * - a missing/invalid snooze duration falls back to 10 minutes;
     * - the reminder notice falls back to the default when reminders are off
     *   and the field was cleared.
     */
    fun toRule(): AppRule {
        val frequency: ReminderFrequency =
            resolveFrequency(frequencySelection, customMinutes)
                ?: if (frequencySelection == FrequencySelection.CUSTOM) {
                    // Unreachable after validation; defensive fallback to the
                    // field's starting value rather than inventing a new interval.
                    ReminderFrequency.EveryMinutes(RuleDefaults.CUSTOM_MINUTES_START)
                } else {
                    ReminderFrequency.Never
                }

        val snooze = snoozeMinutes
            ?.takeIf { it >= ReminderActions.MIN_SNOOZE_MINUTES }
            ?.coerceAtMost(ReminderActions.MAX_SNOOZE_MINUTES)
            ?: ReminderActions.DEFAULT_SNOOZE_MINUTES

        return AppRule(
            appId = appId,
            appName = appName,
            category = category,
            enabled = enabled,
            configuredEver = true,
            openingNotice = openingNotice.trim(),
            quitOption = ResponseOption(option1Label.trim(), RuleAction.LEAVE_APP),
            continueOption = ResponseOption(option2Label.trim(), RuleAction.CONTINUE_INTO_APP),
            reminderFrequency = frequency,
            reminderNotice = reminderNotice.trim()
                .ifBlank { RuleDefaults.REMINDER_NOTICE },
            reminderActions = ReminderActions(
                quitLabel = advancedQuitLabel.trim().ifBlank { RuleDefaults.ADV_QUIT_LABEL },
                snoozeLabel = advancedSnoozeLabel.trim().ifBlank { RuleDefaults.ADV_SNOOZE_LABEL },
                snoozeMinutes = snooze,
                dismissLabel = advancedDismissLabel.trim().ifBlank { RuleDefaults.ADV_DISMISS_LABEL },
            ),
            isSample = isSample,
        )
    }
}
