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
        val snooze = json.optInt("advSnoozeMinutes", ReminderActions.DEFAULT_SNOOZE_MINUTES)
            .coerceIn(ReminderActions.MIN_SNOOZE_MINUTES, ReminderActions.MAX_SNOOZE_MINUTES)
        return AppRule(
            appId = appId,
            appName = json.optString("appName").ifBlank { appId },
            category = json.optString("category"),
            enabled = json.optBoolean("enabled", false),
            configuredEver = json.optBoolean("configuredEver", false),
            openingNotice = migrateOpeningNotice(json.optString("openingNotice")),
            quitOption = ResponseOption(
                label = json.optString("option1Label").ifBlank { RuleDefaults.QUIT_OPTION_LABEL },
                action = RuleAction.LEAVE_APP,
            ),
            continueOption = ResponseOption(
                label = json.optString("option2Label").ifBlank { RuleDefaults.CONTINUE_OPTION_LABEL },
                action = RuleAction.CONTINUE_INTO_APP,
            ),
            reminderFrequency = ReminderFrequencyCodec.decode(json.optString("reminderFrequency")),
            reminderNotice = json.optString("reminderNotice")
                .ifBlank { RuleDefaults.REMINDER_NOTICE },
            reminderActions = ReminderActions(
                quitLabel = json.optString("advQuitLabel").ifBlank { RuleDefaults.ADV_QUIT_LABEL },
                snoozeLabel = json.optString("advSnoozeLabel").ifBlank { RuleDefaults.ADV_SNOOZE_LABEL },
                snoozeMinutes = snooze,
                dismissLabel = json.optString("advDismissLabel").ifBlank { RuleDefaults.ADV_DISMISS_LABEL },
            ),
            isSample = json.optBoolean("isSample", false),
        )
    }

    /**
     * Blank notices fall back to the approved default. The known legacy default
     * "You Are Distracted" is migrated the same way as the browser prototype,
     * without touching any notice the user actually wrote.
     */
    private fun migrateOpeningNotice(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isEmpty() || trimmed.equals(LEGACY_OPENING_NOTICE, ignoreCase = true)) {
            RuleDefaults.OPENING_NOTICE
        } else {
            trimmed
        }
    }

    private const val LEGACY_OPENING_NOTICE = "You Are Distracted"
}
