package com.nawzblaze.momentwise.data.local

import com.nawzblaze.momentwise.data.ReminderFrequencyCodec
import com.nawzblaze.momentwise.data.RuleDefaults
import com.nawzblaze.momentwise.data.model.AppRule
import com.nawzblaze.momentwise.data.model.ReminderActions
import com.nawzblaze.momentwise.data.model.ResponseOption
import com.nawzblaze.momentwise.data.model.RuleAction
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON mapping for [AppRule] persistence, built on org.json (bundled with
 * Android — no extra serialization dependency for a model this small).
 *
 * Decoding is defensive: missing or malformed fields fall back to the approved
 * defaults so a partially written or legacy record can never crash the app or
 * silently invent unapproved behaviour.
 */
internal object AppRuleJson {

    fun encodeList(rules: List<AppRule>): String {
        val array = JSONArray()
        rules.forEach { array.put(encode(it)) }
        return array.toString()
    }

    /** Returns null when the input is blank or structurally invalid. */
    fun decodeList(raw: String?): List<AppRule>? {
        if (raw.isNullOrBlank()) return null
        return try {
            val array = JSONArray(raw)
            (0 until array.length())
                .map { decode(array.getJSONObject(it)) }
                .filter { it.appId.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun encode(rule: AppRule): JSONObject = JSONObject().apply {
        put("appId", rule.appId)
        put("appName", rule.appName)
        put("category", rule.category)
        put("enabled", rule.enabled)
        put("configuredEver", rule.configuredEver)
        put("openingNotice", rule.openingNotice)
        put("option1Label", rule.quitOption.label)
        put("option2Label", rule.continueOption.label)
        put("reminderFrequency", ReminderFrequencyCodec.encode(rule.reminderFrequency))
        put("reminderNotice", rule.reminderNotice)
        put("advQuitLabel", rule.reminderActions.quitLabel)
        put("advSnoozeLabel", rule.reminderActions.snoozeLabel)
        put("advSnoozeMinutes", rule.reminderActions.snoozeMinutes)
        put("advDismissLabel", rule.reminderActions.dismissLabel)
        put("isSample", rule.isSample)
    }

    private fun decode(json: JSONObject): AppRule {
        val appId = json.optString("appId")
        val appName = json.optString("appName").ifBlank { appId }
        val snooze = json.optInt("advSnoozeMinutes", ReminderActions.DEFAULT_SNOOZE_MINUTES)
            .coerceIn(ReminderActions.MIN_SNOOZE_MINUTES, ReminderActions.MAX_SNOOZE_MINUTES)
        return AppRule(
            appId = appId,
            appName = appName,
            category = json.optString("category"),
            enabled = json.optBoolean("enabled", false),
            configuredEver = json.optBoolean("configuredEver", false),
            openingNotice = migrateOpeningNotice(json.optString("openingNotice")),
            quitOption = ResponseOption(
                label = json.optString("option1Label").ifBlank { RuleDefaults.leaveLabel(appName) },
                action = RuleAction.LEAVE_APP,
            ),
            continueOption = ResponseOption(
                label = migrateContinueLabel(json.optString("option2Label")),
                action = RuleAction.CONTINUE_INTO_APP,
            ),
            reminderFrequency = ReminderFrequencyCodec.decode(json.optString("reminderFrequency")),
            reminderNotice = json.optString("reminderNotice")
                .ifBlank { RuleDefaults.REMINDER_NOTICE },
            reminderActions = ReminderActions(
                quitLabel = json.optString("advQuitLabel").ifBlank { RuleDefaults.ADV_QUIT_LABEL },
                snoozeLabel = json.optString("advSnoozeLabel").ifBlank { RuleDefaults.ADV_SNOOZE_LABEL },
                snoozeMinutes = snooze,
                dismissLabel = migrateDismissLabel(json.optString("advDismissLabel")),
            ),
            isSample = json.optBoolean("isSample", false),
        )
    }

    /**
     * Blank notices and the two shipped generic prompts fall back to the
     * current default. A saved custom prompt is kept, including
     * "What brought you here?". Intervals are not touched here.
     */
    private fun migrateOpeningNotice(raw: String): String {
        val trimmed = raw.trim()
        val legacy = trimmed.isEmpty() ||
            LEGACY_OPENING_NOTICES.any { it.equals(trimmed, ignoreCase = true) }
        return if (legacy) RuleDefaults.OPENING_NOTICE else trimmed
    }

    /** The previous shipped label only. A renamed button stays. */
    private fun migrateContinueLabel(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isEmpty() || LEGACY_CONTINUE_LABELS.any { it.equals(trimmed, ignoreCase = true) }) {
            RuleDefaults.CONTINUE_OPTION_LABEL
        } else {
            trimmed
        }
    }

    /** The previous shipped reminder action only. A renamed button stays. */
    private fun migrateDismissLabel(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isEmpty() || trimmed == LEGACY_DISMISS_LABEL) {
            RuleDefaults.ADV_DISMISS_LABEL
        } else {
            trimmed
        }
    }

    private val LEGACY_OPENING_NOTICES = setOf(
        "You Are Distracted",
        "What do you want to do right now?",
        "Take a breath. Choose what you want to do next.",
        "Is this where you want your time to go?",
        "Take a moment. Choose what comes next.",
        "What did you come here for?",
    )
    private val LEGACY_CONTINUE_LABELS = setOf(
        "Continue",
        "I'm here with purpose",
    )
    private const val LEGACY_DISMISS_LABEL = "Dismiss reminder"
}
