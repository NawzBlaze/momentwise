package com.nawzblaze.momentwise.data

import com.nawzblaze.momentwise.data.model.FrequencySelection
import com.nawzblaze.momentwise.data.model.ReminderFrequency

/** Fields of the Configure Rule screen that can report a validation error. */
enum class RuleField {
    OPENING_NOTICE,
    OPTION1_LABEL,
    OPTION2_LABEL,
    CUSTOM_INTERVAL,
    REMINDER_NOTICE,
}

/**
 * Immutable validation outcome. Errors are keyed per field so the UI can render
 * them inline, matching the prototype's `validation-msg` behaviour.
 */
data class RuleValidationResult(private val errors: Map<RuleField, String>) {
    val isValid: Boolean get() = errors.isEmpty()

    fun errorFor(field: RuleField): String? = errors[field]

    companion object {
        val VALID = RuleValidationResult(emptyMap())
    }
}

/**
 * Validates a Configure Rule draft. Rules mirror the approved prototype exactly
 * (`saveCurrentConfig` in prototype/app.js):
 *
 * - opening notice must not be blank (max 120 chars);
 * - both option labels must not be blank (max 24 chars) and must differ
 *   case-insensitively;
 * - Custom frequency requires an integer interval of 1..300 minutes;
 * - when reminders are enabled the reminder notice must not be blank (max 120).
 *
 * Advanced action labels intentionally do not error when blank — they normalise
 * back to the approved defaults on save, as in the prototype.
 */
object RuleValidator {

    fun validate(draft: AppRuleDraft): RuleValidationResult {
        val errors = LinkedHashMap<RuleField, String>()

        val notice = draft.openingNotice.trim()
        if (notice.isEmpty()) {
            errors[RuleField.OPENING_NOTICE] = "Please enter an opening notice."
        } else if (notice.length > RuleDefaults.MAX_NOTICE_LENGTH) {
            errors[RuleField.OPENING_NOTICE] =
                "Opening notice must be ${RuleDefaults.MAX_NOTICE_LENGTH} characters or fewer."
        }

        val opt1 = draft.option1Label.trim()
        val opt2 = draft.option2Label.trim()
        if (opt1.isEmpty()) {
            errors[RuleField.OPTION1_LABEL] = "Label cannot be empty."
        } else if (opt1.length > RuleDefaults.MAX_OPTION_LABEL_LENGTH) {
            errors[RuleField.OPTION1_LABEL] =
                "Label must be ${RuleDefaults.MAX_OPTION_LABEL_LENGTH} characters or fewer."
        }
        if (opt2.isEmpty()) {
            errors[RuleField.OPTION2_LABEL] = "Label cannot be empty."
        } else if (opt2.length > RuleDefaults.MAX_OPTION_LABEL_LENGTH) {
            errors[RuleField.OPTION2_LABEL] =
                "Label must be ${RuleDefaults.MAX_OPTION_LABEL_LENGTH} characters or fewer."
        }
        if (opt1.isNotEmpty() && opt1.equals(opt2, ignoreCase = true)) {
            errors[RuleField.OPTION2_LABEL] = "Option labels must be distinct."
        }

        if (draft.frequencySelection == FrequencySelection.CUSTOM) {
            val minutes = draft.customMinutes
            if (minutes == null ||
                minutes !in ReminderFrequency.MIN_MINUTES..ReminderFrequency.MAX_MINUTES
            ) {
                errors[RuleField.CUSTOM_INTERVAL] =
                    "Please enter a valid interval between ${ReminderFrequency.MIN_MINUTES} " +
                        "and ${ReminderFrequency.MAX_MINUTES} minutes."
            }
        }

        if (draft.frequencySelection != FrequencySelection.NEVER &&
            draft.reminderNotice.trim().isEmpty()
        ) {
            errors[RuleField.REMINDER_NOTICE] = "Please provide a reminder notice message."
        } else if (draft.reminderNotice.trim().length > RuleDefaults.MAX_NOTICE_LENGTH) {
            errors[RuleField.REMINDER_NOTICE] =
                "Reminder notice must be ${RuleDefaults.MAX_NOTICE_LENGTH} characters or fewer."
        }

        return if (errors.isEmpty()) RuleValidationResult.VALID else RuleValidationResult(errors)
    }
}
